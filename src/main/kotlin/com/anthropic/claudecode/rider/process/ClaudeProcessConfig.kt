package com.anthropic.claudecode.rider.process

import com.anthropic.claudecode.rider.settings.ClaudeSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.EnvironmentUtil
import com.intellij.util.SystemProperties
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves the Claude CLI binary path and assembles the environment variables
 * required to run it in VS Code IDE integration mode.
 */
object ClaudeProcessConfig {

    private val log = Logger.getInstance(ClaudeProcessConfig::class.java)

    private val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
    private val isMac = System.getProperty("os.name", "").contains("Mac", ignoreCase = true)

    /**
     * Resolves the claude binary path using the following priority:
     * 1. User-configured path from ClaudeSettings
     * 2. `claude` / `claude.exe` found on the system PATH
     * 3. Binary bundled in the VS Code extension directory (fallback)
     */
    fun resolveBinaryPath(): String? {
        val settings = ClaudeSettings.getInstance()

        // 1. User-configured path
        val configured = settings.claudeExecutablePath.trim()
        if (configured.isNotEmpty()) {
            val f = File(configured)
            if (f.exists() && f.canExecute()) {
                log.info("Using user-configured Claude binary: $configured")
                return configured
            }
            log.warn("Configured Claude path does not exist or is not executable: $configured")
        }

        // 2. System PATH — look for native binary first, then npm .cmd shim
        val candidates = if (isWindows) listOf("claude.exe", "claude.cmd") else listOf("claude")
        for (exeName in candidates) {
            val fromPath = findOnPath(exeName)
            if (fromPath != null) {
                log.info("Found Claude on PATH: $fromPath")
                return fromPath
            }
        }

        // 3. npm global bin directory (may not be on PATH)
        if (isWindows) {
            val npmDir = System.getenv("APPDATA")?.let { File(it, "npm") }
            if (npmDir != null && npmDir.exists()) {
                for (name in listOf("claude.exe", "claude.cmd")) {
                    val f = File(npmDir, name)
                    if (f.exists()) {
                        log.info("Found Claude in npm global bin: ${f.absolutePath}")
                        return f.absolutePath
                    }
                }
            }
        }

        // 4. VS Code extension bundled binary (Windows only)
        if (isWindows) {
            val vscodeBinary = findVsCodeExtensionBinary()
            if (vscodeBinary != null) {
                log.info("Using VS Code extension Claude binary: $vscodeBinary")
                return vscodeBinary
            }
        }

        log.warn("Claude binary not found")
        return null
    }

    private fun findOnPath(exeName: String): String? {
        val pathEnv = System.getenv("PATH") ?: return null
        val separator = if (isWindows) ";" else ":"
        return pathEnv.split(separator)
            .map { File(it, exeName) }
            .firstOrNull { it.exists() && it.canExecute() }
            ?.absolutePath
    }

    private fun findVsCodeExtensionBinary(): String? {
        val home = SystemProperties.getUserHome()
        val extensionsDir = File(home, ".vscode/extensions")
        if (!extensionsDir.exists()) return null

        // Find any claude-code extension directory
        val extDirs = extensionsDir.listFiles { f ->
            f.isDirectory && f.name.startsWith("anthropic.claude-code")
        } ?: return null

        // Prefer architecture-specific binary, fall back to generic
        val arch = if (System.getProperty("os.arch", "").contains("64")) "x64" else "x86"
        val platform = "win32"

        for (extDir in extDirs.sortedByDescending { it.name }) {
            val archSpecific = File(extDir, "resources/native-binaries/$platform-$arch/claude.exe")
            if (archSpecific.exists()) return archSpecific.absolutePath

            val generic = File(extDir, "resources/native-binary/claude.exe")
            if (generic.exists()) return generic.absolutePath
        }
        return null
    }

    /**
     * Builds the environment variable map for the Claude subprocess.
     * Merges the current process environment with the required IDE integration vars.
     */
    fun buildEnvironment(): Map<String, String> {
        val env = EnvironmentUtil.getEnvironmentMap().toMutableMap()

        // Required for VS Code IPC mode in the Claude binary
        env["CLAUDE_CODE_ENTRYPOINT"] = "claude-vscode"
        env["MCP_CONNECTION_NONBLOCKING"] = "true"

        // Merge user-configured environment variables
        val settings = ClaudeSettings.getInstance()
        for (v in settings.environmentVariables) {
            if (v.name.isNotBlank()) {
                env[v.name] = v.value
            }
        }

        return env
    }

    /**
     * On Windows, Claude CLI requires Git Bash for some shell operations.
     * Returns the path to bash.exe if found, or null.
     */
    fun findGitBash(): String? {
        if (!isWindows) return null
        val candidates = listOf(
            "C:/Program Files/Git/bin/bash.exe",
            "C:/Program Files (x86)/Git/bin/bash.exe",
            System.getenv("ProgramFiles")?.let { "$it/Git/bin/bash.exe" },
            System.getenv("LOCALAPPDATA")?.let { "$it/Programs/Git/bin/bash.exe" }
        )
        return candidates.filterNotNull()
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?.absolutePath
    }
}
