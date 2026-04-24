package com.anthropic.claudecode.rider.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Restarts the Claude process to start a new conversation.
 * Mapped to Ctrl+Shift+Escape.
 */
class NewConversationAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Push a create_new_conversation request to the webview, which will
        // trigger the React SessionManager to create a new session and launch_claude.
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude") ?: return
        toolWindow.show()

        val panel = toolWindow.contentManager.contents
            .mapNotNull { it.component as? com.anthropic.claudecode.rider.toolwindow.ClaudeToolWindowPanel }
            .firstOrNull() ?: return

        val msg = buildString {
            append("{\"type\":\"request\",\"channelId\":\"\",\"requestId\":\"\",")
            append("\"request\":{\"type\":\"create_new_conversation\"}}")
        }
        panel.browserManager?.sendToWebview(msg)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
