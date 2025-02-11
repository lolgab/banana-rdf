/*
 *  Copyright (c) 2012 , 2021 W3C Members
 *
 *  See the NOTICE file(s) distributed with this work for additional
 *  information regarding copyright ownership.
 *
 *  This program and the accompanying materials are made available under
 *  the W3C Software Notice and Document License (2015-05-13) which is available at
 *  https://www.w3.org/Consortium/Legal/2015/copyright-software-and-document.
 *
 *  SPDX-License-Identifier: W3C-20150513
 */

package org.w3.banana

import org.w3.banana.RDF.*
import org.w3.banana.TestConstants.*

open class GraphTest[R <: RDF](using ops: Ops[R]) extends munit.FunSuite:
   // todo: find a way to simplify these imports for end users of the library
   import ops.{*, given}
   import org.w3.banana.prefix.FOAF

   val timbl: URI[R] = URI(tim("i"))
   val bblf: URI[R] = URI(bbl("i"))
   val foaf: FOAF[R] = FOAF[R]

   test("empty graph contains no triples") {
     val g0: Graph[R] = Graph.empty
     assertEquals(g0.size, 0)
   }

   test("Add a triple to a graph and it becomes one bigger") {
     val bkt = Triple(bblf, foaf.knows, timbl)
     val g1 = Graph.empty + bkt
     assertEquals(g1.size, 1)
   }

   test("Create a Graph of 3 triples") {
     val bkt = Triple(bblf, foaf.knows, timbl)
     val triples: List[RDF.Triple[R]] =
       bkt :: (for n <- List(bblf, timbl) yield Triple(n, rdf.typ, foaf.Person))
     val g = Graph(triples)
     assertEquals(g.size, 3)
     val it: Iterator[RDF.Triple[R]] = g.find(`*`, rdf.typ, foaf.Person)
     assertEquals(it.map(t => t.subj.asNode).toSet, Set(bblf, timbl))
   }

   test("Test Graph syntax methods") {
     val g0: RDF.Graph[R] = Graph.empty
     val bkt: RDF.Triple[R] = Triple(bblf, foaf.knows, timbl)
     val g1: RDF.Graph[R] = g0 + bkt
     assertEquals(g1.size, 1)
     val g1x: RDF.Graph[R] = g1.union(g1)
     assertEquals(g1x.size, 1)
     assert(g1 isomorphic g1x)
     val tkb: RDF.Triple[R] = Triple(timbl, foaf.knows, bblf)
     val g2: RDF.Graph[R] = g1 + tkb
     assertEquals(g2.size, 2)
     val g2LessG1 = g2.diff(g1)
     assertEquals(g2LessG1.size, 1)
     assertEquals(g2LessG1.triples.head, tkb)
     val tname = Triple(timbl, foaf.name, "Tim" `@` Lang("en"))
     val bbyear =
       Triple(
         bblf,
         URI(foafPre("byear")),
         "1967" ^^ xsd.integer
       ) // note: byear does not exist in foaf
// this does not work with TypeTests. Try again when we can express Literal[R] <: Node[R]
     val glit: RDF.Graph[R] = Graph(tname, bbyear)
     assertEquals(glit.size, 2)
     val gbig = glit.union(g2)
     assertEquals(gbig.size, 4)
     assert(gbig ≅ g2.union(glit))
   }
end GraphTest

open class GraphSearchTest[Rdf <: RDF](using ops: Ops[Rdf]) extends munit.FunSuite:
   // todo: find a way to simplify these imports for end users of the library
   import ops.given
   import org.w3.banana.prefix.{FOAF, XSD}

   val timbl: URI[Rdf] = URI(tim("i"))
   val bblf: URI[Rdf] = URI(bbl("i"))
   val xsd: XSD[Rdf] = XSD[Rdf]
   val foaf: FOAF[Rdf] = FOAF[Rdf]
   import ops.`*`
   val bkt = Triple(bblf, foaf.knows, timbl)
   val tname = Triple(timbl, foaf.name, "Tim" `@` Lang("en"))
   val bname = Triple(bblf, foaf.name, "Henry" `@` Lang("en"))

   test("Test Graph with  n <= 1 triples") {
     val g0: Graph[Rdf] = Graph.empty
     assertEquals(g0.find(`*`, `*`, `*`).toSeq, Seq())
     assertEquals(g0.find(bblf, `*`, `*`).toSeq, Seq())
     val g1 = g0 + bkt
     assertEquals(g1.find(`*`, `*`, `*`).toSeq, Seq(bkt))
     assertEquals(g1.find(bblf, `*`, `*`).toSeq, Seq(bkt))
     assertEquals(g1.find(bblf, foaf.knows, `*`).toSeq, Seq(bkt))
     assertEquals(g1.find(bblf, foaf.knows, timbl).toSeq, Seq(bkt))
     assertEquals(g1.find(`*`, foaf.knows, timbl).toSeq, Seq(bkt))
     assertEquals(g1.find(`*`, `*`, timbl).toSeq, Seq(bkt))
     assertEquals(g1.find(`*`, foaf.knows, `*`).toSeq, Seq(bkt))
     assertEquals(g1.find(timbl, `*`, `*`).toSeq, Seq())
   }

   test("Test graph with 3 triples") {
     val g3 = Graph(bkt, tname, bname)
     assertEquals(g3.find(`*`, `*`, `*`).toSet, g3.triples.toSet)
     assertEquals(g3.find(bblf, `*`, `*`).toSet, Set(bkt, bname))
     assertEquals(g3.find(bblf, foaf.knows, `*`).toSeq, Seq(bkt))
     assertEquals(g3.find(bblf, foaf.knows, timbl).toSeq, Seq(bkt))
     assertEquals(g3.find(`*`, foaf.knows, timbl).toSeq, Seq(bkt))
     assertEquals(g3.find(`*`, foaf.knows, timbl).toSeq, Seq(bkt))
     assertEquals(g3.find(`*`, `*`, timbl).toSeq, Seq(bkt))
     assertEquals(g3.find(`*`, foaf.knows, `*`).toSeq, Seq(bkt))
     assertEquals(g3.find(timbl, `*`, `*`).toSeq, Seq(tname))
     assertEquals(g3.find(`*`, timbl, `*`).toSeq, Seq())
   }

//		val g1x = g1.union(g1)
//		println("g1x="+g1x)
//		assertEquals(g1x.size,1)
//		assert(g1 isomorphic g1x)
//		val tkb = Triple(timbl,foaf.knows,bblf)
//		val g2 = g1 + tkb
//		assertEquals(g2.size,2)
//		val g2LessG1 = g2.diff(g1)
//		assertEquals(g2LessG1.size,1)
//		assertEquals(g2LessG1.triples.head,tkb)
//		val tname = Triple(timbl, foaf.name, "Tim"`@`Lang("en"))
//		val bbyear = Triple(bblf,URI(foafPre("byear")),"1967"^^xsd.integer) //note: byear does not exist in foaf
//		// this does not work with TypeTests. Try again when we can express Literal[R] <: Node[R]
//		val glit: Graph[Rdf] = Graph(tname, bbyear)
//		assertEquals(glit.size,2)
//		val gbig = glit.union(g2)
//		assertEquals(gbig.size,4)
//		assert(gbig ≅ g2.union(glit))

end GraphSearchTest
