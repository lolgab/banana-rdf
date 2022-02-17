import Dependencies.{TestLibs, Ver, fish, jenaLibs, typelevel}
import JSEnv.{Chrome, Firefox, NodeJS}
import org.scalajs.linker.interface.ModuleKind.ESModule
import org.scalajs.linker.interface.OutputPatterns
import sbt.Keys.description
import sbtcrossproject.CrossPlugin.autoImport.{CrossType, crossProject}
import org.scalajs.jsenv.nodejs.NodeJSEnv
import sbt.ThisBuild
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeOptions}
import org.openqa.selenium.firefox.{FirefoxOptions, FirefoxProfile}
import org.openqa.selenium.remote.server.{DriverFactory, DriverProvider}
import org.scalajs.jsenv.selenium.SeleniumJSEnv

name                               := "banana-rdf"
ThisBuild / tlBaseVersion          := "0.9"
ThisBuild / tlUntaggedAreSnapshots := true

ThisBuild / organization     := "net.bblfish.rdf"
ThisBuild / organizationName := "Henry Story"
ThisBuild / startYear        := Some(2012)
ThisBuild / developers := List(
  tlGitHubDev("bblfish", "Henry Story"),
  tlGitHubDev("betehess", "Alexandre Bertails")
)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / resolvers += Dependencies.sonatypeSNAPSHOT

enablePlugins(TypelevelCiReleasePlugin)
enablePlugins(TypelevelSonatypePlugin)

ThisBuild / tlCiReleaseBranches := Seq() // "scala3" if github were to do the releases
ThisBuild / tlCiReleaseTags     := false // don't publish artifacts on github

ThisBuild / crossScalaVersions := Seq("3.1.1") //, "2.13.8")
//scalaVersion := Ver.scala3

//ThisBuild / shellPrompt := ((s: State) => Project.extract(s).currentRef.project + "> ")

ThisBuild / homepage := Some(url("https://github.com/bblfish/banana-rdf"))
ThisBuild / scmInfo := Some(
  ScmInfo(url("https://github.com/bblfish/banana-rdf"), "git@github.com:bblfish/banana-rdf.git")
)
def w3cLicence(yearStart: Int, yearEnd: Option[Int] = None) = Some(HeaderLicense.Custom(
  s""" Copyright (c) $yearStart ${yearEnd.map(", " + _).getOrElse("")} W3C Members
     |
     | See the NOTICE file(s) distributed with this work for additional
     | information regarding copyright ownership.
     |
     | This program and the accompanying materials are made available under
     | the W3C Software Notice and Document License (2015-05-13) which is available at
     | https://www.w3.org/Consortium/Legal/2015/copyright-software-and-document.
     |
     | SPDX-License-Identifier: W3C-20150513""".stripMargin
))

ThisBuild / headerLicense := w3cLicence(2021)

tlReplaceCommandAlias("ciJS", List(CI.NodeJS, CI.Chrome, CI.Firefox).mkString)
addCommandAlias("ciNode", CI.NodeJS.toString)
addCommandAlias("ciFirefox", CI.Firefox.toString)
addCommandAlias("ciChrome", CI.Chrome.toString)

lazy val useJSEnv =
  settingKey[JSEnv]("Use Node.js or a headless browser for running Scala.js tests")
Global / useJSEnv := JSEnv.NodeJS

ThisBuild / Test / jsEnv := {
  val old = (Test / jsEnv).value

  useJSEnv.value match {
    case NodeJS => old
    case Firefox =>
      val options = new FirefoxOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options)
    case Chrome =>
      val options = new ChromeOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options)
  }
}

lazy val root = tlCrossRootProject.aggregate(
  rdf,
  ntriples,
  jena,
  rdf4j,
  rdflibJS
)

addCommandAlias("prePR", "; root/clean; scalafmtSbt; +root/scalafmtAll; +root/headerCreate")

lazy val commonSettings = Seq(
  organization  := "net.bblfish.rdf",
  description   := "RDF framework for Scala",
  headerLicense := w3cLicence(2012, Some(2021)),
  startYear     := Some(2012),
  scalaVersion  := Ver.scala3,
  homepage      := Some(url("https://github.com/banana-rdf/banana-rdf")),
  updateOptions := updateOptions.value.withCachedResolution(
    true
  ) // to speed up dependency resolution
)

