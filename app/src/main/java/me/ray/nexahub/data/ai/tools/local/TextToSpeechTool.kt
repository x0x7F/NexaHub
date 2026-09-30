package me.ray.nexahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.ray.nexahub.ai.core.InputSchema
import me.ray.nexahub.ai.core.Tool
import me.ray.nexahub.ai.ui.UIMessagePart
import me.ray.nexahub.data.datastore.SettingsStore
import me.ray.nexahub.data.datastore.getSelectedTTSProvider
import me.ray.nexahub.data.event.AppEvent
import me.ray.nexahub.data.event.AppEventBus
import me.ray.nexahub.tts.provider.TTSManager

internal fun buildTextToSpeechTool(
    eventBus: AppEventBus,
    ttsManager: TTSManager,
    settingsStore: SettingsStore,
): Tool = Tool(
    name = "text_to_speech",
    description = """
        Speak text aloud to the user using the device's text-to-speech engine.
        Use this when the user asks you to read something aloud, or when audio output is appropriate.
        The tool returns immediately; audio plays in the background on the device.
        Provide natural, readable text without markdown formatting.
    """.trimIndent().replace("\n", " "),
    systemPrompt = { _, _ ->
        // If the selected TTS provider has hard-coded tone-tag guidance, inject it into the system prompt (otherwise empty)
        settingsStore.settingsFlow.value.getSelectedTTSProvider()
            ?.let { ttsManager.getPromptGuidance(it) }
            .orEmpty()
    },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "The text to speak aloud")
                })
            },
            required = listOf("text")
        )
    },
    execute = {
        val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            ?: error("text is required")
        eventBus.emit(AppEvent.Speak(text))
        val payload = buildJsonObject {
            put("success", true)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)
