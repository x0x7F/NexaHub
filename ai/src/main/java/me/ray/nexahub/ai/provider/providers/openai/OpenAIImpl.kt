package me.ray.nexahub.ai.provider.providers.openai

import kotlinx.coroutines.flow.Flow
import me.ray.nexahub.ai.provider.ProviderSetting
import me.ray.nexahub.ai.provider.TextGenerationResult
import me.ray.nexahub.ai.provider.TextGenerationParams
import me.ray.nexahub.ai.ui.StreamChunk
import me.ray.nexahub.ai.ui.UIMessage

interface OpenAIImpl {
    suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): TextGenerationResult

    suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams,
    ): Flow<StreamChunk>
}
