package com.phodal.shire.inline

import com.intellij.icons.AllIcons
import com.intellij.ide.KeyboardAwareFocusOwner
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI.CurrentTheme
import com.phodal.shirecore.ShireCoroutineScope
import com.phodal.shirecore.llm.LlmProvider
import com.phodal.shirecore.runner.console.cancelHandler
import com.phodal.shirecore.ui.ShirePanelView
import com.phodal.shirecore.ui.input.ShireLineBorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.event.*
import java.awt.geom.Rectangle2D
import javax.swing.*

class ShireInlineChatPanel(val editor: Editor) : JPanel(GridBagLayout()), EditorCustomElementRenderer,
    Disposable {
    var inlay: Inlay<*>? = null
    val inputPanel = ShireInlineChatInputPanel(this, onSubmit = { input ->
        this.centerPanel.isVisible = true
        val project = editor.project!!

        val prompt = ShireInlineChatService.getInstance().prompt(project, input)
        val flow: Flow<String>? = LlmProvider.provider(project)?.stream(prompt, "", false)

        val panelView = ShirePanelView(project, showInput = false)
        panelView.minimumSize = Dimension(800, 40)
        setContent(panelView)

        ShireCoroutineScope.scope(project).launch {
            val suggestion = StringBuilder()
            panelView.onStart()

            flow?.cancelHandler { panelView.handleCancel = it }?.cancellable()?.collect { char ->
                suggestion.append(char)

                invokeLater {
                    panelView.onUpdate(suggestion.toString())
                    panelView.resize()
                }
            }

            panelView.resize()
            panelView.onFinish(suggestion.toString())
        }
    })
    private var centerPanel: JPanel = JPanel(BorderLayout())
    private var container: Container? = null

    init {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(12, 12, 12, 12),
                RoundedLineBorder(JBColor.LIGHT_GRAY, 18, 1)
            ),
            BorderFactory.createCompoundBorder(
                ShireLineBorder(JBColor.border(), 1, true, 8),
                BorderFactory.createMatteBorder(10, 10, 10, 10, JBColor.PanelBackground)
            )
        )

        isOpaque = false
        cursor = Cursor.getPredefinedCursor(0)

        val c = GridBagConstraints()
        c.gridx = 0
        c.gridy = 0
        c.weightx = 1.0
        c.fill = 2
        add(inputPanel, c)

        val submitPresentation = Presentation("Submit")
        submitPresentation.icon = AllIcons.Actions.Cancel
        val submitButton = ActionButton(
            DumbAwareAction.create {
                ShireInlineChatService.getInstance().closeInlineChat(editor)
            },
            submitPresentation, "", Dimension(24, 20)
        )
        submitButton.isOpaque = true
        submitButton.background = JBColor.PanelBackground
        c.gridx = 1
        c.weightx = 0.0
        c.fill = 1
        add(submitButton, c)

        val jPanel = JPanel(BorderLayout())
        jPanel.isVisible = false
        jPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                IdeFocusManager.getInstance(editor.project).requestFocus(inputPanel.getInputComponent(), true)
            }
        })
        this.centerPanel = jPanel

        c.gridx = 0
        c.gridy = 1
        c.fill = 1
        add(this.centerPanel, c)

        this.inAllChildren { child ->
            child.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) {
                    this@ShireInlineChatPanel.redraw()
                }
            })
        }
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = size.width

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = size.height

    private fun redraw() {
        ApplicationManager.getApplication().invokeLater {
            if (this.size.height != this.getMinimumSize().height) {
                this.size = Dimension(800, this.getMinimumSize().height)
                this.inlay?.update()

                this.revalidate()
                this.repaint()
            }
        }
    }

    fun createInlay(offset: Int) {
        inlay = editor.inlayModel.addBlockElement(offset, false, true, 1, this)
    }

    fun setInlineContainer(container: Container) {
        this.container = container
    }

    override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
        bounds = inlay.bounds ?: return
        revalidate()
        repaint()
    }

    fun setContent(content: JComponent) {
        content.isOpaque = true
        ApplicationManager.getApplication().invokeLater {
            if (!this.centerPanel.isVisible) {
                this.centerPanel.isVisible = true
            }

            this.centerPanel.removeAll()
            this.centerPanel.add(content, BorderLayout.CENTER)

            this@ShireInlineChatPanel.redraw()
        }
    }

    fun JComponent.inAllChildren(callback: (JComponent) -> Unit) {
        callback(this)
        components.forEach { component ->
            if (component is JComponent) {
                component.inAllChildren(callback)
            }
        }
    }

    override fun dispose() {
        inlay?.dispose()
    }
}

class ShireInlineChatInputPanel(
    val shireInlineChatPanel: ShireInlineChatPanel,
    val onSubmit: (String) -> Unit,
) : JPanel(GridBagLayout()) {
    private val textArea: JBTextArea

    init {
        layout = BorderLayout()
        textArea = object: JBTextArea(), KeyboardAwareFocusOwner {
            override fun skipKeyEventDispatcher(event: KeyEvent): Boolean = true

            init {
                isOpaque = false
                isFocusable = true
                lineWrap = true
                wrapStyleWord = true
                border = BorderFactory.createEmptyBorder(8, 6, 8, 6)
            }
        }

        border = ShireLineBorder(CurrentTheme.Focus.focusColor(), 1, true, 8)

        // escape to close
        textArea.actionMap.put("escapeAction", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                ShireInlineChatService.getInstance().closeInlineChat(shireInlineChatPanel.editor)
            }
        })
        textArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escapeAction")
        // submit with enter
        textArea.actionMap.put("enterAction", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                submit()
            }
        })
        textArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "enterAction")
        // newLine with shift + enter
        textArea.actionMap.put("newlineAction", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                textArea.append("\n")
            }
        })
        textArea.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "newlineAction")
        add(textArea)

        val submitPresentation = Presentation("Submit")
        submitPresentation.icon = AllIcons.Actions.Execute
        val submitButton = ActionButton(
            DumbAwareAction.create { submit() },
            submitPresentation, "", Dimension(40, 20)
        )

        add(submitButton, BorderLayout.EAST)
    }

    private fun submit() {
        val trimText = textArea.text.trim()
        textArea.text = ""
        onSubmit(trimText)
    }

    fun getInputComponent(): Component = textArea
}