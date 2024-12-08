package com.phodal.shirecore.middleware.builtin

import com.intellij.diff.DiffContentFactoryEx
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestProducer
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.combined.COMBINED_DIFF_MAIN_UI
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.ui.UIUtil
import com.phodal.shirecore.middleware.PostProcessor
import com.phodal.shirecore.middleware.PostProcessorContext
import com.phodal.shirecore.middleware.PostProcessorType

class DiffProcessor : PostProcessor {
    override val processorName: String = PostProcessorType.Diff.handleName
    override val description: String =
        "`diff` will show the diff of two texts, default is current code and llm response"

    private val diffFactory = DiffContentFactoryEx.getInstanceEx()

    override fun isApplicable(context: PostProcessorContext): Boolean {
        return true
    }

    override fun execute(
        project: Project,
        context: PostProcessorContext,
        console: ConsoleView?,
        args: List<Any>,
    ): Any {
        if (args.size < 2) {
            console?.print("DiffProcessor: not enough arguments", ConsoleViewContentType.ERROR_OUTPUT)
            return ""
        }

        val currentDocContent: DocumentContent = diffFactory.create(args[0].toString())
        val newDocContent = diffFactory.create(args[1].toString())

        val diffRequest =
            SimpleDiffRequest("Shire Diff", currentDocContent, newDocContent, "Current code", "Llm response")
        val producer = SimpleDiffRequestProducer.create("Shire Diff") {
            diffRequest
        }

        val chain = SimpleDiffRequestChain.fromProducer(producer)
        UIUtil.invokeLaterIfNeeded { ->
            DiffManager.getInstance().showDiff(project, chain, DiffDialogHints.DEFAULT)
        }
        return ""
    }
}

class ShireDiffContext(private val project: Project) : DiffContext() {
    private val mainUi get() = getUserData(COMBINED_DIFF_MAIN_UI)

    private val ownContext: UserDataHolder = UserDataHolderBase()

    override fun getProject() = project
    override fun isFocusedInWindow(): Boolean = mainUi?.isFocusedInWindow() ?: false
    override fun isWindowFocused(): Boolean = mainUi?.isWindowFocused() ?: false
    override fun requestFocusInWindow() {
        mainUi?.requestFocusInWindow()
    }

    override fun <T> getUserData(key: Key<T>): T? {
        return ownContext.getUserData(key)
    }

    override fun <T> putUserData(key: Key<T>, value: T?) {
        ownContext.putUserData(key, value)
    }
}
