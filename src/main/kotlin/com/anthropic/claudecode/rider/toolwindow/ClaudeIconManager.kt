package com.anthropic.claudecode.rider.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.Icon

/**
 * Manages the Claude tool window icon state: idle / pending / done.
 *
 * Idle:    standard Claude logo
 * Pending: Claude logo with blue dot (AI is generating a response)
 * Done:    Claude logo with orange dot (response finished, window not focused)
 */
object ClaudeIconManager {

    private val ICON_IDLE    = IconLoader.getIcon("/icons/claude-logo.svg", ClaudeIconManager::class.java)
    private val ICON_PENDING = IconLoader.getIcon("/icons/claude-logo-pending.svg", ClaudeIconManager::class.java)
    private val ICON_DONE    = IconLoader.getIcon("/icons/claude-logo-done.svg", ClaudeIconManager::class.java)

    fun setPending(project: Project) = setIcon(project, ICON_PENDING)
    fun setDone(project: Project) = setIcon(project, ICON_DONE)
    fun setIdle(project: Project) = setIcon(project, ICON_IDLE)

    private fun setIcon(project: Project, icon: Icon) {
        ApplicationManager.getApplication().invokeLater {
            ToolWindowManager.getInstance(project).getToolWindow("Claude")?.setIcon(icon)
        }
    }
}
