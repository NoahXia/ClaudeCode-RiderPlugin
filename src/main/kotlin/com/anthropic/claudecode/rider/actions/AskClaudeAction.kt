package com.anthropic.claudecode.rider.actions

import com.anthropic.claudecode.rider.toolwindow.ClaudeToolWindowPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Sends selected editor text to the active Claude session via `insert_at_mention`.
 *
 * [prefix] is prepended to the selected code so Claude knows what to do with it.
 * If nothing is selected, the action is disabled.
 */
class AskClaudeAction(text: String, private val prefix: String) : AnAction(text) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor  = e.getData(CommonDataKeys.EDITOR) ?: return
        val selected = editor.selectionModel.selectedText?.takeIf { it.isNotBlank() } ?: return

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude") ?: return
        if (!toolWindow.isVisible) toolWindow.show()

        // Give JCEF a moment to become visible before injecting text.
        ApplicationManager.getApplication().invokeLater {
            val panel = toolWindow.contentManager.contents
                .mapNotNull { it.component as? ClaudeToolWindowPanel }
                .firstOrNull() ?: return@invokeLater

            val text = if (prefix.isBlank()) selected else "$prefix$selected"
            panel.browserManager?.insertAtMention(text)
        }
    }

    override fun update(e: AnActionEvent) {
        val hasSelection = e.getData(CommonDataKeys.EDITOR)
            ?.selectionModel?.hasSelection() == true
        e.presentation.isEnabled = e.project != null && hasSelection
    }
}
