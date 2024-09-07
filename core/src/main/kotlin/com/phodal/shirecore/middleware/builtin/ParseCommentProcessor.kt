package com.phodal.shirecore.middleware.builtin

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.phodal.shirecore.middleware.BuiltinPostHandler
import com.phodal.shirecore.middleware.ShireRunVariableContext
import com.phodal.shirecore.middleware.PostProcessor
import com.phodal.shirecore.provider.psi.PsiElementDataBuilder
import org.jetbrains.annotations.NonNls

class ParseCommentProcessor : PostProcessor {
    override val processorName: String = BuiltinPostHandler.ParseComment.handleName

    override fun isApplicable(context: ShireRunVariableContext): Boolean = true

    fun preHandleDoc(newDoc: String): @NonNls String {
        val newDocWithoutCodeBlock = newDoc.removePrefix("```java")
            .removePrefix("```")
            .removeSuffix("```")

        val fromSuggestion = buildDocFromSuggestion(newDocWithoutCodeBlock, "/**", "*/")
        return fromSuggestion
    }

    fun buildDocFromSuggestion(suggestDoc: String, commentStart: String, commentEnd: String): String {
        val startIndex = suggestDoc.indexOf(commentStart)
        if (startIndex < 0) {
            return ""
        }

        val docComment = suggestDoc.substring(startIndex)
        val endIndex = docComment.indexOf(commentEnd, commentStart.length)
        if (endIndex < 0) {
            return docComment + commentEnd
        }

        val substring = docComment.substring(0, endIndex + commentEnd.length)
        return substring
    }

    private fun getDocFromOutput(context: ShireRunVariableContext) =
        preHandleDoc(context.pipeData["output"] as String? ?: context.genText ?: "")

    override fun execute(project: Project, context: ShireRunVariableContext, console: ConsoleView?, args: List<Any>): String {
        val defaultComment: String = getDocFromOutput(context)
        val currentFile = context.currentFile ?: return defaultComment

        val comment = PsiElementDataBuilder.provide(currentFile.language)
            ?.parseComment(project, defaultComment) ?: return defaultComment

        return comment
    }
}
