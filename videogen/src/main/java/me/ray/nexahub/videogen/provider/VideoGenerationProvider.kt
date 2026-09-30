package me.ray.nexahub.videogen.provider

import me.ray.nexahub.videogen.model.VideoGenerationRequest
import me.ray.nexahub.videogen.model.VideoGenerationTask

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
