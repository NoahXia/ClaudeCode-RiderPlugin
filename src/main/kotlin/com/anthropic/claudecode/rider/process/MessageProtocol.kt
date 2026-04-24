package com.anthropic.claudecode.rider.process

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Serializable types for the JSON messages exchanged between the Rider plugin
 * and the Claude CLI subprocess.
 *
 * The protocol mirrors the VS Code extension's IPC format:
 *  - Kotlin -> webview: { type: "from-extension", message: <payload> }
 *  - Webview -> Kotlin: <raw message object with type field>
 */

@Serializable
data class IncomingMessage(
    val type: String,
    val message: JsonElement? = null
)

@Serializable
data class OutgoingEnvelope(
    val type: String = "from-extension",
    val message: JsonObject
)

@Serializable
data class EnvVar(
    var name: String = "",
    var value: String = ""
)
