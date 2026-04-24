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
        description = "Claude Code AI coding assistant for JetBrains Rider"
        changeNotes = "Initial MVP release"
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    instrumentCode = false
}
