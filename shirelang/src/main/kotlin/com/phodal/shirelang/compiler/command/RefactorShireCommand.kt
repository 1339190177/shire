package com.phodal.shirelang.compiler.command

import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.phodal.shirelang.completion.dataprovider.BuiltinRefactorCommand
import com.phodal.shirecore.provider.shire.RefactoringTool
import com.phodal.shirelang.psi.ShireFile

/**
 * `RefactorShireCommand` is a class that implements the `ShireCommand` interface. It is responsible for executing
 * refactoring commands within a project based on the provided argument and text segment.
 *
 * The class has three private properties:
 * - `myProject`: A `Project` instance representing the current project.
 * - `argument`: A `String` containing the refactoring command to be executed.
 * - `textSegment`: A `String` containing the text segment relevant to the refactoring command.
 *
 * The `execute` method is the main entry point for executing a refactoring command. It first attempts to parse the
 * `argument` into a `BuiltinRefactorCommand` using the `fromString` method. If the command is not recognized, a
 * message indicating that it is unknown is returned.
 *
 * Depending on the type of refactoring command, the `execute` method performs different actions:
 * - For `BuiltinRefactorCommand.RENAME`: The method splits the `textSegment` using " to " and assigns the result to
 *   the variables `from` and `to`. It then performs a rename operation on a class in Java or Kotlin. The actual
 *   implementation of the rename operation is not provided in the code snippet, but it suggests using `RenameQuickFix`.
 * @see com.intellij.jvm.analysis.quickFix.RenameQuickFix for kotlin
 * @see com.intellij.spellchecker.quickfixes.RenameTo for by typos rename which will be better
 * - For `BuiltinRefactorCommand.SAFEDELETE`: The method checks the usage of the symbol before deleting it. It
 *   suggests using `SafeDeleteFix` as an example.
 * @see org.jetbrains.kotlin.idea.inspections.SafeDeleteFix for Kotlin
 * @see com.intellij.codeInsight.daemon.impl.quickfix.SafeDeleteFix for Java
 * - For `BuiltinRefactorCommand.DELETE`: The method does not perform any specific action, but it is expected to be
 *   implemented to handle the deletion of elements.
 * @see com.intellij.codeInspection.LocalQuickFixOnPsiElement
 * - For `BuiltinRefactorCommand.MOVE`: The method suggests using ` as an example for moving elements move package fix to a different package.
 * @see com.intellij.codeInspection.MoveToPackageFix
 *
 *
 * The `execute` method always returns `null`, indicating that the refactoring command has been executed, but the
 * actual result of the refactoring is not returned.
 *
 * This class is designed to be used within a refactoring tool or plugin that provides built-in refactoring commands.
 * It demonstrates how to handle different refactoring scenarios
 */
class RefactorShireCommand(val myProject: Project, private val argument: String, private val textSegment: String) :
    ShireCommand {
    override suspend fun doExecute(): String? {
        var currentEditFile: PsiFile? = null
        val editor = FileEditorManager.getInstance(myProject).selectedTextEditor
        if (editor != null) {
            val currentFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return "File not found"
            val currentPsiFile = PsiManager.getInstance(myProject).findFile(currentFile)

            // will not handle the case where the current file is not a ShireFile
            currentEditFile = if (currentPsiFile is ShireFile) {
                null
            } else {
                currentPsiFile
            }
        }

        val language = currentEditFile?.language ?: Language.findLanguageByID("JAVA") ?: return "Language not found"
        val refactoringTool = RefactoringTool.forLanguage(language)
            ?: return "Refactoring tool not found for Java"

        val command = BuiltinRefactorCommand.fromString(argument) ?: return "Unknown refactor command: $argument"

        when (command) {
            BuiltinRefactorCommand.RENAME -> {
                val (from, to) = textSegment.split(" to ")
                refactoringTool.rename(from.trim(), to.trim(), currentEditFile)
            }

            BuiltinRefactorCommand.SAFE_DELETE -> {
                val psiFile = refactoringTool.lookupFile(textSegment.trim()) ?: return "File not found"
                refactoringTool.safeDelete(psiFile)
            }

            BuiltinRefactorCommand.DELETE -> {
                val psiFile = refactoringTool.lookupFile(textSegment.trim()) ?: return "File not found"
                refactoringTool.safeDelete(psiFile)
            }

            BuiltinRefactorCommand.MOVE -> {
                val (from, to) = textSegment.split(" to ")
                val psiFile = refactoringTool.lookupFile(from.trim()) ?: return "File not found"
                refactoringTool.move(psiFile, to.trim())
            }
        }

        return null
    }
}

