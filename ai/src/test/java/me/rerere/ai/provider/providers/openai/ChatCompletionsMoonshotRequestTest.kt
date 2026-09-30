package me.rerere.ai.provider.providers.openai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Moonshot (api.moonshot.cn) thinking.keep handling:
 * - K2.6 kept-thinking via thinking.keep = "all" (#1586)
 */
class ChatCompletionsMoonshotRequestTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    // Helper to invoke private buildChatCompletionRequest via reflection
    private fun buildRequest(
        modelId: String,
        reasoningLevel: ReasoningLevel,
    ): JsonObject {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "buildChatCompletionRequest",
            List::class.java,
            TextGenerationParams::class.java,
            ProviderSetting.OpenAI::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        val model = Model(
            modelId = modelId,
            abilities = listOf(ModelAbility.REASONING)
        )
        val params = TextGenerationParams(
            model = model,
            reasoningLevel = reasoningLevel,
        )
        val providerSetting = ProviderSetting.OpenAI(baseUrl = "https://api.moonshot.cn/v1")
        return method.invoke(
            api,
            listOf(UIMessage.user("hi")),
            params,
            providerSetting,
            true
        ) as JsonObject
    }

    // #1586: with thinking on, K2.6 sends thinking.keep = "all" (preserved thinking)
    @Test
    fun `k2_6 sends thinking keep all when reasoning enabled`() {
        val body = buildRequest("kimi-k2.6", ReasoningLevel.HIGH)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertEquals("all", thinking?.get("keep")?.jsonPrimitive?.content)
    }

    // #1586: with thinking off, K2.6 doesn't send keep (the docs recommend pairing keep with enabled)
    @Test
    fun `k2_6 omits keep when reasoning disabled`() {
        val body = buildRequest("kimi-k2.6", ReasoningLevel.OFF)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("disabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertFalse(thinking?.containsKey("keep") == true)
    }

    // #1586: K2.5 doesn't support the keep parameter, so it isn't sent even with thinking on
    @Test
    fun `k2_5 never sends keep`() {
        val body = buildRequest("kimi-k2.5", ReasoningLevel.HIGH)
        val thinking = body["thinking"]?.jsonObject
        assertEquals("enabled", thinking?.get("type")?.jsonPrimitive?.content)
        assertFalse(thinking?.containsKey("keep") == true)
    }
}
