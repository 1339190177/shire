package com.phodal.shirelang.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.phodal.shirelang.completion.dataprovider.BuiltinCommand
import com.phodal.shirelang.completion.provider.*
import com.phodal.shirelang.psi.ShireFrontMatterEntry
import com.phodal.shirelang.psi.ShireTypes
import com.phodal.shirelang.psi.ShireUsed

class ShireCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, identifierAfter(ShireTypes.CODE_BLOCK_START), CodeFenceLanguageCompletion())

        extend(CompletionType.BASIC, identifierAfter(ShireTypes.AGENT_START), CustomAgentCompletion())

        extend(CompletionType.BASIC, identifierAfter(ShireTypes.VARIABLE_START), VariableCompletionProvider())

        // FOR
        extend(CompletionType.BASIC, identifierAfter(ShireTypes.VARIABLE_START), AgentToolOverviewCompletion())

        extend(CompletionType.BASIC, identifierAfter(ShireTypes.COMMAND_START), BuiltinCommandCompletion())

        extend(CompletionType.BASIC, identifierAfter(ShireTypes.FRONTMATTER_START), HobbitHoleKeyCompletion())
        extend(CompletionType.BASIC, hobbitHolePattern(), HobbitHoleCompletion())
        extend(CompletionType.BASIC, whenConditionPattern(), WhenConditionCompletionProvider())
        extend(CompletionType.BASIC, whenConditionFuncPattern(), WhenConditionFunctionCompletionProvider())

        // command completion
        extend(
            CompletionType.BASIC,
            (valuePatterns(listOf(BuiltinCommand.FILE, BuiltinCommand.RUN, BuiltinCommand.WRITE))),
            FileReferenceLanguageProvider()
        )
        extend(
            CompletionType.BASIC,
            commandPropPattern(BuiltinCommand.REV.commandName),
            RevisionReferenceLanguageProvider()
        )
        extend(
            CompletionType.BASIC,
            commandPropPattern(BuiltinCommand.SYMBOL.commandName),
            SymbolReferenceLanguageProvider()
        )
        extend(
            CompletionType.BASIC,
            commandPropPattern(BuiltinCommand.FILE_FUNC.commandName),
            FileFunctionProvider()
        )
        extend(
            CompletionType.BASIC,
            commandPropPattern(BuiltinCommand.Refactor.commandName),
            RefactoringFuncProvider()
        )
        extend(
            CompletionType.BASIC,
            commandPropPattern(BuiltinCommand.RUN.commandName),
            ProjectRunProvider()
        )
    }

    private inline fun <reified I : PsiElement> psiElement() = PlatformPatterns.psiElement(I::class.java)

    private fun baseUsedPattern(): PsiElementPattern.Capture<PsiElement> =
        PlatformPatterns.psiElement()
            .inside(psiElement<ShireUsed>())

    private fun identifierAfter(type: IElementType): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement(ShireTypes.IDENTIFIER)
            .afterLeaf(PlatformPatterns.psiElement().withElementType(type))

    private fun commandPropPattern(text: String): PsiElementPattern.Capture<PsiElement> =
        baseUsedPattern()
            .withElementType(ShireTypes.COMMAND_PROP)
            .afterLeafSkipping(
                PlatformPatterns.psiElement(ShireTypes.COLON),
                PlatformPatterns.psiElement().withText(text)
            )

    private fun hobbitHolePattern(): ElementPattern<out PsiElement> {
        return PlatformPatterns.psiElement()
            .inside(psiElement<ShireFrontMatterEntry>())
            .afterLeafSkipping(
                PlatformPatterns.psiElement(ShireTypes.COLON),
                PlatformPatterns.psiElement().withElementType(ShireTypes.FRONT_MATTER_ID)
            )
    }

    private fun whenConditionPattern(): ElementPattern<out PsiElement> {
        return PlatformPatterns.psiElement()
            .inside(psiElement<ShireFrontMatterEntry>())
            .afterLeaf(PlatformPatterns.psiElement().withText("$"))
    }

    private fun whenConditionFuncPattern(): ElementPattern<out PsiElement> {
        return PlatformPatterns.psiElement(ShireTypes.IDENTIFIER)
            .inside(psiElement<ShireFrontMatterEntry>())
            .afterLeafSkipping(
                PlatformPatterns.psiElement(ShireTypes.IDENTIFIER),
                PlatformPatterns.psiElement(ShireTypes.DOT),
            )
    }

    private fun valuePatterns(listOf: List<BuiltinCommand>): ElementPattern<out PsiElement> {
        val patterns = listOf.map { commandPropPattern(it.commandName) }
        return PlatformPatterns.or(*patterns.toTypedArray())
    }
}
