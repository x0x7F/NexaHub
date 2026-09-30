package me.ray.nexahub.videogen.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.ray.nexahub.videogen.model.VideoGenerationRequest
import me.ray.nexahub.videogen.model.VideoGenerationTask
import me.ray.nexahub.videogen.provider.providers.AliyunVideoGenerationProvider
import me.ray.nexahub.videogen.provider.providers.MiniMaxVideoGenerationProvider
import me.ray.nexahub.videogen.provider.providers.VolcengineVideoGenerationProvider
import okhttp3.OkHttpClient
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VideoGenerationManager(
    client: OkHttpClient = OkHttpClient(),
) {
    private val aliyun = AliyunVideoGenerationProvider(client)
    private val volcengine = VolcengineVideoGenerationProvider(client)
    private val miniMax = MiniMaxVideoGenerationProvider(client)

    suspend fun create(
        setting: VideoGenerationProviderSetting,
        request: VideoGenerationRequest,
    ): Result<VideoGenerationTask> = provider(setting).createUnsafe(setting, request)

    suspend fun query(
        setting: VideoGenerationProviderSetting,
        taskId: String,
    ): Result<VideoGenerationTask> = provider(setting).queryUnsafe(setting, taskId)

    /**
     * Polls and emits the server states in order. Polling stops immediately when the Flow is cancelled.
     */
    fun watch(
        setting: VideoGenerationProviderSetting,
        taskId: String,
        interval: Duration = 15.seconds,
    ): Flow<VideoGenerationTask> = flow {
        require(interval.isPositive()) { "interval must be positive" }
        while (true) {
            val task = query(setting, taskId).getOrThrow()
            emit(task)
            if (task.isTerminal) return@flow
            delay(interval)
        }
    }

    private fun provider(setting: VideoGenerationProviderSetting): VideoGenerationProvider<*> =
        when (setting) {
            is VideoGenerationProviderSetting.Aliyun -> aliyun
            is VideoGenerationProviderSetting.Volcengine -> volcengine
            is VideoGenerationProviderSetting.MiniMax -> miniMax
        }

    @Suppress("UNCHECKED_CAST")
    private suspend fun VideoGenerationProvider<*>.createUnsafe(
        setting: VideoGenerationProviderSetting,
        request: VideoGenerationRequest,
    ): Result<VideoGenerationTask> =
        (this as VideoGenerationProvider<VideoGenerationProviderSetting>).create(setting, request)

    @Suppress("UNCHECKED_CAST")
    private suspend fun VideoGenerationProvider<*>.queryUnsafe(
        setting: VideoGenerationProviderSetting,
        taskId: String,
    ): Result<VideoGenerationTask> =
        (this as VideoGenerationProvider<VideoGenerationProviderSetting>).query(setting, taskId)
}
