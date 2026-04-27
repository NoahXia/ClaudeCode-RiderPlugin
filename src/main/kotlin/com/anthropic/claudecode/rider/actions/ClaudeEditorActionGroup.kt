package com.anthropic.claudecode.rider.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/**
 * "Claude" submenu in the editor right-click popup.
 * Visible whenever a file is open; selection-dependent actions disable themselves individually.
 */
class ClaudeEditorActionGroup : ActionGroup() {

    private val children = arrayOf<AnAction>(
        AskClaudeAction("Ask Claude", ""),
        AskClaudeAction("Explain with Claude", "Explain the following code: "),
        AskClaudeAction("Fix with Claude", "Fix the following code: "),
        SendFileAction()
    )

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = children

    override fun update(e: AnActionEvent) {
        val hasFile = e.getData(CommonDataKeys.VIRTUAL_FILE) != null
        e.presentation.isVisible = e.project != null && hasFile
        e.presentation.isEnabled = hasFile
    }
}
