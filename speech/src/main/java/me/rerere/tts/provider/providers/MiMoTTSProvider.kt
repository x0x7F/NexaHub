package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.http.SseEvent
import me.rerere.common.http.sseFlow
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
import java.util.Base64
import java.util.concurrent.TimeUnit

// MiMo streaming audio uses 24kHz PCM16LE per the docs example
private const val MIMO_SAMPLE_RATE = 24000
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
// Only delta.audio.data matters; other fields are ignored
private val mimoJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class MiMoChunk(
    val choices: List<MiMoChoice> = emptyList()
)

@Serializable
private data class MiMoChoice(
    val delta: MiMoDelta? = null
)

@Serializable
private data class MiMoDelta(
    val audio: MiMoAudio? = null
)

@Serializable
private data class MiMoAudio(
    val data: String? = null
)

internal fun decodeMiMoAudioData(data: String): ByteArray? {
    val payload = data.trim()
    // [DONE] marks the end of the stream and produces no audio
    if (payload == "[DONE]") return null
    // data other than [DONE] is treated as a JSON fragment; parse failures are rethrown
    val chunk = mimoJson.decodeFromString<MiMoChunk>(payload)
    val encoded = chunk.choices.firstOrNull()?.delta?.audio?.data ?: return null
    // An empty string counts as no audio fragment
    if (encoded.isBlank()) return null
    return Base64.getDecoder().decode(encoded)
}

internal class MiMoSseProcessor(
    private val model: String,
    private val voice: String
) {
    private var hasAudio = false
    // metadata is built once and used for the whole stream
    private val metadata = mapOf(
        "provider" to "mimo",
        "model" to model,
        "voice" to voice
    )

    fun process(event: SseEvent): AudioChunk? {
        return when (event) {
            is SseEvent.Open -> null
            is SseEvent.Event -> {
                // Only handle delta events containing audio.data; ignore other events
                val pcmData = decodeMiMoAudioData(event.data) ?: return null
                hasAudio = true
                AudioChunk(
                    data = pcmData,
                    format = AudioFormat.PCM,
                    sampleRate = MIMO_SAMPLE_RATE,
                    metadata = metadata
                )
            }

            is SseEvent.Closed -> {
                // If the whole stream had no audio fragments, fail
                if (!hasAudio) {
                    throw IllegalStateException("MiMo TTS returned no audio chunks")
                }
                // Add a terminating chunk when the stream closes so the player can finish up
                AudioChunk(
                    data = byteArrayOf(),
                    format = AudioFormat.PCM,
                    sampleRate = MIMO_SAMPLE_RATE,
                    isLast = true,
                    metadata = metadata
                )
            }

            is SseEvent.Failure -> {
                val statusCode = event.response?.code
                if (statusCode != null) {
                    throw TTSProviderException(
                        message = "MiMo TTS streaming failed: HTTP $statusCode",
                        statusCode = statusCode,
                        cause = event.throwable
                    )
                }
                throw event.throwable ?: Exception("MiMo TTS streaming failed")
            }
        }
    }
}

class MiMoTTSProvider : TTSProvider<TTSProviderSetting.MiMo> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    // MiMo supports embedding style/audio tags in the spoken text to control tone and emotion
    // Official docs: https://xiaomimimo.com (audio tag control)
    override val promptGuidance: String = """
        The active text-to-speech engine (MiMo) supports emotion and style control via embedded tags.
        When you call the text_to_speech tool, you MAY enrich the "text" argument with these tags to make the speech more expressive.
        Put tags ONLY inside the tool's text argument — never in your visible reply to the user.

        Two kinds of tags:
        1. Overall style tag — place ONE at the very beginning of the text: (style) . Combine multiple styles with spaces inside the same brackets, e.g. (开心 磁性) . Brackets may be () , （） or [] .
           Common styles: 开心/悲伤/愤怒/恐惧/惊讶/兴奋/委屈/平静/冷漠/怅然/欣慰/无奈/释然/温柔/高冷/活泼/严肃/慵懒/俏皮/深沉/磁性/醇厚/清亮/空灵/甜美/沙哑/御姐音/正太音/大叔音/台湾腔/东北话/四川话/河南话/粤语 . Custom styles are also allowed.
           For singing, the text MUST start with (唱歌) followed by lyrics (Chinese lyrics work best).
        2. Inline audio tags — insert [tag] anywhere to fine-tune delivery, e.g. [吸气] [深呼吸] [叹气] [笑] [轻笑] [大笑] [冷笑] [抽泣] [哽咽] [颤抖] [气声] [撒娇] [疲惫] [震惊] .

        IMPORTANT constraints (required by this app's text pipeline):
        - Do NOT put any punctuation (，。！？、：；…) INSIDE a tag's brackets. Separate multiple styles with spaces only, e.g. write (紧张 深呼吸) NOT (紧张，深呼吸).
        - Keep inline audio tags standalone like [笑]; do not immediately follow a [tag] with a (…) group.
        - Do not use markdown emphasis (*, _) — it will be stripped.
        - Use tags naturally and sparingly; don't over-annotate.

        Example text argument: (磁性)It's late at night[叹气]and the city is still breathing. I'm the one keeping you company tonight.
    """.trimIndent()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.MiMo,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        // OpenAI-compatible chat/completions SSE streaming; audio deltas are in delta.audio.data
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", request.text)
                })
            })
            put("audio", buildJsonObject {
                put("format", "pcm16")
                put("voice", providerSetting.voice)
            })
            put("stream", true)
        }

        // baseUrl can be customized by the user in settings, so the path is appended directly here
        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl}/chat/completions")
            // MiMo passes the token in the api-key header
            .addHeader("api-key", providerSetting.apiKey)
            .addHeader("Content-Type", "application/json")
            // JsonObject's toString outputs a JSON string
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val processor = MiMoSseProcessor(
            model = providerSetting.model,
            voice = providerSetting.voice
        )

        httpClient.sseFlow(httpRequest).collect { event ->
            processor.process(event)?.let { emit(it) }
        }
    }
}