lazy val rdf = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("rdf"))
  .settings(commonSettings*)
  .settings(
    name := "banana-rdf",
    libraryDependencies ++= Seq(typelevel.catsCore.value)
  )
  .jvmSettings(
    scalacOptions := scala3jvmOptions
  )
  .jsSettings(
    scalacOptions ++= scala3jsOptions
  )

lazy val rdfJVM = rdf.jvm
lazy val rdfJS  = rdf.js

lazy val ntriples = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .settings(commonSettings*)
  .settings(
    headerLicense := w3cLicence(2016),
    description   := "Blocking NTriples Parser"
  )
  .in(file("ntriples"))
  .dependsOn(rdf)
  .jvmSettings(
    scalacOptions := scala3jvmOptions
    //	scalacOptions += "-rewrite"
  )
  .jsSettings(
    scalacOptions ++= scala3jsOptions
    //	scalacOptions += "-rewrite"
  )

lazy val ntriplesJVM = ntriples.jvm
lazy val ntriplesJS  = ntriples.js

lazy val jena = project.in(file("jena"))
  .settings(commonSettings*)
  .settings(
    name                               := "banana-jena",
    description                        := "Jena implementation of banana-rdf",
    scalacOptions                      := scala3jvmOptions,
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.ScalaLibrary,
    libraryDependencies ++= Seq(jenaLibs) // , slf4jNop, aalto )
  )
  .dependsOn(
    rdfJVM,
    rdfTestSuiteJVM % "test->compile",
    ntriplesJVM
  ) // , ntriplesJVM, rdfTestSuiteJVM % "test->compile")

import Dependencies.RDF4J
lazy val rdf4j = project.in(file("rdf4j"))
  .settings(commonSettings*)
  .settings(
    name          := "banana-rdf4j",
    description   := "RDF4J implementation of banana-rdf",
    scalacOptions := scala3jvmOptions,
    libraryDependencies ++= Seq(
      RDF4J.QueryAlgebra,
      RDF4J.QueryParser,
      RDF4J.QueryResult,
      RDF4J.RioTurtle,
      RDF4J.RioRdfxml,
      RDF4J.RioJsonLd,
      RDF4J.SailMemory,
      RDF4J.SailNativeRdf,
      RDF4J.RepositorySail,
      Dependencies.slf4jNop,
      Dependencies.jsonldJava
    )
  ).dependsOn(rdfJVM, rdfTestSuiteJVM % "test->compile") // ntriplesJVM,

lazy val rdflibJS = project.in(file("rdflibJS"))
//	.enablePlugins(ScalaJSPlugin)
  .enablePlugins(ScalaJSBundlerPlugin)
  //	.enablePlugins(WebScalaJSBundlerPlugin)
  // documentation here: https://scalablytyped.org/docs/library-developer
  // call stImport in sbt to generate new sources
  // .enablePlugins(ScalablyTypedConverterGenSourcePlugin)
  //	.enablePlugins(ScalablyTypedConverterPlugin)
  .settings(commonSettings*)
  .settings(
    name        := "rdflibJS",
    description := "rdflib.js implementation of banana-rdf",
    useYarn     := true,
    scalacOptions ++= scala3jsOptions,
    Compile / npmDependencies += "rdflib" -> "2.2.8",
    Test / npmDependencies += "rdflib"    -> "2.2.8",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      fish.rdf_model_js.value,
      TestLibs.scalatest.value % Test,
//			TestLibs.utest.value % Test,
      TestLibs.munit.value % Test
    )
//		testFrameworks += new TestFramework("utest.runner.Framework"),
//		scalaJSUseMainModuleInitializer := true,
//		Compile / mainClass := Some( "org.w3.banana.rdflib.test.Test" ),
//		Compile / scalaJSLinkerConfig ~= {
//			//	nodejs needs .mjs extension. See https://www.scala-js.org/doc/project/module.html
//			_.withModuleKind(ModuleKind.ESModule)
//			// replacing CommonJSModule with what is below creates a linking problem
//			.withOutputPatterns(OutputPatterns.fromJSFile("%s.mjs"))
//		}
  ).dependsOn(rdfJS, rdfTestSuiteJS % "test->compile")

