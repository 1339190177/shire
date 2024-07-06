package com.phodal.shirecore.middleware.builtin

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.util.messages.MessageBusConnection
import com.phodal.shirecore.middleware.BuiltinPostHandler
import com.phodal.shirecore.middleware.PostCodeHandleContext
import com.phodal.shirecore.middleware.PostProcessor

class VerifyCodeProcessor : PostProcessor {
    override val processorName: String = BuiltinPostHandler.VerifyCode.handleName

    override fun isApplicable(context: PostCodeHandleContext): Boolean {
        return true
    }

    override fun execute(
        project: Project,
        context: PostCodeHandleContext,
        console: ConsoleView?,
        args: List<Any>,
    ): String {
        val code = context.pipeData["output"]
        if (code !is VirtualFile) {
            console?.print("No code to verify\n", ConsoleViewContentType.ERROR_OUTPUT)
            return ""
        }

        val psiFile = PsiManager.getInstance(project).findFile(code)
        if (psiFile == null) {
            console?.print("No code to verify\n", ConsoleViewContentType.ERROR_OUTPUT)
            return ""
        }

        var errors: List<String> = listOf()
        collectSyntaxError(psiFile.virtualFile, project) {
            errors = it
        }

        if (errors.isNotEmpty()) {
            console?.print("Syntax errors found:\n${errors.joinToString("\n")}\n", ConsoleViewContentType.ERROR_OUTPUT)
        } else {
            console?.print("No syntax errors found\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        }

        return errors.joinToString("\n")
    }

    /**
     * This function is used to collect syntax errors from a given PsiFile and then execute a specified action with the list of errors.
     *
     * @param outputFile the VirtualFile representing the output file to collect syntax errors from
     * @param project the Project in which the file is located
     * @param runAction the action to run with the list of syntax errors (optional)
     */
    private fun collectSyntaxError(
        outputFile: VirtualFile,
        project: Project,
        runAction: ((errors: List<String>) -> Unit)?,
    ) {
        val sourceFile = runReadAction { PsiManager.getInstance(project).findFile(outputFile) } ?: return

        collectSyntaxError(sourceFile, runAction, outputFile, project)
    }

    /**
     * This function is used to collect syntax errors in a given source file using the PSI (Program Structure Interface) of the file.
     * It takes the source file, a callback function to run after collecting errors, an output file, and the project as parameters.
     *
     * @param sourceFile The PSI file from which syntax errors need to be collected.
     * @param runAction A callback function that takes a list of errors as input and performs some action.
     * @param outputFile The virtual file where the errors will be collected.
     * @param project The project to which the files belong.
     */
    fun collectSyntaxError(
        sourceFile: PsiFile,
        runAction: ((errors: List<String>) -> Unit)?,
        outputFile: VirtualFile,
        project: Project,
    ) {
        val collectPsiError = sourceFile.collectPsiError()
        if (collectPsiError.isNotEmpty()) {
            runAction?.invoke(collectPsiError)
            return
        }

        val document = runReadAction { FileDocumentManager.getInstance().getDocument(outputFile) } ?: return
        val range = TextRange(0, document.textLength)
        val errors = mutableListOf<String>()

        DaemonCodeAnalyzerEx.getInstance(project).restart(sourceFile);

        val hintDisposable = Disposer.newDisposable()
        val busConnection: MessageBusConnection = project.messageBus.connect(hintDisposable)
        busConnection.subscribe(
            DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC,
            SimpleCodeErrorListener(document, project, range, errors, runAction, busConnection, hintDisposable)
        )
    }

    inner class SimpleCodeErrorListener(
        private val document: Document,
        private val project: Project,
        private val range: TextRange,
        private val errors: MutableList<String>,
        private val runAction: ((errors: List<String>) -> Unit)?,
        private val busConnection: MessageBusConnection,
        private val hintDisposable: Disposable,
    ) : DaemonCodeAnalyzer.DaemonListener {
        override fun daemonFinished() {
            DaemonCodeAnalyzerEx.processHighlights(
                document,
                project,
                HighlightSeverity.ERROR,
                range.startOffset,
                range.endOffset
            ) {
                if (it.description != null) {
                    errors.add(it.description)
                }

                true
            }

            runAction?.invoke(errors)
            busConnection.disconnect()
            Disposer.dispose(hintDisposable)
        }
    }


    /**
     * This function is an extension function for PsiFile class in Kotlin.
     * It collects syntax errors present in the PsiFile and returns a list of error messages.
     * It creates a PsiSyntaxCheckingVisitor object to visit each element in the PsiFile.
     * If the element is a PsiErrorElement, it adds a message to the errors list with the error description and position.
     * Finally, it returns the list of error messages.
     */
    private fun PsiFile.collectPsiError(): MutableList<String> {
        val errors = mutableListOf<String>()
        val visitor = object : PsiSyntaxCheckingVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is PsiErrorElement) {
                    errors.add("Syntax error at position ${element.textRange.startOffset}: ${element.errorDescription}")
                }
                super.visitElement(element)
            }
        }

        this.accept(visitor)
        return errors
    }

    abstract class PsiSyntaxCheckingVisitor : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            runReadAction {
                element.children.forEach { it.accept(this) }
            }
        }
    }
}
