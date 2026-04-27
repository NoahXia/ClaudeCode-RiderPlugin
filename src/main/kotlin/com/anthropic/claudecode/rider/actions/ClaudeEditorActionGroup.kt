package com.anthropic.claudecode.rider.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/**
 * "Claude" submenu in the editor right-click popup.
 * Only visible when there is a selection in the editor.
 */
class ClaudeEditorActionGroup : ActionGroup() {

    private val children = arrayOf<AnAction>(
        AskClaudeAction("Ask Claude", ""),
        AskClaudeAction("Explain with Claude", "Explain the following code:\n\n"),
        AskClaudeAction("Fix with Claude", "Fix the following code:\n\n")
    )

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = children

    override fun update(e: AnActionEvent) {
        val hasSelection = e.getData(CommonDataKeys.EDITOR)
            ?.selectionModel?.hasSelection() == true
        e.presentation.isVisible = e.project != null && hasSelection
        e.presentation.isEnabled = hasSelection
    }
}
