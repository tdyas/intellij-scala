/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import collection.mutable.{HashMap, ArrayBuffer, HashSet, Set, ListBuffer}
import com.intellij.psi.PsiClass
import api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types._

abstract class MixinNodes {
  type T
  type K >: T
  def equiv(t1 : K, t2 : K) : Boolean
  def computeHashCode(t : K) : Int
  def isAbstract(t : T) : Boolean
  class Node (val info : T, val substitutor : ScSubstitutor) {
    var supers : Seq[Node] = Seq.empty
    var primarySuper : Option[Node] = None
  }
  
  class Map extends HashMap[K, Node] {
    override def elemHashCode(k : K) = computeHashCode(k)
    override def elemEquals(k1 : K, k2 : K) = equiv(k1, k2)
  }

  class MultiMap extends HashMap[K, Set[Node]] with collection.mutable.MultiMap[K, Node] {
    override def elemHashCode(k : K) = computeHashCode(k)
    override def elemEquals(k1 : K, k2 : K) = equiv(k1, k2)
  }

  object MultiMap {def empty = new MultiMap}

  def mergeSupers (maps : List[Map]) : MultiMap = {
    maps.foldLeft(MultiMap.empty){
      (res, curr) => {
        for ((k, node) <- curr) {
          res.add(k, node)
        }
        res
      }
    }
  }

  def mergeWithSupers(thisMap : Map, supersMerged : MultiMap) {
    for ((key, nodes) <- supersMerged) {
      val primarySuper = nodes.find {n => !isAbstract(n.info)} match {
        case None => nodes.toArray(0)
        case Some(concrete) => concrete
      }
      thisMap.get(key) match {
        case Some(node) => {
          node.primarySuper = Some(primarySuper)
          node.supers = nodes.toArray
        }
        case None => {
          nodes -= primarySuper
          primarySuper.supers = nodes.toArray
          thisMap += ((key, primarySuper))
        }
      }
    }
  }

  def build(td : PsiClass) = {
    def inner(clazz : PsiClass, subst : ScSubstitutor, visited : Set[PsiClass]) : Map = {
      val map = new Map
      if (visited.contains(clazz)) return map
      visited += clazz

      val superTypes = clazz match {
        case td : ScTypeDefinition => {
          processScala(td, subst, map)
          td.superTypes
        }
        case _ => {
          processJava(clazz, subst, map)
          clazz.getSuperTypes.map {psiType => ScType.create(psiType, clazz.getProject)}
        }
      }

      val superTypesBuff = new ListBuffer[Map]
      for (superType <- superTypes) {
        superType match {
          case parameterized: ScParameterizedType => {
            parameterized.designated match {
              case superClass: PsiClass => superTypesBuff += inner(superClass, combine(parameterized.substitutor, subst, superClass), visited)
            }
          }
          case ScDesignatorType(superClass : PsiClass) => superTypesBuff += inner(superClass, ScSubstitutor.empty, visited)
          case _ =>
        }
      }
      mergeWithSupers(map, mergeSupers(superTypesBuff.toList))

      map
    }
    inner(td, ScSubstitutor.empty, new HashSet[PsiClass])
  }

  def combine(superSubst : ScSubstitutor, derived : ScSubstitutor, superClass : PsiClass) = {
    var res : ScSubstitutor = ScSubstitutor.empty
    for ((tp, t) <- superSubst.map) {
      res = res + (tp, derived.subst(t))
    }
    superClass match {
      case td : ScTypeDefinition => for (alias <- td.aliases) {
        res = res + (alias.name, derived.subst(alias))
      }
      case _ => ()
    }
    res
  }

  def processJava(clazz : PsiClass, subst : ScSubstitutor, map : Map)
  def processScala(td : ScTypeDefinition, subst : ScSubstitutor, map : Map)
}
