package com.anthropic.claudecode.rider

import com.anthropic.claudecode.rider.process.ClaudeProcessConfig
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Runs after project open to validate the environment.
 * On Windows, warns if Git Bash is not installed (Claude CLI requires it).
 */
class ClaudeStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
        if (!isWindows) return

        if (ClaudeProcessConfig.findGitBash() == null) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Claude Code")
                ?.createNotification(
                    "Claude Code: Git for Windows Not Found",
                    "Claude Code requires Git for Windows (Git Bash). " +
                    "Please install it from <a href='https://git-scm.com/download/win'>git-scm.com</a>.",
                    NotificationType.WARNING
                )
                ?.setImportant(false)
                ?.notify(project)
        }
    }
}
