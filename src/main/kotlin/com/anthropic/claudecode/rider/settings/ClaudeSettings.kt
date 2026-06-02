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
        var environmentVariables: MutableList<EnvVar> = mutableListOf(),
        // Session IDs the user has "deleted" from the history list. Mirrors the official
        // VS Code extension, whose delete button is really a hide: it appends the id to a
        // persisted `hiddenSessionIds` list (VS Code globalState) and never touches the
        // .jsonl on disk. Persisting (vs. an in-memory set) keeps the session hidden across
        // IDE restarts; not deleting the file avoids the race where a resumed
        // `claude --resume` subprocess recreates a just-deleted session. Session IDs are
        // UUIDs, globally unique, so a flat list needs no per-project keying.
        var hiddenSessionIds: MutableList<String> = mutableListOf()
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

    /** True if the user has hidden ("deleted") this session from the history list. */
    @Synchronized
    fun isSessionHidden(sessionId: String): Boolean =
        state.hiddenSessionIds.contains(sessionId)

    /** Hides a session from the history list, persisted so it stays hidden across restarts. */
    @Synchronized
    fun hideSession(sessionId: String) {
        if (!state.hiddenSessionIds.contains(sessionId)) {
            state.hiddenSessionIds.add(sessionId)
        }
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
