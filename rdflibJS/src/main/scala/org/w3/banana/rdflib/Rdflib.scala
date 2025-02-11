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

package org.w3.banana.rdflib

import org.w3.banana.operations.StoreFactory
import org.w3.banana.rdflib.facade.*
import org.w3.banana.rdflib.facade.FormulaOpts.FormulaOpts
import org.w3.banana.rdflib.facade.storeMod.IndexedFormula
import org.w3.banana.{Ops, RDF, operations}
import run.cosy.rdfjs.model
import run.cosy.rdfjs.model.{DataFactory, Term}

import scala.annotation.targetName
import scala.reflect.TypeTest
import scala.scalajs.js
import scala.scalajs.js.undefined

object Rdflib extends RDF:
   type Top = AnyRef

   override opaque type rGraph <: Top = storeMod.IndexedFormula
   override opaque type rTriple <: Top = model.Quad
   override opaque type rNode <: Top = model.ValueTerm[?]
   override opaque type rURI <: rNode = model.NamedNode

   override opaque type Store = storeMod.IndexedFormula
   override opaque type Graph <: rGraph = storeMod.IndexedFormula
   override opaque type Triple <: rTriple = model.Quad
   override opaque type Quad <: Top = model.Quad
   override opaque type Node <: rNode = model.ValueTerm[?]
   override opaque type URI <: Node & rURI = model.NamedNode
   override opaque type BNode <: Node = model.BlankNode
   override opaque type Literal <: Node = model.Literal
   override opaque type Lang <: Top = String
   override opaque type DefaultGraphNode <: Node = model.DefaultGraph

   override type NodeAny = Null

   override type MGraph = storeMod.IndexedFormula // a mutable graph

   //	given uriTT: TypeTest[Node,URI] with {
//		override def unapply(s: Node): Option[s.type & URI] =
//			s match
//				//note: using rjIRI won't compile
//				case x: (s.type & org.eclipse.rdf4j.model.IRI) => Some(x)
//				case _ => None
//	}

//	given literalTT: TypeTest[Node,Literal] with {
//		override def unapply(s: Node): Option[s.type & Literal] =
//			s match
//				//note: this does not compile if we use URI instead of jena.Node_URI
//				case x: (s.type & org.eclipse.rdf4j.model.Literal) => Some(x)
//				case _ => None
//	}

   /** Here we build up the methods functions allowing RDF.Graph[R] notation to be used.
     *
     * This will be the same code in every singleton implementation of RDF.
     */
   given ops: Ops[R] with
      import js.JSConverters.*
      val df: DataFactory = model.DataFactory()
      def opts(): FormulaOpts = facade.FormulaOpts().setRdfFactory(df)
      import RDF.Statement as St

