package com.phodal.shirecore.ui

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.NullableComponent
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.phodal.shirecore.provider.sketch.ExtensionLangSketch
import com.phodal.shirecore.provider.sketch.LanguageSketchProvider
import com.phodal.shirecore.sketch.LangSketch
import com.phodal.shirecore.sketch.highlight.CodeHighlightSketch
import com.phodal.shirecore.ui.input.ShireInput
import com.phodal.shirecore.utils.markdown.CodeFence
import com.phodal.shirecore.utils.markdown.CodeFenceLanguage
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class ShirePanelView(val project: Project, showInput: Boolean = true) : SimpleToolWindowPanel(true, true),
    NullableComponent {
    private var progressBar: CustomProgressBar = CustomProgressBar(this)
    private var shireInput: ShireInput = ShireInput(project)

    private var myList = JPanel(VerticalLayout(JBUI.scale(0))).apply {
        this.isOpaque = true
        this.background = UIUtil.getLabelBackground()
    }

    private var userPrompt: JPanel = JPanel(BorderLayout()).apply {
        this.isOpaque = true
        this.background = JBUI.CurrentTheme.CustomFrameDecorations.titlePaneInactiveBackground()
        this.border = JBUI.Borders.empty(10, 0)
    }

    private var contentPanel = JPanel(BorderLayout()).apply {
        this.isOpaque = true
        this.background = UIUtil.getLabelBackground()
    }

    private var panelContent: DialogPanel = panel {
        row { cell(progressBar).fullWidth() }
        row { cell(userPrompt).fullWidth().fullHeight() }
        row { cell(myList).fullWidth().fullHeight() }
    }

    private val scrollPanel: JBScrollPane = JBScrollPane(
        panelContent,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    ).apply {
        this.verticalScrollBar.autoscrolls = true
    }

    var handleCancel: ((String) -> Unit)? = null

    init {
        contentPanel.add(scrollPanel, BorderLayout.CENTER)

        if (showInput) {
            contentPanel.add(shireInput, BorderLayout.SOUTH)
        }

        setContent(contentPanel)
    }

    fun onStart() {
        initializePreAllocatedBlocks(project)
        progressBar.isIndeterminate = true
    }

    private val blockViews: MutableList<LangSketch> = mutableListOf()
    private fun initializePreAllocatedBlocks(project: Project) {
        repeat(16) {
            runInEdt {
                val codeBlockViewer = CodeHighlightSketch(project, "", PlainTextLanguage.INSTANCE)
                blockViews.add(codeBlockViewer)
                myList.add(codeBlockViewer)
            }
        }
    }

    fun addRequestPrompt(text: String) {
        runInEdt {
            val codeBlockViewer = CodeHighlightSketch(project, text, CodeFenceLanguage.findLanguage("Markdown")).apply {
                initEditor(text)
            }

            codeBlockViewer.editorFragment?.setCollapsed(true)
            codeBlockViewer.editorFragment!!.updateExpandCollapseLabel()
            codeBlockViewer.editorFragment!!.editor.backgroundColor = JBColor(0xF7FAFDF, 0x2d2f30)

            val panel = panel {
                row {
                    cell(codeBlockViewer).fullWidth()
                }
            }.also {
                it.border = JBUI.Borders.empty(10, 0)
            }

            userPrompt.add(panel, BorderLayout.CENTER)

            this.revalidate()
            this.repaint()
        }
    }

    fun onUpdate(text: String) {
        val codeFenceList = CodeFence.parseAll(text)

        runInEdt {
            codeFenceList.forEachIndexed { index, codeFence ->
                if (index < blockViews.size) {
                    var langSketch: ExtensionLangSketch? = null
                    if (codeFence.originLanguage != null && codeFence.isComplete && blockViews[index] !is ExtensionLangSketch) {
                        langSketch = LanguageSketchProvider.provide(codeFence.originLanguage)
                            ?.create(project, codeFence.text)
                    }

                    if (langSketch != null) {
                        val oldComponent = blockViews[index]
                        blockViews[index] = langSketch
                        myList.remove(index)
                        myList.add(langSketch.getComponent(), index)

                        oldComponent.dispose()
                    } else {
                        blockViews[index].apply {
                            updateLanguage(codeFence.ideaLanguage, codeFence.originLanguage)
                            updateViewText(codeFence.text)
                        }
                    }
                } else {
                    val codeBlockViewer = CodeHighlightSketch(project, codeFence.text, PlainTextLanguage.INSTANCE)
                    blockViews.add(codeBlockViewer)
                    myList.add(codeBlockViewer.getComponent())
                }
            }

            while (blockViews.size > codeFenceList.size) {
                val lastIndex = blockViews.lastIndex
                blockViews.removeAt(lastIndex)
                myList.remove(lastIndex)
            }

            myList.revalidate()
            myList.repaint()

            scrollToBottom()
        }
    }

    fun onFinish(text: String) {
        runInEdt {
            blockViews.filter { it.getViewText().isNotEmpty() }.forEach {
                it.doneUpdateText(text)
            }

            blockViews.filter { it.getViewText().isEmpty() }.forEach {
                myList.remove(it.getComponent())
            }
        }

        progressBar.isIndeterminate = false
        progressBar.isVisible = false
        scrollToBottom()
    }

    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val verticalScrollBar = scrollPanel.verticalScrollBar
            verticalScrollBar.value = verticalScrollBar.maximum
        }
    }

    fun resize() {
        val height = myList.components.sumOf { it.height }
        if (height < 600) {
            this.minimumSize = JBUI.size(800, height)
        } else {
            this.minimumSize = JBUI.size(800, 600)
        }
    }

    override fun isNull(): Boolean {
        return !isVisible
    }

    fun cancel(s: String) = runCatching { handleCancel?.invoke(s) }
}

fun <T : JComponent> Cell<T>.fullWidth(): Cell<T> {
    return this.align(AlignX.FILL)
}

fun <T : JComponent> Cell<T>.fullHeight(): Cell<T> {
    return this.align(AlignY.FILL)
}
