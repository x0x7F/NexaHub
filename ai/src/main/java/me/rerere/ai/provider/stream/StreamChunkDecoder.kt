package me.rerere.ai.provider.stream

import me.rerere.ai.ui.StreamChunk

/**
 * A stateful, transport-independent decoder used for one provider response stream.
 * Each response stream must create its own instance.
 */
interface StreamChunkDecoder {
    /** Convert a single raw SSE event into generic stream events. Throws directly on parse failure. */
    fun accept(event: SseEvent): DecodeResult

    /** Finalize when the SSE closes normally. Implementations must make Finish from this method and explicit end events idempotent. */
    fun onClosed(): List<StreamChunk>
}

data class DecodeResult(
    val chunks: List<StreamChunk> = emptyList(),
    /** The provider protocol has explicitly ended; the transport may close the connection. */
    val completed: Boolean = false,
)
