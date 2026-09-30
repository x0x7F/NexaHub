package me.rerere.ai.provider.stream

import kotlinx.serialization.Serializable

/**
 * A Server-Sent Event independent of any specific HTTP client.
 *
 * [event] maps to the SSE `event` field; the `type` in the provider's response JSON stays inside [data].
 * Trace tests record at this layer, so they can be reused across OkHttp, Ktor or other transports.
 */
@Serializable
data class SseEvent(
    val id: String? = null,
    val event: String? = null,
    val data: String,
    val retryMillis: Long? = null,
)
