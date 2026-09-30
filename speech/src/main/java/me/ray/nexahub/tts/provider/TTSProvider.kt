package me.ray.nexahub.tts.provider

import android.content.Context
import kotlinx.coroutines.flow.Flow
import me.ray.nexahub.tts.model.AudioChunk
import me.ray.nexahub.tts.model.TTSRequest

interface TTSProvider<T : TTSProviderSetting> {
    fun generateSpeech(
        context: Context,
        providerSetting: T,
        request: TTSRequest
    ): Flow<AudioChunk>

    /**
     * Optional: a prompt guiding the AI on how to add the tone/emotion tags this provider supports to the spoken text.
     *
     * Empty by default = nothing injected. Providers that support inline tags override this directly in the implementation (hard-coded).
     * This content is appended to the system prompt when the text_to_speech tool is enabled.
     *
     * Note (two constraints when writing it):
     * 1. Ask the AI to put tags only in the text parameter of the text_to_speech tool, not in the body shown to the user;
     * 2. Tags must survive the cleanup pipeline: avoid symbols such as `*` `_` that stripMarkdown removes,
     *    and don't put punctuation that TextChunker splits on (such as `。，！？…`) inside tags. Prefer square/angle brackets.
     */
    val promptGuidance: String
        get() = ""
}
