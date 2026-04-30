plugins {
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.anthropic.claudecode"
version = "1.0.15"

kotlin {
    jvmToolchain(17)
}

// Set RIDER_SDK_PATH env var to use a local Rider installation for faster builds,
// e.g.: RIDER_SDK_PATH="C:/Program Files/JetBrains/JetBrains Rider 2025.1"
val localRiderPath: String? = System.getenv("RIDER_SDK_PATH")
    ?: "C:/Program Files/JetBrains/JetBrains Rider 2022.2.1".takeIf {
        File(it).exists()
    }

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (localRiderPath != null) {
            local(localRiderPath)
        } else {
            rider("2025.1")
        }
        pluginVerifier()
        zipSigner()
    }

    // JSON serialization for message protocol
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.anthropic.claudecode.rider"
        name = "Claude Code"
        version = project.version.toString()
        description = providers.provider { file("src/main/resources/META-INF/plugin.xml").readText()
            .substringAfter("<description><![CDATA[").substringBefore("]]></description>").trimIndent() }
        changeNotes = """
            <ul>
                <li>Fix: clicking links in chat now opens the system default browser instead of spawning a new embedded Chromium (cef_server.exe) process — intercept <code>onBeforePopup</code> in the JCEF life-span handler and delegate to <code>Desktop.browse()</code></li>
                <li>Fix: Write/Edit/Read/Glob tools now pre-approved via <code>--allowedTools</code> — Claude 2.1.x checks allowedTools before sending control_request, so without this flag Write was silently denied before our auto-allow code could run</li>
                <li>Fix: Bash tool permission dialog now works — <code>pendingPermissions</code> map was never populated, so webview responses were discarded and Claude never received the control_response</li>
                <li>Fix: "Web" sessions tab hidden in session list — remote sessions are not supported in Rider</li>
                <li>Fix: Chinese (CJK) text no longer appears as garbled characters in session list and session messages — <code>atob()</code> returns a binary string, not Unicode; added <code>TextDecoder('utf-8')</code> to correctly decode multi-byte UTF-8 before JSON.parse</li>
                <li>Fix: session list now correctly finds sessions for projects with paths containing backslashes, colons, or underscores (e.g. <code>F:\GitHub\my_project</code>) — matches the Claude CLI's encoding rule of replacing every non-alphanumeric character with a hyphen</li>
                <li>Fix: Tool permission dialog (Allow/Deny) now renders correctly — webview <code>isVisible</code> is now set to true on load, which the React app requires to show the interactive permission UI</li>
                <li>Fix: Bash/tool call blocks now display correctly — added <code>--include-partial-messages</code> so Claude streams incremental events the webview needs to render tool use in real time</li>
                <li>Fix: <code>sendToWebview</code> now uses base64 encoding to safely transmit any JSON (previously backtick template literals could break on <code>${'$'}{...}</code> patterns in tool output)</li>
                <li>Fix: <code>--permission-mode</code> is now forwarded to the Claude CLI from the webview setting</li>
                <li>IDE theme adaptation: reads IDE palette and editor font, reloads on theme switch</li>
                <li>Tool window icon states: idle / pending / done</li>
                <li>Tool window title updates with session name via rename_tab</li>
                <li>Session list shows current git branch, limited to 10 most recent sessions</li>
                <li>check_git_status RPC with real branch detection</li>
                <li>set_model / set_permission_mode / set_thinking_level persist to settings</li>
                <li>get_current_selection returns line numbers and language ID</li>
                <li>Settings UI: Browse button for executable path, Test Connection button</li>
                <li>View help docs opens GitHub repository</li>
                <li>General config / Switch account / /remote-control hidden (N/A in Rider)</li>
                <li>Fixed MCP servers and Plugins page crashes</li>
            </ul>
        """
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    instrumentCode = false
}