//      given [T]:  Conversion[T, scala.scalajs.js.UndefOr[T]] with
//         def apply(x: T): scala.scalajs.js.UndefOr[T] =
//           x.asInstanceOf[scala.scalajs.js.UndefOr[T]]
//
      import scala.collection.mutable

      val defaulGraph: RDF.DefaultGraphNode[R] = df.defaultGraph()

      val `*` : RDF.NodeAny[R] = null

      given basicStoreFactory: StoreFactory[R] with
         override def makeStore(): RDF.Store[R] =
            val fopts = org.w3.banana.rdflib.facade.FormulaOpts()
            fopts.setRdfFactory(model.DataFactory())
            org.w3.banana.rdflib.facade.storeMod(fopts)

      given Store: operations.Store[R](using ops) with
         // todo: need to integrate locking functionality
         extension (store: RDF.Store[R])
            override def add(qs: RDF.Quad[R]*): store.type =
               for q <- qs do store.addStatement(q)
               store

            override def remove(qs: RDF.Quad[R]*): store.type =
               store.remove(qs.toJSArray)
               store

            override def find(
                s: St.Subject[R] | RDF.NodeAny[R],
                p: St.Relation[R] | RDF.NodeAny[R],
                o: St.Object[R] | RDF.NodeAny[R],
                g: St.Graph[R] | RDF.NodeAny[R]
            ): Iterator[RDF.Quad[R]] =
               // todo: note, we loose the try exception failure here
               val oUndef =
                 o.asInstanceOf[scala.scalajs.js.UndefOr[run.cosy.rdfjs.model.Quad.Object | Null]]

               val res: scala.collection.mutable.Seq[RDF.Quad[R]] =
                 store.statementsMatching(s, p, oUndef, g, false)
               res.iterator

            override def remove(
                s: St.Subject[R] | RDF.NodeAny[R],
                p: St.Relation[R] | RDF.NodeAny[R],
                o: St.Object[R] | RDF.NodeAny[R],
                g: St.Graph[R] | RDF.NodeAny[R]
            ): store.type = store.remove(store.statementsMatching(s, p, o, g, false)).nn

            override def default: St.Graph[R] = defaulGraph

      end Store

      given Graph: operations.Graph[R] with
         def empty: RDF.Graph[R] = storeMod(opts())
         def apply(triples: Iterable[RDF.Triple[R]]): RDF.Graph[R] =
            val graph: storeMod.IndexedFormula = empty
            triples.foreach(t => graph.addStatement(t))
            graph
         def triplesIn(graph: RDF.Graph[R]): Iterable[RDF.Triple[R]] =
            val iFrm: org.w3.banana.rdflib.facade.storeMod.IndexedFormula = graph
            iFrm.`match`(undefined, undefined, undefined, undefined)

         // note the graph size may be bigger as we are using a quad store
         def graphSize(graph: RDF.Graph[R]): Int =
           graph.length.toInt

         // If one modelled Graphs as Named Graphs, then union could just be unioning the names
         // this type of union is very inefficient
         def gunion(graphs: Seq[RDF.Graph[R]]): RDF.Graph[R] =
           graphs match
            case Seq(x) => x
            case _ =>
              val newGraph: IndexedFormula = empty
              graphs.foreach(g => g.statements.foreach(s => newGraph.addStatement(s)))
              newGraph

         def difference(g1: RDF.Graph[R], g2: RDF.Graph[R]): RDF.Graph[R] =
            val newgraph: IndexedFormula = empty
            triplesIn(g1) foreach { triple =>
              if !g2.holdsStatement(triple) then newgraph.add(triple)
            }
            newgraph

         import org.w3.banana.isomorphism.*
         // todo: set preferences to be higher
         // todo: perhaps have isomorphism be an external object?
         private val mapGen = new SimpleMappingGenerator[R](VerticeCBuilder.simpleHash[R])
         private val iso = new GraphIsomorphism[R](mapGen)
         def isomorphism(left: RDF.Graph[R], right: RDF.Graph[R]): Boolean =
            val a = iso.findAnswer(left, right)
            a.isSuccess

         def findTriples(
             graph: RDF.Graph[R],
             s: St.Subject[R] | RDF.NodeAny[R],
             p: St.Relation[R] | RDF.NodeAny[R],
             o: St.Object[R] | RDF.NodeAny[R]
         ): Iterator[RDF.Triple[R]] =
            val sm: mutable.Seq[RDF.Triple[R]] =
              graph.statementsMatching(s, p, o, df.defaultGraph(), false)
            sm.iterator
      end Graph

      given rGraph: operations.rGraph[R] with
         def empty: RDF.rGraph[R] = storeMod(opts())
         def apply(triples: Iterable[RDF.rTriple[R]]): RDF.rGraph[R] =
            val graph: storeMod.IndexedFormula = empty
            graph.addAll(triples.toJSArray)
            graph

         import org.w3.banana.isomorphism.*
         private val mapGen = new SimpleMappingGenerator[R](VerticeCBuilder.simpleHash[R])
         private val iso = new GraphIsomorphism[R](mapGen)

         extension (rGraph: RDF.rGraph[R])
            override def triples: Iterable[RDF.rTriple[R]] =
               val iFrm: IndexedFormula = rGraph
               iFrm.`match`(undefined, undefined, undefined, undefined)

            override def size: Int = rGraph.length.toInt

            infix def ++(triples: Seq[RDF.rTriple[R]]): RDF.rGraph[R] =
              if triples.isEmpty then rGraph
              else
                 val newGraph: IndexedFormula = empty
                 rGraph.triples.foreach(t => newGraph.addStatement(t))
                 triples.foreach { s =>
                    println("now adding " + s)
                    newGraph.addStatement(s)
                 }
                 newGraph

            infix def isomorphic(other: RDF.rGraph[R]): Boolean =
               val a = iso.findAnswer(rGraph, other)
               a.isSuccess
      end rGraph

      given Subject: operations.Subject[R] with
         extension (subj: RDF.Statement.Subject[R])
           def foldSubj[A](uriFnct: RDF.URI[R] => A, bnFcnt: RDF.BNode[R] => A): A =
             subj match
              case nn: model.NamedNode    => uriFnct(nn)
              case blank: model.BlankNode => bnFcnt(blank)
      end Subject

      given Triple: operations.Triple[R] with
         import RDF.Statement as St
         // todo: check whether it really is not legal in rdflib to have a literal as subject
         // warning throws an exception
         def apply(s: St.Subject[R], p: St.Relation[R], o: St.Object[R]): RDF.Triple[R] =
           df.quad(s, p, o, df.defaultGraph())
         def subjectOf(t: RDF.Triple[R]): St.Subject[R] = t.subj
         def relationOf(t: RDF.Triple[R]): St.Relation[R] = t.rel
         def objectOf(t: RDF.Triple[R]): St.Object[R] = t.obj
      end Triple

      override val Quad = new operations.Quad[R](this):
         def apply(s: St.Subject[R], p: St.Relation[R], o: St.Object[R]): RDF.Quad[R] =
           df.quad(s, p, o, df.defaultGraph())
         def apply(
             s: St.Subject[R],
             p: St.Relation[R],
             o: St.Object[R],
             where: St.Graph[R]
         ): RDF.Quad[R] = df.quad(s, p, o, where)
         protected def subjectOf(s: RDF.Quad[R]): St.Subject[R] = s.subj
         protected def relationOf(s: RDF.Quad[R]): St.Relation[R] = s.rel
         protected def objectOf(s: RDF.Quad[R]): St.Object[R] = s.obj
         protected def graphOf(s: RDF.Quad[R]): St.Graph[R] = s.graph
      end Quad

      // todo: see whether this really works! It may be that we need to create a new construct
      given rTriple: operations.rTriple[R] with
         import RDF.rStatement as rSt
         def apply(s: rSt.Subject[R], p: rSt.Relation[R], o: rSt.Object[R]): RDF.rTriple[R] =
           df.quad(s, p, o, df.defaultGraph())
         def untuple(t: RDF.rTriple[R]): rTripleI =
           (subjectOf(t).widenToNode, relationOf(t), objectOf(t).asNode)
         protected def subjectOf(t: RDF.rTriple[R]): rSt.Subject[R] = t.subj
         protected def relationOf(t: RDF.rTriple[R]): rSt.Relation[R] = t.rel
         protected def objectOf(t: RDF.rTriple[R]): rSt.Object[R] = t.obj
      end rTriple

      given Node: operations.Node[R] with
         private def rl(node: RDF.Node[R]): Term[?] = node.asInstanceOf[Term[?]]
         extension (node: RDF.Node[R])
            def isURI: Boolean = rl(node).isInstanceOf[model.NamedNode]
            def isBNode: Boolean = rl(node).isInstanceOf[model.BlankNode]
            def isLiteral: Boolean = rl(node).isInstanceOf[model.Literal]
            // we override fold, as we can implement it faster with pattern matching
            override def fold[A](
                uriF: RDF.URI[R] => A,
                bnF: RDF.BNode[R] => A,
                litF: RDF.Literal[R] => A
            ): A = node match
             case nn: model.NamedNode    => uriF(nn)
             case blank: model.BlankNode => bnF(blank)
             case lit: model.Literal     => litF(lit)
      end Node

      given rNode: operations.rNode[R] with
         private def rl(node: RDF.rNode[R]): Term[?] = node.asInstanceOf[Term[?]]

         extension (rnode: RDF.rNode[R])
            def isURI: Boolean = rl(rnode).isInstanceOf[model.NamedNode]
            def isBNode: Boolean = rl(rnode).isInstanceOf[model.BlankNode]
            def isLiteral: Boolean = rl(rnode).isInstanceOf[model.Literal]

      end rNode

      given BNode: operations.BNode[R] with
         def apply(s: String): RDF.BNode[R] = df.blankNode(s)
         def apply(): RDF.BNode[R] = df.blankNode(undefined)
         extension (bn: RDF.BNode[R])
           def label: String = bn.value
      end BNode

      override given bnodeTT: TypeTest[Matchable, RDF.BNode[R]] with
         def unapply(s: Matchable): Option[s.type & RDF.BNode[R]] =
           s match
            // note: this does not compile if we use URI instead of jena.Node_URI
            case x: (s.type & run.cosy.rdfjs.model.BlankNode) => Some(x)
            case _                                            => None

      val Literal = new operations.Literal[R]:
         import org.w3.banana.operations.URI.*
         private val xsdString = df.namedNode(xsdStr).nn
         private val xsdLangString = df.namedNode(xsdLangStr).nn

         def apply(plain: String): RDF.Literal[R] = df.literal(plain, undefined)
         def apply(lit: LiteralI): RDF.Literal[R] = lit match
          case LiteralI.Plain(text)     => apply(text)
          case LiteralI.`@`(text, lang) => df.literal(text, lang)
          case LiteralI.`^^`(text, tp)  => df.literal(text, tp)

         def unapply(x: Matchable): Option[LiteralI] = x match
          case lit: model.Literal =>
            val lex: String = lit.value
            val dt: RDF.URI[R] = lit.datatype
            val lang: String = lit.language
            if lang.isEmpty then
               // todo: this comparison could be costly, check
               if dt == xsdString then Some(LiteralI.Plain(lex))
               else Some(LiteralI.^^(lex, dt))
            else if dt == xsdLangString then
               Some(LiteralI.`@`(lex, Lang(lang)))
            else None
          case _ => None

         @targetName("langLit")
         def apply(lex: String, lang: RDF.Lang[R]): RDF.Literal[R] = df.literal(lex, lang.label)

         @targetName("dataTypeLit")
         def apply(lex: String, dataTp: RDF.URI[R]): RDF.Literal[R] = df.literal(lex, dataTp)

         extension (lit: RDF.Literal[R])
           def text: String = lit.value
      end Literal

      override given literalTT: TypeTest[Matchable, RDF.Literal[R]] with
         override def unapply(s: Matchable): Option[s.type & RDF.Literal[R]] =
           s match
            case x: (s.type & model.Literal) => Some(x)
            case _                           => None

      given Lang: operations.Lang[R] with
         def apply(lang: String): RDF.Lang[R] = lang
         extension (lang: RDF.Lang[R])
           def label: String = lang

      given rURI: operations.rURI[R] with
         override protected def mkUriUnsafe(uriStr: String): RDF.rURI[R] =
           df.namedNode(uriStr)
         override def apply(iriStr: String): RDF.rURI[R] = mkUriUnsafe(iriStr)
         override protected def stringVal(uri: RDF.rURI[R]): String =
           uri.asInstanceOf[model.NamedNode].value
      end rURI

      given rUriTT: reflect.TypeTest[Matchable, org.w3.banana.RDF.rURI[R]] with
         override def unapply(s: Matchable): Option[s.type & RDF.rURI[R]] =
           s match
            case x: (s.type & model.NamedNode) => Some(x)
            case _                             => None

      given URI: operations.URI[R] with
         // this does throw an exception on non relative URLs!
         override protected def mkUriUnsafe(iriStr: String): RDF.URI[R] = df.namedNode(iriStr)
         override protected def stringVal(uri: org.w3.banana.RDF.URI[R]): String =
           uri.asInstanceOf[model.NamedNode].value
      end URI

      given subjToURITT: TypeTest[RDF.Statement.Subject[R], RDF.URI[R]] with
         override def unapply(s: RDF.Statement.Subject[R]): Option[s.type & model.NamedNode] =
           if s.asInstanceOf[model.ValueTerm[?]].termType eq Term.NamedNode
           then Some(s.asInstanceOf[s.type & model.NamedNode])
           else None

      given rSubjToURITT: TypeTest[RDF.rStatement.Subject[R], RDF.rURI[R]] with
         override def unapply(s: RDF.rStatement.Subject[R]): Option[s.type & model.NamedNode] =
           if s.asInstanceOf[model.ValueTerm[?]].termType eq Term.NamedNode
           then Some(s.asInstanceOf[s.type & model.NamedNode])
           else None

      given objToURITT: TypeTest[RDF.Statement.Object[R], RDF.URI[R]] with
         override def unapply(s: RDF.Statement.Object[R]): Option[s.type & model.NamedNode] =
           if s.asInstanceOf[model.ValueTerm[?]].termType eq Term.NamedNode
           then Some(s.asInstanceOf[s.type & model.NamedNode])
           else None

      given rObjToURITT: TypeTest[RDF.rStatement.Object[R], RDF.rURI[R]] with
         override def unapply(o: RDF.rStatement.Object[R]): Option[o.type & model.NamedNode] =
           if o.asInstanceOf[model.ValueTerm[?]].termType eq Term.NamedNode
           then Some(o.asInstanceOf[o.type & model.NamedNode])
           else None

end Rdflib

// mutable graphs
//	type MGraph = Model
//
//	// types for the graph traversal API
//	type NodeMatch = Value
//	type NodeAny = Null
//	type NodeConcrete = Value
//
//	// types related to Sparql
//	type Query = ParsedQuery
//	type SelectQuery = ParsedTupleQuery
//	type ConstructQuery = ParsedGraphQuery
//	type AskQuery = ParsedBooleanQuery
//
//	//FIXME Can't use ParsedUpdate because of https://openrdf.atlassian.net/browse/SES-1847
//	type UpdateQuery = Rdf4jParseUpdate
//
//	type Solution = BindingSet
//	// instead of TupleQueryResult so that it's eager instead of lazy
//	type Solutions = Vector[BindingSet]
