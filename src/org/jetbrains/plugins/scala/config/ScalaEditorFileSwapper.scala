package org.jetbrains.plugins.scala.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

//todo: possibly join with JavaEditorFileSwapper (code is almost the same)
object ScalaEditorFileSwapper {
  def findSourceFile(project: Project, eachFile: VirtualFile): VirtualFile = {
    val psiFile: PsiFile = PsiManager.getInstance(project).findFile(eachFile)
    psiFile match {
      case file: ScalaFile if file.isCompiled =>
      case _ => return null
    }
    val fqn: String = getFQN(psiFile)
    if (fqn == null) return null
    val clazz: PsiClass = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
    if (!clazz.isInstanceOf[ScTypeDefinition]) return null
    val sourceClass: PsiClass = (clazz.asInstanceOf[ScTypeDefinition]).getSourceMirrorClass
    if (sourceClass == null || (sourceClass eq clazz)) return null
    val result: VirtualFile = sourceClass.getContainingFile.getVirtualFile
    assert(result != null)
    result
  }

  def getFQN(psiFile: PsiFile): String = {
    if (!(psiFile.isInstanceOf[ScalaFile])) return null
    val classes: Array[PsiClass] = (psiFile.asInstanceOf[ScalaFile]).getClasses
    if (classes.length == 0) return null
    val fqn: String = classes(0).getQualifiedName
    if (fqn == null) return null
    fqn
  }
}