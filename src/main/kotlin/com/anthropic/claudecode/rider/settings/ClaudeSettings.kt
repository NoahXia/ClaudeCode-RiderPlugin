package com.anthropic.claudecode.rider.settings

import com.anthropic.claudecode.rider.process.EnvVar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import kotlinx.serialization.Serializable

/**
 * Application-level persistent settings for the Claude Code Rider plugin.
 * Stored in the IDE's config directory as XML.
 */
@Service(Service.Level.APP)
@State(
    name = "ClaudeCodeSettings",
    storages = [Storage("claude-code.xml")]
)
class ClaudeSettings : PersistentStateComponent<ClaudeSettings.State> {

    companion object {
        fun getInstance(): ClaudeSettings =
            ApplicationManager.getApplication().getService(ClaudeSettings::class.java)
    }

    data class State(
        var claudeExecutablePath: String = "",
        var initialPermissionMode: String = "default",
        var useCtrlEnterToSend: Boolean = false,
        var autosave: Boolean = true,
        var model: String = "default",
        var thinkingLevel: String = "none",
        var environmentVariables: MutableList<EnvVar> = mutableListOf()
    )

    private var state = State()

    // Delegated properties for convenient access
    var claudeExecutablePath: String
        get() = state.claudeExecutablePath
        set(value) { state.claudeExecutablePath = value }

    var initialPermissionMode: String
        get() = state.initialPermissionMode
        set(value) { state.initialPermissionMode = value }

    var useCtrlEnterToSend: Boolean
        get() = state.useCtrlEnterToSend
        set(value) { state.useCtrlEnterToSend = value }

    var autosave: Boolean
        get() = state.autosave
        set(value) { state.autosave = value }

    var model: String
        get() = state.model
        set(value) { state.model = value }

    var thinkingLevel: String
        get() = state.thinkingLevel
        set(value) { state.thinkingLevel = value }

    var environmentVariables: MutableList<EnvVar>
        get() = state.environmentVariables
        set(value) { state.environmentVariables = value }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
