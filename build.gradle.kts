plugins {
    id("org.jetbrains.intellij.platform") version "2.6.0"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.anthropic.claudecode"
version = "1.0.0-mvp"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        local("C:/Program Files/JetBrains/JetBrains Rider 2022.2.1")
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
                <li>IDE theme adaptation: reads IDE palette and editor font, reloads on theme switch</li>
                <li>Tool window icon states: idle / pending / done</li>
                <li>check_git_status RPC with real branch detection</li>
                <li>set_model / set_permission_mode / set_thinking_level persist to settings</li>
                <li>get_current_selection returns line numbers and language ID</li>
                <li>Settings UI: Browse button for executable path, Test Connection button</li>
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
