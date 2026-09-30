package me.rerere.tts.model

/**
 * Unified playback state (exposed to the app side).
 */
enum class PlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Paused,
    Ended,
    Error
}

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    val currentChunkIndex: Int = 0, // 1-based, aligned with the currentChunk StateFlow
    val totalChunks: Int = 0,
    val errorMessage: String? = null
)


