package com.phodal.shirecore.provider.variable

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

interface VariableProvider<T> {
    fun resolve(variable: T, project: Project, editor: Editor, psiElement: PsiElement?,): Any
}
