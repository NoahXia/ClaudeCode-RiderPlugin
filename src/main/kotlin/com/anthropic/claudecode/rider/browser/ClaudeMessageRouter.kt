package com.anthropic.claudecode.rider.browser

import com.anthropic.claudecode.rider.process.ClaudeProcessManager
import com.anthropic.claudecode.rider.settings.ClaudeSettings
import com.anthropic.claudecode.rider.toolwindow.ClaudeIconManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.SystemInfo
import kotlinx.serialization.json.*
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Handles all JavaScript -> Kotlin IPC messages from the JCEF webview.
 *
 * The webview uses a bidirectional RPC protocol:
 *  - Top-level types: "request" (RPC call), "launch_claude", "io_message", "close_channel", "interrupt_claude"
 *  - RPC request envelope: { type, requestId, channelId, request: { type, ...fields } }
 *  - Messages TO the webview are wrapped in { type: "from-extension", message: <inner> } by ClaudeBrowserManager
 */
class ClaudeMessageRouter(
    private val project: Project,
    private val browserManager: ClaudeBrowserManager
) : CefMessageRouterHandlerAdapter() {

    private val log = Logger.getInstance(ClaudeMessageRouter::class.java)
    private val deletedSessionIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: CefQueryCallback?
    ): Boolean {
        if (request == null) return false

        try {
            val json = Json.parseToJsonElement(request).jsonObject
            val type = json["type"]?.jsonPrimitive?.content ?: ""

            when (type) {
                "request"         -> handleRpcRequest(json)
                "launch_claude"   -> handleLaunchClaude(json)
                "io_message"      -> handleIoMessage(json)
                "close_channel"   -> handleCloseChannel(json)
                "interrupt_claude"-> handleInterruptClaude(json)
                else              -> log.debug("Unhandled top-level message type: $type")
            }

            callback?.success("")
        } catch (e: Exception) {
            log.warn("Error processing message from webview: ${e.message}", e)
            callback?.failure(500, e.message ?: "Internal error")
        }

        return true
    }

    // ── RPC dispatcher ──────────────────────────────────────────────────────

    private fun handleRpcRequest(envelope: JsonObject) {
        val requestId = envelope["requestId"]?.jsonPrimitive?.content ?: ""
        val req       = envelope["request"]?.jsonObject ?: return
        val reqType   = req["type"]?.jsonPrimitive?.content ?: ""

        log.debug("RPC request type=$reqType requestId=$requestId")

        when (reqType) {
            "init"                    -> handleInit(requestId)
            "get_claude_state"        -> sendResponse(requestId, buildJsonObject {
                    put("type", "get_claude_state_response")
                    put("config", buildJsonObject {
                        put("claudeSettings", buildJsonObject {
                            put("effective", buildJsonObject {
                                put("permissions", buildJsonObject {})
                                put("model", JsonNull)
                            })
                            put("applied", buildJsonObject {})
                            put("errors", JsonArray(emptyList()))
                        })
                        put("settings", buildJsonObject {})
                        put("models", buildModelsArray())
                    })
                })
            "get_auth_status"         -> handleGetAuthStatus(requestId)
            "login"                   -> sendResponse(requestId, buildJsonObject {
                put("type", "login_response")
                put("auth", buildJsonObject {
                    put("authMethod", "claudeai")
                    put("email", JsonNull)
                    put("subscriptionType", JsonNull)
                })
            })
            "list_sessions_request"   -> handleListSessions(requestId)
            "get_session_request"     -> handleGetSession(requestId, req)
            "new_conversation_tab"    -> handleNewConversationTab(requestId, req)
            "get_current_selection"   -> handleGetCurrentSelection(requestId)
            "open_file"               -> handleOpenFile(requestId, req)
            "open_url"                -> handleOpenUrl(requestId, req)
            "show_notification"       -> handleShowNotification(requestId, req)
            "log_event"               -> sendResponse(requestId, buildJsonObject { put("type", "log_event_response") })
            "rename_tab"              -> handleRenameTab(requestId, req)
            "update_session_state"    -> sendResponse(requestId, buildJsonObject { put("type", "update_session_state_response") })
            "delete_session"          -> handleDeleteSession(requestId, req)
            "rename_session"          -> handleRenameSession(requestId, req)
            "fork_conversation"       -> sendResponse(requestId, buildJsonObject { put("type", "fork_conversation_response") })
            "message_rated"           -> sendResponse(requestId, buildJsonObject { put("type", "message_rated_response") })
            "set_permission_mode"     -> handleSetPermissionMode(requestId, req)
            "set_model"               -> handleSetModel(requestId, req)
            "set_thinking_level"      -> handleSetThinkingLevel(requestId, req)
            "apply_settings"          -> sendResponse(requestId, buildJsonObject { put("type", "apply_settings_response") })
            "get_asset_uris"          -> sendResponse(requestId, buildJsonObject { put("type", "asset_uris_response"); put("uris", buildJsonObject {}) })
            "request_usage_update"    -> sendResponse(requestId, buildJsonObject { put("type", "request_usage_update_response") })
            "dismiss_onboarding"      -> sendResponse(requestId, buildJsonObject { put("type", "dismiss_onboarding_response") })
            "dismiss_terminal_banner" -> sendResponse(requestId, buildJsonObject { put("type", "dismiss_terminal_banner_response") })
            "dismiss_review_upsell_banner" -> sendResponse(requestId, buildJsonObject { put("type", "dismiss_review_upsell_banner_response") })
            "list_plugins"            -> sendResponse(requestId, buildJsonObject {
                put("type", "list_plugins_response")
                put("installed", JsonArray(emptyList()))
                put("available", JsonArray(emptyList()))
            })
            "list_marketplaces"       -> sendResponse(requestId, buildJsonObject {
                put("type", "list_marketplaces_response")
                put("marketplaces", JsonArray(emptyList()))
            })
            "get_mcp_servers"         -> sendResponse(requestId, buildJsonObject {
                put("type", "get_mcp_servers_response")
                put("mcpServers", JsonArray(emptyList()))
            })
            "get_context_usage"       -> sendResponse(requestId, buildJsonObject { put("type", "get_context_usage_response") })
            "check_git_status"        -> handleCheckGitStatus(requestId, req)
            "list_files_request"      -> handleListFiles(requestId, req)
            "generate_session_title"  -> sendResponse(requestId, buildJsonObject { put("type", "generate_session_title_response"); put("title", JsonNull) })
            "list_remote_sessions"    -> sendResponse(requestId, buildJsonObject { put("type", "list_remote_sessions_response"); put("sessions", JsonArray(emptyList())) })
            "open_folder"             -> sendResponse(requestId, buildJsonObject { put("type", "open_folder_response") })
            "open_config"             -> handleOpenConfig(requestId)
            "open_help"               -> handleOpenHelp(requestId)
            "open_output_panel"       -> handleOpenOutputPanel(requestId)
            else -> {
                log.debug("Unhandled RPC request type: $reqType — sending stub response")
                sendResponse(requestId, buildJsonObject { put("type", "${reqType}_response") })
            }
        }
    }

    // ── init ────────────────────────────────────────────────────────────────

    private fun handleInit(requestId: String) {
        val cwd = project.basePath ?: System.getProperty("user.home", "")
        val settings = ClaudeSettings.getInstance()
        val platform = when {
            SystemInfo.isWindows -> "windows"
            SystemInfo.isMac     -> "mac"
            else                 -> "linux"
        }

        val response = buildJsonObject {
            put("type", "init_response")
            put("state", buildJsonObject {
                put("defaultCwd", cwd)
                put("openNewInTab", false)
                put("showTerminalBanner", false)
                put("showReviewUpsellBanner", false)
                put("isOnboardingEnabled", false)
                put("isOnboardingDismissed", true)
                put("authStatus", buildJsonObject {
                    put("authMethod", "claudeai")
                    put("email", JsonNull)
                    put("subscriptionType", JsonNull)
                })
                put("modelSetting", settings.model)
                put("thinkingLevel", settings.thinkingLevel)
                put("initialPermissionMode", settings.initialPermissionMode)
                put("allowDangerouslySkipPermissions", false)
                put("platform", platform)
                put("speechToTextEnabled", false)
                put("speechToTextMicDenied", false)
                put("marketplaceType", "none")
                put("useCtrlEnterToSend", settings.useCtrlEnterToSend)
                put("chromeMcpState", buildJsonObject { put("status", "disconnected") })
                put("settings", buildJsonObject {})
                // claudeSettings must have effective.permissions to avoid NPE in the webview
                put("claudeSettings", buildJsonObject {
                    put("effective", buildJsonObject {
                        put("permissions", buildJsonObject {})
                        put("model", JsonNull)
                    })
                    put("applied", buildJsonObject {})
                    put("errors", JsonArray(emptyList()))
                })
                put("experimentGates", buildJsonObject {})
                put("spinnerVerbsConfig", JsonNull)
                put("currentRepo", JsonNull)
            })
        }
        sendResponse(requestId, response)
    }

    // ── auth status ─────────────────────────────────────────────────────────

    private fun handleGetAuthStatus(requestId: String) {
        sendResponse(requestId, buildJsonObject {
            put("type", "get_auth_status_response")
            put("status", buildJsonObject {
                put("authMethod", "claudeai")
                put("email", JsonNull)
                put("subscriptionType", JsonNull)
            })
        })
    }

    // ── sessions ─────────────────────────────────────────────────────────────

    private fun handleListSessions(requestId: String) {
        val sessions = loadSessionsFromDisk()
        sendResponse(requestId, buildJsonObject {
            put("type", "list_sessions_response")
            put("sessions", JsonArray(sessions))
        })
    }

    private fun loadSessionsFromDisk(): List<JsonElement> {
        val cwd = project.basePath ?: return emptyList()
        val gitBranch = readGitBranch(cwd)
        return try {
            val claudeDir = Paths.get(System.getProperty("user.home"), ".claude", "projects")
            if (!Files.exists(claudeDir)) return emptyList()

            val encoded = cwd.replace("\\", "/").trimEnd('/')
            val projectDir = Files.list(claudeDir).use { stream ->
                stream.filter { Files.isDirectory(it) }
                      .filter { dir ->
                          val name = dir.fileName.toString()
                          name == encoded || name == encoded.replace("/", "-") ||
                          name == encoded.replace(":", "") ||
                          name.endsWith(cwd.substringAfterLast('/').substringAfterLast('\\'))
                      }
                      .findFirst()
                      .orElse(null)
            }

            if (projectDir == null) return emptyList()

            Files.list(projectDir).use { stream ->
                stream.filter { it.toString().endsWith(".jsonl") }
                      .filter { !deletedSessionIds.contains(it.fileName.toString().removeSuffix(".jsonl")) }
                      .sorted { a, b ->
                          Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a))
                      }
                      .limit(50)
                      .map { file ->
                          val sessionId = file.fileName.toString().removeSuffix(".jsonl")
                          val lastMod = Files.getLastModifiedTime(file).toMillis()
                          val size = Files.size(file)
                          val summary = readSessionSummary(file)
                          buildJsonObject {
                              put("id", sessionId)
                              put("lastModified", lastMod)
                              put("fileSize", size)
                              put("summary", summary)
                              if (gitBranch != null) put("gitBranch", gitBranch) else put("gitBranch", JsonNull)
                              put("worktree", JsonNull)
                              put("isCurrentWorkspace", true)
                          }
                      }.toList()
            }
        } catch (e: Exception) {
            log.warn("Failed to load sessions from disk", e)
            emptyList()
        }
    }

    private fun readGitBranch(cwd: String): String? {
        return try {
            val proc = ProcessBuilder("git", "-C", cwd, "branch", "--show-current")
                .redirectErrorStream(true)
                .start()
            val branch = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.waitFor()
            branch?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun readSessionSummary(path: java.nio.file.Path): String {
        return try {
            var summaryTitle: String? = null
            var firstUserContent: String? = null
            Files.newBufferedReader(path).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val json = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                    // summary records (written by rename or Claude itself) take priority
                    if (json["type"]?.jsonPrimitive?.contentOrNull == "summary") {
                        summaryTitle = json["summary"]?.jsonPrimitive?.contentOrNull
                        continue
                    }
                    // fall back to first user message content
                    if (firstUserContent == null) {
                        val role = json["role"]?.jsonPrimitive?.contentOrNull
                            ?: json["message"]?.jsonObject?.get("role")?.jsonPrimitive?.contentOrNull
                        if (role == "user") {
                            val content = json["content"]?.jsonPrimitive?.contentOrNull
                                ?: json["message"]?.jsonObject?.get("content")?.let {
                                    when {
                                        it is JsonPrimitive -> it.contentOrNull
                                        it is JsonArray -> it.firstOrNull()
                                            ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                        else -> null
                                    }
                                }
                            if (!content.isNullOrBlank()) firstUserContent = content.take(80)
                        }
                    }
                }
            }
            summaryTitle ?: firstUserContent ?: "Session"
        } catch (e: Exception) {
            "Session"
        }
    }

    private fun handleGetSession(requestId: String, req: JsonObject) {
        ClaudeIconManager.setIdle(project)
        val sessionId = req["sessionId"]?.jsonPrimitive?.contentOrNull
        if (sessionId == null) {
            sendResponse(requestId, buildJsonObject {
                put("type", "error"); put("error", "No sessionId provided")
            })
            return
        }
        val messages = loadSessionMessages(sessionId)
        sendResponse(requestId, buildJsonObject {
            put("type", "get_session_response")
            put("messages", JsonArray(messages))
            put("sessionDiffs", JsonArray(emptyList()))
        })
    }

    private fun loadSessionMessages(sessionId: String): List<JsonElement> {
        val cwd = project.basePath ?: return emptyList()
        return try {
            val claudeDir = Paths.get(System.getProperty("user.home"), ".claude", "projects")
            Files.walk(claudeDir, 2).use { stream ->
                val file = stream.filter { it.fileName.toString() == "$sessionId.jsonl" }.findFirst().orElse(null)
                    ?: return emptyList()
                Files.readAllLines(file).mapNotNull { line ->
                    if (line.isBlank()) null
                    else runCatching { Json.parseToJsonElement(line) }.getOrNull()
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to load session $sessionId", e)
            emptyList()
        }
    }

    // ── session delete / rename ──────────────────────────────────────────────

    private fun handleDeleteSession(requestId: String, req: JsonObject) {
        val sessionId = req["sessionId"]?.jsonPrimitive?.contentOrNull
        if (sessionId == null) {
            sendResponse(requestId, buildJsonObject { put("type", "delete_session_response") })
            return
        }
        try {
            val claudeDir = Paths.get(System.getProperty("user.home"), ".claude", "projects")
            val file = Files.walk(claudeDir, 2).use { stream ->
                stream.filter { it.fileName.toString() == "$sessionId.jsonl" }.findFirst().orElse(null)
            }
            if (file != null) {
                Files.delete(file)
                deletedSessionIds.add(sessionId)
                log.info("Deleted session $sessionId")
            }
        } catch (e: Exception) {
            log.warn("Failed to delete session $sessionId", e)
        }
        sendResponse(requestId, buildJsonObject { put("type", "delete_session_response") })
    }

    private fun handleRenameSession(requestId: String, req: JsonObject) {
        val sessionId = req["sessionId"]?.jsonPrimitive?.contentOrNull
        val title     = req["title"]?.jsonPrimitive?.contentOrNull
        if (sessionId == null || title == null) {
            sendResponse(requestId, buildJsonObject { put("type", "rename_session_response"); put("skipped", true) })
            return
        }
        try {
            val claudeDir = Paths.get(System.getProperty("user.home"), ".claude", "projects")
            val file = Files.walk(claudeDir, 2).use { stream ->
                stream.filter { it.fileName.toString() == "$sessionId.jsonl" }.findFirst().orElse(null)
            }
            if (file != null) {
                // Append a summary record so readSessionSummary picks up the new title
                val summaryLine = buildJsonObject {
                    put("type", "summary")
                    put("summary", title)
                    put("leafUuid", "")
                }.toString()
                Files.write(file, ("\n" + summaryLine + "\n").toByteArray(Charsets.UTF_8),
                    java.nio.file.StandardOpenOption.APPEND)
                log.info("Renamed session $sessionId to '$title'")
                sendResponse(requestId, buildJsonObject { put("type", "rename_session_response"); put("skipped", false) })
                return
            }
        } catch (e: Exception) {
            log.warn("Failed to rename session $sessionId", e)
        }
        sendResponse(requestId, buildJsonObject { put("type", "rename_session_response"); put("skipped", true) })
    }

    // ── new conversation tab ─────────────────────────────────────────────────

    private fun handleNewConversationTab(requestId: String, req: JsonObject) {
        val channelId = java.util.UUID.randomUUID().toString()
        val cwd = req["cwd"]?.jsonPrimitive?.contentOrNull ?: project.basePath ?: System.getProperty("user.home")
        val initialPrompt = req["initialPrompt"]?.jsonPrimitive?.contentOrNull
        val sessionId = req["sessionId"]?.jsonPrimitive?.contentOrNull

        val processManager = ClaudeProcessManager.getInstance(project)
        processManager.launchChannel(channelId, cwd, sessionId, "default", "none")

        if (!initialPrompt.isNullOrBlank()) {
            val userMsg = buildJsonObject {
                put("type", "user")
                put("session_id", "")
                put("parent_tool_use_id", JsonNull)
                put("message", buildJsonObject {
                    put("role", "user")
                    put("content", initialPrompt)
                })
            }
            processManager.sendIoMessage(channelId, userMsg.toString(), done = false)
        }

        sendResponse(requestId, buildJsonObject {
            put("type", "new_conversation_tab_response")
            put("channelId", channelId)
        })
    }

    // ── Claude process channel management ────────────────────────────────────

    private fun handleLaunchClaude(json: JsonObject) {
        val channelId     = json["channelId"]?.jsonPrimitive?.contentOrNull ?: return
        val cwd           = json["cwd"]?.jsonPrimitive?.contentOrNull ?: project.basePath ?: System.getProperty("user.home")
        val resume        = json["resume"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        val permMode      = json["permissionMode"]?.jsonPrimitive?.contentOrNull ?: "default"
        val thinkingLevel = json["thinkingLevel"]?.jsonPrimitive?.contentOrNull ?: "none"

        log.info("launch_claude channelId=$channelId cwd=$cwd resume=$resume permMode=$permMode")
        ClaudeIconManager.setPending(project)
        ClaudeProcessManager.getInstance(project)
            .launchChannel(channelId, cwd, resume, permMode, thinkingLevel)
    }

    private fun handleIoMessage(json: JsonObject) {
        val channelId = json["channelId"]?.jsonPrimitive?.contentOrNull ?: return
        val message   = json["message"]?.jsonObject ?: return
        val done      = json["done"]?.jsonPrimitive?.booleanOrNull ?: false

        if (message["type"]?.jsonPrimitive?.contentOrNull == "user") {
            ClaudeProcessManager.getInstance(project)
                .sendIoMessage(channelId, message.toString(), done)
        }
    }

    private fun handleCloseChannel(json: JsonObject) {
        val channelId = json["channelId"]?.jsonPrimitive?.contentOrNull ?: return
        log.info("close_channel $channelId (requested by webview)")
        ClaudeProcessManager.getInstance(project).closeChannel(channelId)
        ClaudeIconManager.setDone(project)
    }

    private fun handleInterruptClaude(json: JsonObject) {
        val channelId = json["channelId"]?.jsonPrimitive?.contentOrNull ?: return
        log.info("interrupt_claude $channelId")
        ClaudeProcessManager.getInstance(project).interruptChannel(channelId)
    }

    // ── IDE integration ──────────────────────────────────────────────────────

    private fun handleGetCurrentSelection(requestId: String) {
        ApplicationManager.getApplication().runReadAction {
            val editorManager = FileEditorManager.getInstance(project)
            val editor = editorManager.selectedTextEditor
            val vFile = editorManager.selectedFiles.firstOrNull()
            val selection = editor?.selectionModel?.selectedText ?: ""
            val filePath  = vFile?.path ?: ""
            val startLine = editor?.selectionModel?.selectionStartPosition?.line ?: 0
            val endLine   = editor?.selectionModel?.selectionEndPosition?.line ?: 0
            val languageId = vFile?.let {
                FileTypeManager.getInstance().getFileTypeByFile(it).name.lowercase()
            } ?: ""
            sendResponse(requestId, buildJsonObject {
                put("type", "get_current_selection_response")
                put("selectedText", selection)
                put("filePath", filePath)
                put("startLine", startLine)
                put("endLine", endLine)
                put("languageId", languageId)
            })
        }
    }

    private fun handleOpenFile(requestId: String, req: JsonObject) {
        val filePath = req["filePath"]?.jsonPrimitive?.contentOrNull ?: run {
            sendResponse(requestId, buildJsonObject { put("type", "open_file_response") }); return
        }
        val line = req["location"]?.jsonObject?.get("line")?.jsonPrimitive?.intOrNull ?: 0
        val col  = req["location"]?.jsonObject?.get("column")?.jsonPrimitive?.intOrNull ?: 0
        ApplicationManager.getApplication().invokeLater {
            val vFile = LocalFileSystem.getInstance().findFileByPath(filePath)
            if (vFile != null) OpenFileDescriptor(project, vFile, line, col).navigate(true)
        }
        sendResponse(requestId, buildJsonObject { put("type", "open_file_response") })
    }

    private fun handleOpenUrl(requestId: String, req: JsonObject) {
        val url = req["url"]?.jsonPrimitive?.contentOrNull ?: run {
            sendResponse(requestId, buildJsonObject { put("type", "open_url_response") }); return
        }
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
        } catch (e: Exception) {
            log.warn("Failed to open URL: $url", e)
        }
        sendResponse(requestId, buildJsonObject { put("type", "open_url_response") })
    }

    private fun handleOpenHelp(requestId: String) {
        try {
            if (Desktop.isDesktopSupported())
                Desktop.getDesktop().browse(URI("https://github.com/NoahXia/ClaudeCode-RiderPlugin"))
        } catch (e: Exception) {
            log.warn("Failed to open help URL", e)
        }
        sendResponse(requestId, buildJsonObject { put("type", "open_help_response") })
    }

    private fun handleRenameTab(requestId: String, req: JsonObject) {
        val title = req["title"]?.jsonPrimitive?.contentOrNull
        if (!title.isNullOrBlank()) {
            ApplicationManager.getApplication().invokeLater {
                com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                    .getToolWindow("Claude")
                    ?.setTitle(title)
            }
        }
        sendResponse(requestId, buildJsonObject { put("type", "rename_tab_response") })
    }

    private fun handleOpenConfig(requestId: String) {
        ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                .showSettingsDialog(project, "com.anthropic.claudecode.rider.settings")
        }
        sendResponse(requestId, buildJsonObject { put("type", "open_config_response") })
    }

    private fun handleOpenOutputPanel(requestId: String) {
        ApplicationManager.getApplication().invokeLater {
            val twm = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
            // Try common tool window IDs for the IDE's log/event panel
            for (id in listOf("Event Log", "Problems View", "Build")) {
                val tw = twm.getToolWindow(id)
                if (tw != null) { tw.show(); break }
            }
        }
        sendResponse(requestId, buildJsonObject { put("type", "open_output_panel_response") })
    }

    private fun handleShowNotification(requestId: String, req: JsonObject) {
        val message  = req["message"]?.jsonPrimitive?.contentOrNull ?: ""
        val severity = req["severity"]?.jsonPrimitive?.contentOrNull ?: "info"
        val notifType = when (severity.lowercase()) {
            "error"   -> NotificationType.ERROR
            "warning" -> NotificationType.WARNING
            else      -> NotificationType.INFORMATION
        }
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Claude Code")
                ?.createNotification(message, notifType)
                ?.notify(project)
        }
        sendResponse(requestId, buildJsonObject { put("type", "show_notification_response") })
    }

    private fun handleListFiles(requestId: String, req: JsonObject) {
        val pattern = req["pattern"]?.jsonPrimitive?.contentOrNull ?: ""
        val cwd = project.basePath ?: System.getProperty("user.home")
        val root = Paths.get(cwd)

        val ignored = setOf(
            ".git", ".idea", "build", "out", "node_modules", ".gradle",
            ".intellijPlatform", "__pycache__", ".DS_Store", "dist", "target"
        )

        val results = mutableListOf<JsonElement>()
        try {
            Files.walk(root, 8)
                .filter { path ->
                    // Skip ignored directories at any level
                    path.none { part -> part.fileName?.toString() in ignored }
                }
                .filter { path ->
                    if (pattern.isBlank()) true
                    else {
                        val rel = root.relativize(path).toString().replace('\\', '/')
                        val name = path.fileName?.toString() ?: ""
                        name.contains(pattern, ignoreCase = true) ||
                        rel.contains(pattern, ignoreCase = true)
                    }
                }
                .filter { it != root }
                .sorted()
                .limit(100)
                .forEach { path ->
                    val rel = root.relativize(path).toString().replace('\\', '/')
                    val name = path.fileName?.toString() ?: rel
                    val type = if (Files.isDirectory(path)) "directory" else "file"
                    results += buildJsonObject {
                        put("path", rel)
                        put("name", name)
                        put("type", type)
                    }
                }
        } catch (e: Exception) {
            log.warn("list_files_request failed: ${e.message}")
        }

        sendResponse(requestId, buildJsonObject {
            put("type", "list_files_response")
            put("files", JsonArray(results))
        })
    }

    // u2500u2500 settings u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500u2500

    private fun handleSetPermissionMode(requestId: String, req: JsonObject) {
        val mode = req["permissionMode"]?.jsonPrimitive?.contentOrNull ?: return
        ClaudeSettings.getInstance().initialPermissionMode = mode
        sendResponse(requestId, buildJsonObject { put("type", "set_permission_mode_response"); put("success", true) })
    }

    private fun handleSetModel(requestId: String, req: JsonObject) {
        val model = req["model"]?.jsonPrimitive?.contentOrNull ?: return
        ClaudeSettings.getInstance().model = model
        sendResponse(requestId, buildJsonObject { put("type", "set_model_response") })
    }

    private fun handleSetThinkingLevel(requestId: String, req: JsonObject) {
        val level = req["thinkingLevel"]?.jsonPrimitive?.contentOrNull ?: return
        ClaudeSettings.getInstance().thinkingLevel = level
        sendResponse(requestId, buildJsonObject { put("type", "set_thinking_level_response") })
    }

    private fun handleCheckGitStatus(requestId: String, req: JsonObject) {
        val cwd = req["cwd"]?.jsonPrimitive?.contentOrNull ?: project.basePath ?: ""
        try {
            val proc = ProcessBuilder("git", "-C", cwd, "branch", "--show-current")
                .redirectErrorStream(true)
                .start()
            val branch = proc.inputStream.bufferedReader().readLine()?.trim() ?: ""
            proc.waitFor()
            sendResponse(requestId, buildJsonObject {
                put("type", "check_git_status_response")
                put("isGitRepo", branch.isNotEmpty())
                if (branch.isNotEmpty()) put("branch", branch) else put("branch", JsonNull)
                put("hasUncommittedChanges", false)
            })
        } catch (e: Exception) {
            log.debug("check_git_status: git not found or error: ${e.message}")
            sendResponse(requestId, buildJsonObject {
                put("type", "check_git_status_response")
                put("isGitRepo", false)
                put("branch", JsonNull)
                put("hasUncommittedChanges", false)
            })
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun sendResponse(requestId: String, response: JsonObject) {
        val message = buildJsonObject {
            put("type", "response")
            put("requestId", requestId)
            put("response", response)
        }
        browserManager.sendToWebview(message.toString())
    }

    private fun buildModelsArray(): JsonArray {
        data class Model(
            val value: String,
            val label: String,
            val description: String,
            val supportsEffort: Boolean = false,
            val supportsAutoMode: Boolean = false,
            val supportsFastMode: Boolean = false
        )
        val models = listOf(
            Model(
                value = "default",
                label = "Default (recommended)",
                description = "Use the default model (currently Sonnet 4.6) · $3/$15 per Mtok"
            ),
            Model(
                value = "claude-sonnet-4-6-20251001",
                label = "Sonnet (1M context)",
                description = "Sonnet 4.6 for long sessions · $3/$15 per Mtok"
            ),
            Model(
                value = "claude-opus-4-7-20250514",
                label = "Opus 4.7 (1M context)",
                description = "Opus 4.7 with 1M context · Most capable for complex work · $5/$25 per Mtok",
                supportsEffort = true
            ),
            Model(
                value = "claude-haiku-4-5-20251001",
                label = "Haiku",
                description = "Haiku 4.5 · Fastest for quick answers · $1/$5 per Mtok"
            ),
            Model(
                value = "claude-sonnet-4-6",
                label = "Sonnet 4.6",
                description = "claude-sonnet-4-6"
            )
        )
        return JsonArray(models.map { m ->
            buildJsonObject {
                put("value", m.value)
                put("label", m.label)
                put("description", m.description)
                put("supportsEffort", m.supportsEffort)
                put("supportsAutoMode", m.supportsAutoMode)
                put("supportsFastMode", m.supportsFastMode)
                if (m.supportsEffort) put("supportedEffortLevels", JsonArray(listOf(
                    JsonPrimitive("low"), JsonPrimitive("medium"), JsonPrimitive("high")
                )))
            }
        })
    }
}
