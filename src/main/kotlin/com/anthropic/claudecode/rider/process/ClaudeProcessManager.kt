package com.anthropic.claudecode.rider.process

import com.anthropic.claudecode.rider.browser.ClaudeBrowserManager
import com.anthropic.claudecode.rider.toolwindow.ClaudeIconManager
import com.anthropic.claudecode.rider.toolwindow.ClaudeToolWindowPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Project-level service managing Claude CLI subprocesses.
 *
 * Each webview "channel" (conversation) maps to one spawned claude process.
 * Messages flow as JSON lines on stdin/stdout following the Claude Code SDK protocol.
 */
@Service(Service.Level.PROJECT)
class ClaudeProcessManager(private val project: Project) : Disposable {

    companion object {
        fun getInstance(project: Project): ClaudeProcessManager = project.service()
    }

    private val log = Logger.getInstance(ClaudeProcessManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Channel(val process: Process, val job: Job)
    private val channels = ConcurrentHashMap<String, Channel>()

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Spawns a new Claude process for [channelId].
     * [resume] is an optional session ID to resume an existing conversation.
     */
    fun launchChannel(
        channelId: String,
        cwd: String,
        resume: String?,
        permissionMode: String,
        thinkingLevel: String
    ) {
        if (channels.containsKey(channelId)) {
            log.warn("Channel $channelId already exists, ignoring launch_claude")
            return
        }

        val binary = ClaudeProcessConfig.resolveBinaryPath()
        if (binary == null) {
            log.warn("Cannot launch channel $channelId: claude binary not found")
            sendErrorToWebview(channelId, "Claude CLI not found. Please install claude and configure the path in Settings → Tools → Claude Code.")
            return
        }

        val args = mutableListOf(
            binary,
            "--output-format", "stream-json",
            "--verbose",
            "--input-format", "stream-json",
            "--permission-prompt-tool", "stdio"
        )
        if (!resume.isNullOrBlank()) args += listOf("--resume", resume)

        try {
            val pb = ProcessBuilder(args)
                .directory(File(cwd.takeIf { File(it).exists() } ?: (project.basePath ?: System.getProperty("user.home"))))
                .redirectErrorStream(false)
            pb.environment().putAll(ClaudeProcessConfig.buildEnvironment())

            val proc = pb.start()
            log.info("Claude channel $channelId started (pid=${proc.pid()}, cwd=$cwd)")

            val job = scope.launch {
                try {
                    // Drain stderr to IDE log (warn level so it's always visible)
                    launch {
                        try {
                            proc.errorStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                                log.warn("[claude/$channelId stderr] $line")
                            }
                        } catch (e: IOException) { /* ignore */ }
                    }

                    // Read stdout lines and forward as io_message to webview
                    proc.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                        if (line.isNotBlank()) {
                            log.debug("[claude/$channelId stdout] $line")
                            forwardIoMessage(channelId, line)
                        }
                    }
                } catch (e: IOException) {
                    log.debug("Channel $channelId stdout ended: ${e.message}")
                } finally {
                    log.info("Claude channel $channelId process exited")
                    channels.remove(channelId)
                    sendCloseChannel(channelId, null)
                    ClaudeIconManager.setDone(project)
                }
            }

            channels[channelId] = Channel(proc, job)

        } catch (e: Exception) {
            log.error("Failed to launch channel $channelId", e)
            sendErrorToWebview(channelId, "Failed to start Claude: ${e.message}")
        }
    }

    /**
     * Sends an io_message.message JSON string to the Claude process on [channelId] stdin.
     * [done] signals that no more input is coming (closes stdin).
     */
    fun sendIoMessage(channelId: String, messageJson: String, done: Boolean) {
        val channel = channels[channelId]
        if (channel == null) {
            log.warn("sendIoMessage: channel $channelId not found")
            return
        }
        try {
            val line = messageJson + "\n"
            channel.process.outputStream.write(line.toByteArray(Charsets.UTF_8))
            channel.process.outputStream.flush()
            if (done) channel.process.outputStream.close()
        } catch (e: IOException) {
            log.warn("Failed to write to channel $channelId stdin", e)
        }
    }

    /** Kills the process for [channelId] and removes it from the map. */
    fun closeChannel(channelId: String) {
        val channel = channels.remove(channelId) ?: return
        channel.job.cancel()
        channel.process.destroy()
        log.info("Channel $channelId closed")
    }

    /** Sends SIGINT / destroyForcibly to interrupt the running Claude operation. */
    fun interruptChannel(channelId: String) {
        channels[channelId]?.process?.let { proc ->
            try {
                // On Unix send SIGINT; on Windows destroyForcibly is the fallback
                Runtime.getRuntime().exec(arrayOf("kill", "-INT", proc.pid().toString()))
            } catch (e: Exception) {
                proc.destroyForcibly()
            }
        }
    }

    // ── Webview forwarding ────────────────────────────────────────────────────

    private fun forwardIoMessage(channelId: String, line: String) {
        val parsedMessage = runCatching { Json.parseToJsonElement(line) }.getOrNull() ?: return
        val inner = buildJsonObject {
            put("type", "io_message")
            put("channelId", channelId)
            put("message", parsedMessage)
            put("done", false)
        }
        sendToWebview(inner.toString())
    }

    private fun sendCloseChannel(channelId: String, error: String?) {
        val inner = buildJsonObject {
            put("type", "close_channel")
            put("channelId", channelId)
            if (error != null) put("error", error) else put("error", JsonNull)
        }
        sendToWebview(inner.toString())
    }

    private fun sendErrorToWebview(channelId: String, message: String) {
        sendCloseChannel(channelId, message)
    }

    private fun sendToWebview(innerJson: String) {
        ApplicationManager.getApplication().invokeLater {
            getBrowserManager()?.sendToWebview(innerJson)
        }
    }

    private fun getBrowserManager(): ClaudeBrowserManager? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Claude") ?: return null
        val panel = toolWindow.contentManager.contents
            .mapNotNull { it.component as? ClaudeToolWindowPanel }
            .firstOrNull() ?: return null
        return panel.browserManager
    }

    // ── Disposable ────────────────────────────────────────────────────────────

    override fun dispose() {
        scope.cancel()
        channels.values.forEach { it.job.cancel(); it.process.destroy() }
        channels.clear()
    }
}