//lazy val rdflibScratch =  project.in(file("rdflib.scratch"))
//	// .enablePlugins(ScalaJSBundlerPlugin)
//	//documentation here: https://scalablytyped.org/docs/library-developer
//	// call stImport in sbt to generate new sources
//	//.enablePlugins(ScalablyTypedConverterGenSourcePlugin)
//	.enablePlugins(ScalablyTypedConverterPlugin)
//	.settings(commonSettings: _*)
//	.settings(
//		name := "rdflib-scratch",
//		useYarn := true,
//		scalacOptions ++= scala3jsOptions,
//		Compile / npmDependencies += "rdflib" -> "2.2.7",
//		stUseScalaJsDom := true,
//		libraryDependencies += "org.w3" %%% "rdflib-types" % "0.1-SNAPSHOT",
//		scalaJSUseMainModuleInitializer := true,
//		Compile / mainClass := Some( "org.w3.banana.testRdfLib" ),
//	)

lazy val rdfTestSuite = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("rdf-test-suite"))
  .settings(commonSettings*)
  .settings(
    name        := "banana-test",
    description := "Generic tests to be run on each banana-rdf implementation",
    libraryDependencies ++= Seq(
      TestLibs.scalatest.value,
      TestLibs.munit.value,
      TestLibs.utest.value
    )
    //	Test / resourceDirectory  := baseDirectory.value / "src/main/resources"
  )
  .dependsOn(rdf, ntriples)
  .jvmSettings(
    scalacOptions := scala3jvmOptions
  )
  .jsSettings(
    scalacOptions ++= scala3jsOptions,
    Test / scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.CommonJSModule)
    } // required for munit to run
  )

lazy val rdfTestSuiteJVM = rdfTestSuite.jvm
lazy val rdfTestSuiteJS  = rdfTestSuite.js

lazy val scala3jvmOptions = Seq(
  // "-classpath", "foo:bar:...",         // Add to the classpath.
  // "-encoding", "utf-8",                // Specify character encoding used by source files.
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-unchecked",   // Enable additional warnings where generated code depends on assumptions.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  // "-explain",                          // Explain errors in more detail.
  // "-explain-types",                    // Explain type errors in more detail.
  "-indent", // Together with -rewrite, remove {...} syntax when possible due to significant indentation.
  // "-no-indent",                        // Require classical {...} syntax, indentation is not significant.
  //	"-rewrite",                          // Attempt to fix code automatically. Use with -indent and ...-migration.
  //	"-source", "future-migration",
  "-new-syntax", // Require `then` and `do` in control expressions.
  // "-old-syntax",                       // Require `(...)` around conditions.
  // "-language:Scala2",                  // Compile Scala 2 code, highlight what needs updating
  // "-language:strictEquality",          // Require +derives Eql+ for using == or != comparisons
  // "-scalajs",                          // Compile in Scala.js mode (requires scalajs-library.jar on the classpath).
  "-source:future", // Choices: future and future-migration. I use this to force future deprecation warnings, etc.
  // "-Xfatal-warnings",                  // Fail on warnings, not just errors
  // "-Xmigration",                       // Warn about constructs whose behavior may have changed since version.
  // "-Ysafe-init",                       // Warn on field access before initialization
  "-Yexplicit-nulls" // For explicit nulls behavior.
)

lazy val scala3jsOptions = Seq(
  "-indent", // Together with -rewrite, remove {...} syntax when possible due to significant indentation.
  "-new-syntax", // Require `then` and `do` in control expressions.
  "-source:future", // Choices: future and future-migration. I use this to force future deprecation warnings, etc.
  "-Yexplicit-nulls" // For explicit nulls behavior.
)

//lazy val scratch = crossProject(JVMPlatform,JSPlatform)
//	.crossType(CrossType.Full)
//	.in(file("scratch"))
//	.settings(commonSettings: _*)
//	.settings(
//		libraryDependencies += TestLibs.munit
//	)
//	.jvmSettings(
//		name := "scratch",
//		scalacOptions ++= scala3jvmOptions,
//		//Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.ScalaLibrary,
////		libraryDependencies ++= Seq(jenaLibs, TestLibs.munit)
//	)
//lazy val scratchJVM = scratch.jvm
//lazy val scratchJS = scratch.js
