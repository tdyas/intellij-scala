package org.jetbrains.plugins.scala
package lang
package psi
package types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDeclaration
import api.statements._
import collection.mutable.{ListBuffer, HashSet, HashMap}
import result.{TypingContext, Failure}
import com.intellij.psi.{PsiElement, PsiTypeParameter}

/**
 * Substitutor should be meaningful only for decls and typeDecls. Components shouldn't be applied by substitutor.
 */
case class ScCompoundType(val components: Seq[ScType], val decls: Seq[ScDeclaredElementsHolder],
                          val typeDecls: Seq[ScTypeAlias], val subst: ScSubstitutor) extends ValueType {
  //compound types are checked by checking the set of signatures in their refinements
  val signatureMap = new HashMap[Signature, ScType] {
    override def elemHashCode(s : Signature) = s.name.hashCode* 31 + s.paramLength
  }

  type Bounds = Pair[ScType, ScType]
  val types = new HashMap[String, Bounds]

  for (typeDecl <- typeDecls) {
    types += ((typeDecl.name, (typeDecl.lowerBound.getOrElse(Nothing), typeDecl.upperBound.getOrElse(Any))))
  }

  val problems : ListBuffer[Failure] = new ListBuffer

  for (decl <- decls) {
    decl match {
      case fun: ScFunction =>
        signatureMap += ((new PhysicalSignature(fun, subst), fun.getType(TypingContext.empty).getOrElse(Any)))
      case varDecl: ScVariable => {
        varDecl.typeElement match {
          case Some(te) => for (e <- varDecl.declaredElements) {
            val varType = te.getType(TypingContext.empty(varDecl.declaredElements))
            varType match {case f@Failure(_, _) => problems += f; case _ =>}
            signatureMap += ((new Signature(e.name, Stream.empty, 0, subst), varType.getOrElse(Any)))
            signatureMap += ((new Signature(e.name + "_", Stream(varType.getOrElse(Any)), 1, subst), Unit)) //setter
          }
          case None =>
        }
      }
      case valDecl: ScValue => valDecl.typeElement match {
        case Some(te) => for (e <- valDecl.declaredElements) {
          val valType = te.getType(TypingContext.empty(valDecl.declaredElements))
          valType match {case f@Failure(_, _) => problems += f; case _ =>}
          signatureMap += ((new Signature(e.name, Stream.empty, 0, subst), valType.getOrElse(Any)))
        }
        case None =>
      }
    }
  }

  def typesMatch(types1 : HashMap[String, Bounds], subst1: ScSubstitutor,
                         types2 : HashMap[String, Bounds], subst2: ScSubstitutor) : Boolean = {
    if (types1.size != types.size) return false
    else {
      for ((name, bounds1) <- types1) {
        types2.get(name) match {
          case None => return false
          case Some (bounds2) => if (!(subst1.subst(bounds1._1) equiv subst2.subst(bounds2._1)) ||
                                     !(subst1.subst(bounds1._2) equiv subst2.subst(bounds2._2))) return false
        }
      }
      true
    }
  }

  override def removeAbstracts = ScCompoundType(components.map(_.removeAbstracts), decls, typeDecls, subst)

  override def updateThisType(place: PsiElement) =
    ScCompoundType(components.map(_.updateThisType(place)), decls, typeDecls, subst)

  override def updateThisType(tp: ScType) =
    ScCompoundType(components.map(_.updateThisType(tp)), decls, typeDecls, subst)
}