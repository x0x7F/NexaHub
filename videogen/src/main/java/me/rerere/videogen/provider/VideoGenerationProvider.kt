package me.rerere.videogen.provider

import me.rerere.videogen.model.VideoGenerationRequest
import me.rerere.videogen.model.VideoGenerationTask

/**
 * Video generation vendors only submit and query async tasks; they never poll implicitly inside the network layer.
 */
interface VideoGenerationProvider<S : VideoGenerationProviderSetting> {
    val id: String

    suspend fun create(
        setting: S,
        request: VideoGenerationRequest,
    ): Result<VideoGenerationTask>

    suspend fun query(
        setting: S,
        taskId: String,
    ): Result<VideoGenerationTask>
}
