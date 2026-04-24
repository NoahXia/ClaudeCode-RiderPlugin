package com.anthropic.claudecode.rider.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Opens or focuses the Claude Code tool window.
 * Mapped to Ctrl+Escape (mirrors the VS Code extension shortcut).
 */
class OpenClaudeAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude") ?: return
        if (!toolWindow.isVisible) {
            toolWindow.show()
        } else {
            toolWindow.activate(null)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
