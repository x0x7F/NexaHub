package me.rerere.asr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class ASRProviderSetting {
    abstract val id: Uuid
    abstract val name: String

    // Describes our adapter, not every API offered by this vendor.
    val supportsServerVadVoiceMode: Boolean
        get() = this is OpenAIRealtime || this is DashScope || this is Volcengine

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
    ): ASRProviderSetting

    @Serializable
    @SerialName("openai_realtime")
    data class OpenAIRealtime(
        override val id: Uuid = Uuid.random(),
        override val name: String = "OpenAI Realtime ASR",
        val apiKey: String = "",
        val websocketUrl: String = "wss://api.openai.com/v1/realtime?intent=transcription",
        val model: String = "gpt-4o-transcribe",
        val language: String = "",
        val prompt: String = "",
        val sampleRate: Int = 24000,
        val vadThreshold: Float = 0.5f,
        val prefixPaddingMs: Int = 300,
        val silenceDurationMs: Int = 500,
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("dashscope")
    data class DashScope(
        override val id: Uuid = Uuid.random(),
        override val name: String = "DashScope ASR",
        val apiKey: String = "",
        val websocketUrl: String = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime",
        val model: String = "qwen3-asr-flash-realtime",
        val language: String = "",
        val sampleRate: Int = 16000,
        val vadThreshold: Float = 0.0f,
        val silenceDurationMs: Int = 400,
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("volcengine")
    data class Volcengine(
        override val id: Uuid = Uuid.random(),
        override val name: String = "Volcengine ASR",
        val apiKey: String = "",
        val websocketUrl: String = VOLCENGINE_ASR_WEBSOCKET_URL,
        val resourceId: String = "volc.seedasr.sauc.duration",
        val language: String = "",
        val silenceDurationMs: Int = 800,
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * Xiaomi MiMo ASR (mimo-v2.5-asr).
     *
     * Unlike OpenAIRealtime / DashScope / Volcengine, MiMo ASR is a one-shot HTTP recognition API based on the OpenAI-compatible
     * chat/completions, not a WebSocket streaming API.
     * While recording, the client splits audio into segments by [segmentDurationSec], converts each PCM segment to WAV, base64-encodes it,
     * embeds it in the messages[].content[].input_audio.data field, and POSTs it to
     * {baseUrl}/chat/completions; the result is in choices[0].message.content.
     *
     * Official docs: https://platform.xiaomimimo.com/docs/api/audio/Speech-Recognition
     */
    @Serializable
    @SerialName("mimo")
    data class MiMo(
        override val id: Uuid = Uuid.random(),
        override val name: String = "MiMo ASR",
        val apiKey: String = "",
        val baseUrl: String = "https://api.xiaomimimo.com/v1",
        val model: String = "mimo-v2.5-asr",
        // auto | zh | en; when empty, asr_options isn't sent and the server defaults to auto
        val language: String = "auto",
        val sampleRate: Int = 16000,
        // Automatically flush the current buffer (upload for recognition) every this many seconds. 0 disables automatic segmentation,
        // uploading everything only when the user calls stop() (note that a single MiMo request is limited to about 7.5MB raw,
        // about 234 seconds at 16kHz/16bit/mono).
        val segmentDurationSec: Int = 30,
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * StepFun Step ASR (stepaudio-2.5-asr).
     *
     * Like MiMo, it's a one-shot HTTP submission + segmented upload, with a few differences:
     * 1) Authentication uses the standard `Authorization: Bearer` instead of an `api-key` header
     * 2) The server responds with an SSE stream (text/event-stream), with event types including
     *    transcript.text.delta / transcript.text.done / error
     * 3) It accepts PCM base64 directly, without a WAV header
     *
     * The client splits by [segmentDurationSec], base64-encodes each PCM segment and POSTs it to
     * {baseUrl}/v1/audio/asr/sse, streaming deltas into the partial transcript,
     * and on done appends the full segment text to completedTranscripts.
     *
     * Official docs: https://platform.stepfun.com/docs/api-reference/audio/asr-sse
     */
    @Serializable
    @SerialName("step")
    data class Step(
        override val id: Uuid = Uuid.random(),
        override val name: String = "Step ASR",
        val apiKey: String = "",
        val baseUrl: String = "https://api.stepfun.com",
        val model: String = "stepaudio-2.5-asr",
        // auto | zh | en; when empty, the language field isn't sent and the server defaults to auto
        val language: String = "auto",
        val sampleRate: Int = 16000,
        // Automatically flush (upload for recognition) every this many seconds. 0 = upload everything only on stop()
        val segmentDurationSec: Int = 30,
        // Inverse Text Normalization: turns "three hundred" -> "300", etc.
        val enableItn: Boolean = true,
        // Whether to return word-level timestamps (not consumed yet, off by default to save tokens)
        val enableTimestamp: Boolean = false,
        // Hotword list, improves recognition accuracy for proper nouns/terms
        val hotwords: List<String> = emptyList(),
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAIRealtime::class,
                DashScope::class,
                Volcengine::class,
                MiMo::class,
                Step::class,
            )
        }
    }
}

const val VOLCENGINE_ASR_WEBSOCKET_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
