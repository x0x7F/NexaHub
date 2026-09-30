package me.ray.nexahub.ai.ui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import me.ray.nexahub.ai.util.json

/**
 * Type-safe schema for [UIMessagePart.metadata]
 *
 * At the serialization layer metadata is still a [JsonObject]; this only provides compile-time types for reads and writes:
 * - Read: `part.metadataAs<ClaudeReasoningMetadata>()?.signature`
 * - Write: `part.metadata = ClaudeReasoningMetadata(signature = ...).toMetadata()`
 *
 * All fields must be nullable and keep the same keys as historical data (use [SerialName] where needed),
 * otherwise metadata persisted in old conversations can't be parsed
 */
sealed interface PartMetadata

/**
 * Metadata of a Claude thinking block; the signature must be sent back
 */
@Serializable
data class ClaudeReasoningMetadata(
    val signature: String? = null,
) : PartMetadata

/**
 * Metadata of an OpenAI Responses API reasoning item; the id and encrypted_content must be sent back
 */
@Serializable
data class OpenAIReasoningMetadata(
    @SerialName("reasoning_id")
    val reasoningId: String? = null,
    @SerialName("encrypted_content")
    val encryptedContent: String? = null,
) : PartMetadata

/**
 * The wire protocol a raw server tool block belongs to, independent of the actual provider brand.
 */
@Serializable
enum class ServerToolProtocol {
    @SerialName("openai_responses")
    OPENAI_RESPONSES,

    @SerialName("anthropic_messages")
    ANTHROPIC_MESSAGES,

    @SerialName("google_generate_content")
    GOOGLE_GENERATE_CONTENT,
}

/**
 * Raw protocol blocks of a provider's server tool.
 *
 * Common fields live on [UIMessagePart.ServerTool] itself; this additionally keeps the provider's raw call/result so that
 * multi-turn requests can send them back as-is. The OpenAI Responses API usually only uses [call]; Claude uses both [call] and [result].
 */
@Serializable
data class ServerToolMetadata(
    @SerialName("server_tool_protocol")
    val protocol: ServerToolProtocol? = null,
    @SerialName("server_tool_call")
    val call: JsonObject? = null,
    @SerialName("server_tool_call_index")
    val callIndex: Int? = null,
    @SerialName("server_tool_result")
    val result: JsonObject? = null,
    @SerialName("server_tool_result_index")
    val resultIndex: Int? = null,
) : PartMetadata

/**
 * Structured reasoning_details from OpenRouter Chat Completions; must be sent back as-is when continuing after tool calls.
 */
@Serializable
data class OpenRouterReasoningMetadata(
    @SerialName("openrouter_reasoning_details")
    val reasoningDetails: JsonArray? = null,
) : PartMetadata

/**
 * thoughtSignature of a Google Gemini part; must be sent back
 */
@Serializable
data class GoogleThoughtMetadata(
    val thoughtSignature: String? = null,
) : PartMetadata

/**
 * Metadata of output parts from file-editing tools (e.g. workspace_edit_file),
 * carrying the unified diff text for the UI's diff view; never sent to the API
 */
@Serializable
data class DiffMetadata(
    val diff: String? = null,
) : PartMetadata

/**
 * Parse metadata into a typed [PartMetadata]; returns null if parsing fails or metadata is null
 *
 * Because the json config has ignoreUnknownKeys, metadata from different providers doesn't interfere
 * (e.g. after switching providers, reasoning metadata written by OpenAI doesn't affect Claude's parsing)
 */
inline fun <reified T : PartMetadata> UIMessagePart.metadataAs(): T? = metadata?.let {
    runCatching { json.decodeFromJsonElement<T>(it) }.getOrNull()
}

/**
 * Encode a typed [PartMetadata] into a metadata [JsonObject]
 *
 * Because the json config has explicitNulls = false, null fields are not written
 */
inline fun <reified T : PartMetadata> T.toMetadata(): JsonObject =
    json.encodeToJsonElement(this).jsonObject
