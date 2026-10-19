package io.github.cn3shy.ssc.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import io.github.cn3shy.ssc.ui.SingleSqlCopyDialog

/**
 * Single Sql Copy 的编辑器入口。
 *
 * 有选中内容时把选中文本交给对话框，否则使用当前编辑器全文。
 */
class SingleSqlCopyAction : AnAction(
    "Single Sql Copy",
    "将多行 SQL 压缩为紧凑的一行，并支持复制、替换选中和插入光标处",
    null
) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectionModel = editor.selectionModel
        val hasSelection = selectionModel.hasSelection()
        val sql = if (hasSelection) {
            selectionModel.selectedText ?: ""
        } else {
            editor.document.text
        }

        SingleSqlCopyDialog(
            project = e.project,
            editor = editor,
            initialSql = sql,
            editorHasSelection = hasSelection
        ).show()
    }
}
