package com.phodal.shirelang.compiler.variable

import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiManager
import com.phodal.shirecore.provider.DefaultPsiContextVariableProvider
import com.phodal.shirecore.provider.PsiContextVariableProvider
import com.phodal.shirecore.provider.PsiVariable

class BuiltinVariableResolver(
    private val context: VariableResolverContext
) : VariableResolver {
    private val variableProvider: PsiContextVariableProvider

    init {
        val psiFile = PsiManager.getInstance(context.myProject).findFile(context.editor.virtualFile)
        variableProvider = if (psiFile?.language != null) {
            PsiContextVariableProvider.provide(psiFile.language)
        } else {
            DefaultPsiContextVariableProvider()
        }
    }

    override fun resolve(): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        context.symbolTable.getAllVariables().forEach {
            val psiVariable = PsiVariable.fromVariableName(it.key)
            if (psiVariable != null) {
                result[it.key] = try {
                    variableProvider.resolveVariableValue(context.element, psiVariable)
                } catch (e: Exception) {
                    logger<CompositeVariableResolver>().error("Failed to resolve variable: ${it.key}", e)
                    ""
                }
            } else {
                result[it.key] = ""
            }
        }

        return result
    }
}