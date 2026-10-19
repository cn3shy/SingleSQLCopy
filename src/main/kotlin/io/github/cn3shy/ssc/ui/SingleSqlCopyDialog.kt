package io.github.cn3shy.ssc.ui

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.DialogWrapperAction
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import io.github.cn3shy.ssc.core.MinifyOptions
import io.github.cn3shy.ssc.core.SqlDialect
import io.github.cn3shy.ssc.core.SqlOneLineMinifier
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * “Single Sql Copy” 主对话框。
 *
 * 对话框为非模态窗口，打开后仍可继续操作 IDEA 主窗口，例如从其他文件复制
 * 内容并粘贴到输入区。输出区只做视觉换行，复制或写回时仍保持原始单行内容。
 */
class SingleSqlCopyDialog(
    private val project: Project?,
    private val editor: Editor,
    private val initialSql: String,
    private val editorHasSelection: Boolean
) : DialogWrapper(project, false, IdeModalityType.MODELESS) {

    private val minifier = SqlOneLineMinifier()

    private lateinit var inputTextArea: JBTextArea
    private lateinit var outputTextArea: JBTextArea
    private lateinit var dialectComboBox: JComboBox<String>
    private lateinit var removeCommentsCheckBox: JCheckBox
    private lateinit var keepOracleHintsCheckBox: JCheckBox
    private lateinit var collapseWhitespaceCheckBox: JCheckBox

    private val selectionListener = object : SelectionListener {
        override fun selectionChanged(event: SelectionEvent) {
            replaceAction.isEnabled = editor.selectionModel.hasSelection()
        }
    }

    private val copyAction = object : DialogWrapperAction("复制") {
        override fun doAction(e: ActionEvent?) {
            copyOutput()
        }
    }

    private val replaceAction = object : DialogWrapperAction("替换选中") {
        init {
            isEnabled = editorHasSelection
        }

        override fun doAction(e: ActionEvent?) {
            replaceSelection()
        }
    }

    private val insertAction = object : DialogWrapperAction("插入光标处") {
        override fun doAction(e: ActionEvent?) {
            insertAtCaret()
        }
    }

    init {
        setTitle("Single Sql Copy")
        setOKButtonText("压缩")
        setCancelButtonText("关闭")
        init()
        editor.selectionModel.addSelectionListener(selectionListener)
        compress()
        SwingUtilities.invokeLater {
            if (outputTextArea.text.isNotEmpty()) {
                outputTextArea.requestFocusInWindow()
                outputTextArea.selectAll()
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, JBUI.scale(10)))
        root.border = JBUI.Borders.empty(10)
        root.preferredSize = Dimension(980, 700)

        val monoFont = Font(Font.MONOSPACED, Font.PLAIN, 12)

        inputTextArea = JBTextArea(initialSql, 10, 80).apply {
            font = monoFont
            lineWrap = false
            toolTipText = "可继续从 IDEA 主窗口复制 SQL 并粘贴到此输入区"
        }

        outputTextArea = JBTextArea(10, 80).apply {
            font = monoFont
            lineWrap = true
            wrapStyleWord = false
            toolTipText = "压缩结果，可编辑并自动换行；执行压缩后默认全选"
        }

        val inputScroll = JBScrollPane(inputTextArea)
        val outputScroll = JBScrollPane(outputTextArea)

        val editorsPanel = JPanel(GridLayout(2, 1, 0, JBUI.scale(8)))
        editorsPanel.add(
            createLabeledPanel(
                if (editorHasSelection) {
                    "输入 SQL（当前使用编辑器选中内容）"
                } else {
                    "输入 SQL（当前使用编辑器全文）"
                },
                inputScroll
            )
        )
        editorsPanel.add(
            createLabeledPanel(
                "输出 SQL（可编辑、自动换行，压缩后默认全选）",
                outputScroll
            )
        )
        root.add(editorsPanel, BorderLayout.CENTER)

        dialectComboBox = JComboBox(arrayOf("Oracle", "Generic")).apply {
            selectedIndex = 0
            toolTipText = "Oracle 识别 Hint 与 q-quote；Generic 额外支持 # 行注释"
        }

        removeCommentsCheckBox = JCheckBox("删除注释", true).apply {
            toolTipText = "删除 --、#（Generic）和普通 /* ... */ 注释"
        }

        keepOracleHintsCheckBox = JCheckBox("保留 Oracle Hint", true).apply {
            toolTipText = "保留 /*+ ... */；仅当启用“删除注释”时此选项才会控制 Hint"
        }

        collapseWhitespaceCheckBox = JCheckBox("折叠空白", true).apply {
            toolTipText = "把普通空白折叠为单个空格；字符串内部不受影响"
        }

        removeCommentsCheckBox.addActionListener {
            keepOracleHintsCheckBox.isEnabled = removeCommentsCheckBox.isSelected
        }
        keepOracleHintsCheckBox.isEnabled = removeCommentsCheckBox.isSelected

        val optionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
            add(JBLabel("方言:"))
            add(dialectComboBox)
            add(removeCommentsCheckBox)
            add(keepOracleHintsCheckBox)
            add(collapseWhitespaceCheckBox)
        }
        root.add(optionsPanel, BorderLayout.SOUTH)

        return root
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            getOKAction(),
            copyAction,
            replaceAction,
            insertAction,
            getCancelAction()
        )
    }

    override fun doOKAction() {
        compress()
    }

    override fun dispose() {
        if (!editor.isDisposed) {
            editor.selectionModel.removeSelectionListener(selectionListener)
        }
        super.dispose()
    }

    private fun compress() {
        val source = inputTextArea.text
        if (source.isBlank()) {
            outputTextArea.text = ""
            setErrorText("输入 SQL 不能为空")
            return
        }

        setErrorText(null)
        val options = currentOptions()

        val result = try {
            minifier.minify(source, options)
        } catch (ex: Exception) {
            setErrorText("压缩失败: ${ex.message ?: "未知错误"}")
            return
        }

        outputTextArea.text = result.output
        outputTextArea.selectAll()
    }

    private fun currentOptions(): MinifyOptions {
        val dialect = if (dialectComboBox.selectedIndex == 1) {
            SqlDialect.GENERIC
        } else {
            SqlDialect.ORACLE
        }
        return MinifyOptions(
            dialect = dialect,
            removeComments = removeCommentsCheckBox.isSelected,
            keepOracleHints = keepOracleHintsCheckBox.isSelected,
            collapseWhitespace = collapseWhitespaceCheckBox.isSelected,
            preserveStringNewlines = true
        )
    }

    private fun copyOutput() {
        val text = outputTextArea.text
        if (text.isEmpty()) {
            setErrorText("输出为空，无法复制")
            return
        }
        try {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            setErrorText(null)
        } catch (ex: Exception) {
            setErrorText("复制失败: ${ex.message ?: "未知错误"}")
        }
    }

    private fun replaceSelection() {
        val text = outputTextArea.text
        if (text.isEmpty()) {
            setErrorText("输出为空，无法替换")
            return
        }

        val document = editor.document
        if (!document.isWritable) {
            setErrorText("当前文档为只读，无法替换")
            return
        }

        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            replaceAction.isEnabled = false
            setErrorText("原编辑器没有选中内容")
            return
        }

        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd
        if (start < 0 || end < start || end > document.textLength) {
            setErrorText("选中区域无效")
            return
        }

        try {
            WriteCommandAction.runWriteCommandAction(project) {
                document.replaceString(start, end, text)
            }
            editor.caretModel.moveToOffset(start + text.length)
            editor.selectionModel.setSelection(start, start + text.length)
            setErrorText(null)
        } catch (ex: Exception) {
            setErrorText("替换失败: ${ex.message ?: "未知错误"}")
        }
    }

    private fun insertAtCaret() {
        val text = outputTextArea.text
        if (text.isEmpty()) {
            setErrorText("输出为空，无法插入")
            return
        }

        val document = editor.document
        if (!document.isWritable) {
            setErrorText("当前文档为只读，无法插入")
            return
        }

        val offset = editor.caretModel.offset.coerceIn(0, document.textLength)
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                document.insertString(offset, text)
            }
            editor.caretModel.moveToOffset(offset + text.length)
            setErrorText(null)
        } catch (ex: Exception) {
            setErrorText("插入失败: ${ex.message ?: "未知错误"}")
        }
    }

    private fun createLabeledPanel(title: String, content: JComponent): JPanel {
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBLabel(title), BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        }
    }
}
