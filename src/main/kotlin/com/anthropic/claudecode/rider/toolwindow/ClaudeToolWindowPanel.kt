package com.anthropic.claudecode.rider.toolwindow

import com.anthropic.claudecode.rider.browser.ClaudeBrowserManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingConstants

class ClaudeToolWindowPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    var browserManager: ClaudeBrowserManager? = null
        private set

    init {
        background = JBUI.CurrentTheme.ToolWindow.background()
        initContent()
    }

    private fun initContent() {
        if (JBCefApp.isSupported()) {
            val manager = ClaudeBrowserManager(project, this)
            browserManager = manager
            Disposer.register(this, manager)
            add(manager.component, BorderLayout.CENTER)
        } else {
            val label = JBLabel(
                "<html><center>JCEF is not supported in this environment.<br/>" +
                        "Please use Rider locally to access Claude Code.</center></html>",
                SwingConstants.CENTER
            )
            add(label, BorderLayout.CENTER)
        }
    }

    override fun dispose() {
        // Disposer handles browserManager via Disposer.register
    }
}
