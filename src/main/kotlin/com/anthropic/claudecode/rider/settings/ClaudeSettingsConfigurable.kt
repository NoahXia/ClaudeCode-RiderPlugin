package com.anthropic.claudecode.rider.settings

import com.anthropic.claudecode.rider.process.ClaudeProcessConfig
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import javax.swing.JButton

/**
 * Settings UI page shown under Settings -> Tools -> Claude Code.
 */
class ClaudeSettingsConfigurable : BoundConfigurable("Claude Code") {

    private val settings = ClaudeSettings.getInstance()

    override fun createPanel(): DialogPanel = panel {
        group("Claude CLI") {
            row("Executable path:") {
                textFieldWithBrowseButton(
                    FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                        .apply { title = "Select Claude Executable" }
                )
                    .bindText(settings::claudeExecutablePath)
                    .comment(
                        "Leave empty to auto-detect claude from PATH or the VS Code extension directory.",
                        100
                    )
                    .columns(COLUMNS_LARGE)
            }
            row {
                button("Test Connection") {
                    val path = ClaudeProcessConfig.resolveBinaryPath()
                    if (path == null) {
                        Messages.showErrorDialog(
                            "Claude executable not found.\n\nMake sure 'claude' is on your PATH or set the executable path above.",
                            "Claude Not Found"
                        )
                    } else {
                        try {
                            val proc = ProcessBuilder(path, "--version")
                                .redirectErrorStream(true)
                                .start()
                            val output = proc.inputStream.bufferedReader().readText().trim()
                            proc.waitFor()
                            Messages.showInfoMessage("Found: $path\n\n$output", "Claude Connection OK")
                        } catch (e: Exception) {
                            Messages.showErrorDialog("Failed to run claude --version:\n${e.message}", "Error")
                        }
                    }
                }
            }
        }

        group("Behavior") {
            row("Initial permission mode:") {
                comboBox(listOf("default", "acceptEdits", "plan", "bypassPermissions"))
                    .bindItem(
                        getter = { settings.initialPermissionMode },
                        setter = { settings.initialPermissionMode = it ?: "default" }
                    )
                    .comment("Controls what Claude is allowed to do without asking for confirmation")
            }
            row {
                checkBox("Use Ctrl+Enter to send messages (instead of Enter)")
                    .bindSelected(settings::useCtrlEnterToSend)
            }
            row {
                checkBox("Auto-save files before Claude reads or writes them")
                    .bindSelected(settings::autosave)
            }
        }

        group("Environment Variables") {
            row {
                label("Extra environment variables passed to the Claude process:")
            }
            row {
                textArea()
                    .comment("One per line, format: NAME=VALUE", 80)
                    .rows(4)
                    .apply {
                        component.text = settings.environmentVariables
                            .filter { it.name.isNotBlank() }
                            .joinToString("\n") { "${it.name}=${it.value}" }

                        onApply {
                            settings.environmentVariables = component.text
                                .lines()
                                .filter { it.contains("=") }
                                .map { line ->
                                    val idx = line.indexOf('=')
                                    com.anthropic.claudecode.rider.process.EnvVar(
                                        name = line.substring(0, idx).trim(),
                                        value = line.substring(idx + 1).trim()
                                    )
                                }.toMutableList()
                        }
                    }
            }
        }
    }
}
