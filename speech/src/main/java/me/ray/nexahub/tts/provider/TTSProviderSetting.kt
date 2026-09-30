package me.ray.nexahub.tts.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class TTSProviderSetting {
    abstract val id: Uuid
    abstract val name: String

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
    ): TTSProviderSetting

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: Uuid = Uuid.random(),
        override var name: String = "OpenAI TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com/v1",
        val model: String = "gpt-4o-mini-tts",
        val voice: String = "alloy"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("gemini")
    data class Gemini(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Gemini TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        val model: String = "gemini-2.5-flash-preview-tts",
        val voiceName: String = "Kore"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("system")
    data class SystemTTS(
        override var id: Uuid = Uuid.random(),
        override var name: String = "System TTS",
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("minimax")
    data class MiniMax(
        override var id: Uuid = Uuid.random(),
        override var name: String = "MiniMax TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.minimaxi.com/v1",
        val model: String = "speech-2.8-hd",
        val voiceId: String = "female-shaonv",
        val speed: Float = 1.0f
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("qwen")
    data class Qwen(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Qwen TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1",
        val model: String = "qwen-audio-3.0-tts-flash",
        val voice: String = "longanhuan_v3.6",
        val format: String = "wav",
        val sampleRate: Int = 24000,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("groq")
    data class Groq(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Groq TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.groq.com/openai/v1",
        val model: String = "canopylabs/orpheus-v1-english",
        val voice: String = "austin"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("xai")
    data class XAI(
        override var id: Uuid = Uuid.random(),
        override var name: String = "xAI TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.x.ai/v1",
        val voiceId: String = "eve",
        val language: String = "auto"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("mimo")
    // The defaults are only for a quick start and can be changed freely in settings
    data class MiMo(
        override var id: Uuid = Uuid.random(),
        override var name: String = "MiMo TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.xiaomimimo.com/v1",
        val model: String = "mimo-v2.5-tts",
        val voice: String = "mimo_default"
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("elevenlabs")
    data class ElevenLabs(
        override var id: Uuid = Uuid.random(),
        override var name: String = "ElevenLabs TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.elevenlabs.io",
        val model: String = "eleven_multilingual_v2",
        val voiceId: String = "JBFqnCBsd6RMkjVDRZzb",
        val stability: Float = 0.5f,
        val similarityBoost: Float = 0.75f,
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * StepFun Step TTS (step-tts-mini / step-tts-vivid / stepaudio-2.5-tts).
     *
     * Shares the same baseUrl and authentication as Step ASR (Authorization: Bearer sk-xxx),
     * using the OpenAI-compatible non-streaming [POST /v1/audio/speech] endpoint; the server returns the complete audio
     * binary at once (mp3 by default, optionally wav/pcm/opus/flac). The client wraps the whole audio into one AudioChunk
     * and emits it; TtsSynthesizer collects it and hands it to the player.
     *
     * Only the stepaudio-2.5-tts model supports the [instruction] field (global context, ≤200 characters); other models
     * (step-tts-mini / step-tts-vivid / step-tts-2) ignore it, and it isn't sent when empty.
     *
     * Official docs:
     * - Model overview: https://platform.stepfun.com/docs/guides/models/stepaudio-2.5-tts
     * - Developer guide: https://platform.stepfun.com/docs/guides/developer/tts
     */
    @Serializable
    @SerialName("step")
    data class Step(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Step TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.stepfun.com",
        // step-tts-mini | step-tts-vivid | stepaudio-2.5-tts | step-tts-2
        val model: String = "step-tts-mini",
        // See the developer guide for the full voice-id list; the default matches the official SDK
        val voice: String = "elegantgentle-female",
        // mp3 | wav | pcm | opus | flac; note that the StepFun API uses camelCase field names
        val responseFormat: String = "mp3",
        // 0.5 - 2.0, 1.0 is normal speed
        val speed: Float = 1.0f,
        // 0.1 - 2.0, 1.0 is normal volume
        val volume: Float = 1.0f,
        // 8000 | 16000 | 22050 | 24000
        val sampleRate: Int = 24000,
        // Only applies to stepaudio-2.5-tts; ≤200 characters, not sent when empty
        val instruction: String = "",
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("fish-audio")
    data class FishAudio(
        override var id: Uuid = Uuid.random(),
        override var name: String = "Fish Audio TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://api.fish.audio",
        val model: String = "s2.1-pro",
        val referenceId: String = "",
        val temperature: Float = 0.7f,
        val speed: Float = 1.0f,
        val format: String = "mp3",
        val topP: Float = 0.7f,
        val chunkLength: Int = 300,
        val normalize: Boolean = true,
        val latency: String = "normal",
    ) : TTSProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): TTSProviderSetting {
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
        override val name: String = "Volcengine TTS",
        val apiKey: String = "",
        val baseUrl: String = "https://openspeech.bytedance.com",
        val resourceId: String = "seed-tts-2.0",
        val speaker: String = "zh_female_vv_uranus_bigtts",
        val speechRate: Int = 0,
    ) : TTSProviderSetting() {
        override fun copyProvider(id: Uuid, name: String): TTSProviderSetting =
            copy(id = id, name = name)
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Gemini::class,
                SystemTTS::class,
                MiniMax::class,
                Qwen::class,
                Groq::class,
                XAI::class,
                MiMo::class,
                ElevenLabs::class,
                Step::class,
                FishAudio::class,
                Volcengine::class,
            )
        }
    }
}
