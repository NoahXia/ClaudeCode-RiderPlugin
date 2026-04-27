package com.anthropic.claudecode.rider.actions

import com.anthropic.claudecode.rider.toolwindow.ClaudeToolWindowPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager
import java.nio.file.Paths

/**
 * Injects the currently open file's project-relative path into the Claude
 * input box as an @-prefixed mention.  No text selection required.
 */
class SendFileAction : AnAction("Send File to Claude") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val vFile   = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val filePath = run {
            val basePath = project.basePath
            if (basePath != null) {
                try {
                    val rel = Paths.get(basePath).relativize(Paths.get(vFile.path))
                    "@" + rel.toString().replace('\\', '/')
                } catch (_: IllegalArgumentException) {
                    "@" + vFile.path.replace('\\', '/')
                }
            } else {
                "@" + vFile.path.replace('\\', '/')
            }
        }

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude") ?: return

        val doInject = Runnable {
            val panel = toolWindow.contentManager.contents
                .mapNotNull { it.component as? ClaudeToolWindowPanel }
                .firstOrNull() ?: return@Runnable
            val bm = panel.browserManager ?: return@Runnable
            bm.notifyVisibility(true)
            bm.insertAtMention(filePath)
        }

        if (toolWindow.isVisible) doInject.run() else toolWindow.show(doInject)
    }

    override fun update(e: AnActionEvent) {
        val hasFile = e.getData(CommonDataKeys.VIRTUAL_FILE) != null
        e.presentation.isEnabled = e.project != null && hasFile
    }
}
