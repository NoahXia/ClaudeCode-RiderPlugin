plugins {
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.anthropic.claudecode"
version = "1.0.6"

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
                <li>Fix: Tool permission dialog (Allow/Deny) now renders correctly — webview <code>isVisible</code> is now set to true on load, which the React app requires to show the interactive permission UI</li>
                <li>Fix: Write/Edit tool permission dialog no longer causes silent failure — strip large file content from <code>tool_permission_request</code> inputs to prevent JCEF script-size overflow</li>
                <li>Fix: Bash/tool call blocks now display correctly — added <code>--include-partial-messages</code> so Claude streams incremental events the webview needs to render tool use in real time</li>
                <li>Fix: <code>sendToWebview</code> now uses base64 encoding to safely transmit any JSON (previously backtick template literals could break on <code>${'$'}{...}</code> patterns in tool output)</li>
                <li>Fix: <code>--permission-mode</code> is now forwarded to the Claude CLI from the webview setting</li>
                <li>IDE theme adaptation: reads IDE palette and editor font, reloads on theme switch</li>
                <li>Tool window icon states: idle / pending / done</li>
                <li>Tool window title updates with session name via rename_tab</li>
                <li>Session list shows current git branch</li>
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
