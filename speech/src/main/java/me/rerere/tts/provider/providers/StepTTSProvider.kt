package me.rerere.tts.provider.providers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderException
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TAG = "StepTTSProvider"

// The binary format returned by StepFun /v1/audio/speech is determined by responseFormat
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * StepFun Step TTS adapter.
 *
 * Uses the non-streaming [POST {baseUrl}/v1/audio/speech] endpoint: POST the text once, and the server returns the complete audio
 * binary (mp3/wav/pcm/opus/flac). Authentication uses the standard `Authorization: Bearer sk-xxx`.
 *
 * Field names follow the camelCase convention of StepFun's official Android SDK (TtsSpeechRequest):
 * responseFormat / sampleRate / voiceLabel, etc., unlike OpenAI's snake_case; keep this in mind when pointing baseUrl
 * at an OpenAI-compatible proxy.
 *
 * Official docs:
 * - Model overview: https://platform.stepfun.com/docs/guides/models/stepaudio-2.5-tts
 * - Developer guide: https://platform.stepfun.com/docs/guides/developer/tts
 */
class StepTTSProvider : TTSProvider<TTSProviderSetting.Step> {
    private val httpClient = OkHttpClient.Builder()
        // One-shot synthesis can be slow (long text + high-quality model), so allow a generous read timeout
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.Step,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        // Only sent when instruction isn't empty; step-tts-mini / step-tts-vivid don't support the field,
        // but the server ignores unknown fields (responseFormat also allows ignoreUnknownKeys)
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("input", request.text)
            put("voice", providerSetting.voice)
            put("responseFormat", providerSetting.responseFormat)
            put("speed", providerSetting.speed)
            put("volume", providerSetting.volume)
            put("sampleRate", providerSetting.sampleRate)
            if (providerSetting.instruction.isNotBlank()) {
                put("instruction", providerSetting.instruction)
            }
        }

        Log.i(TAG, "generateSpeech: model=${providerSetting.model} voice=${providerSetting.voice} format=${providerSetting.responseFormat}")

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl.trimEnd('/')}/v1/audio/speech")
            .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/octet-stream")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            // Read the error response body to help debugging (4xx usually returns a JSON error)
            val errorBody = runCatching { response.body?.string() }.getOrNull().orEmpty()
            throw TTSProviderException(
                message = "Step TTS request failed: HTTP ${response.code} ${response.message}. body=$errorBody",
                statusCode = response.code
            )
        }

        val audioBytes = response.body?.bytes()
            ?: throw Exception("Step TTS returned empty body")

        if (audioBytes.isEmpty()) {
            throw Exception("Step TTS returned 0 bytes")
        }

        // StepFun's format strings line up with the AudioFormat enum
        val audioFormat = when (providerSetting.responseFormat.lowercase()) {
            "mp3" -> AudioFormat.MP3
            "wav" -> AudioFormat.WAV
            "pcm" -> AudioFormat.PCM
            "ogg" -> AudioFormat.OGG
            "opus" -> AudioFormat.OPUS
            "aac" -> AudioFormat.AAC
            else -> AudioFormat.MP3
        }

        emit(
            AudioChunk(
                data = audioBytes,
                format = audioFormat,
                sampleRate = providerSetting.sampleRate,
                isLast = true,
                metadata = mapOf(
                    "provider" to "step",
                    "model" to providerSetting.model,
                    "voice" to providerSetting.voice,
                    "responseFormat" to providerSetting.responseFormat,
                )
            )
        )
    }
}
