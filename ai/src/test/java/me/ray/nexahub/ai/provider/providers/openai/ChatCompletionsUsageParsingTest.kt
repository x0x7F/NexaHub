package me.ray.nexahub.ai.provider.providers.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.ray.nexahub.ai.core.TokenUsage
import me.ray.nexahub.ai.util.KeyRoulette
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChatCompletionsAPI token usage parsing.
 */
class ChatCompletionsUsageParsingTest {

    private lateinit var api: ChatCompletionsAPI

    @Before
    fun setUp() {
        api = ChatCompletionsAPI(OkHttpClient(), KeyRoulette.default())
    }

    // Helper to invoke private parseTokenUsage via reflection
    private fun parseTokenUsage(usage: JsonObject): TokenUsage? {
        val method = ChatCompletionsAPI::class.java.getDeclaredMethod(
            "parseTokenUsage",
            JsonObject::class.java
        )
        method.isAccessible = true
        return method.invoke(api, usage) as TokenUsage?
    }

    // #1576: cached tokens fall back through provider dialects
    @Test
    fun `cached tokens fall back across provider dialects`() {
        fun usage(jsonStr: String) = parseTokenUsage(Json.parseToJsonElement(jsonStr).jsonObject)

        // Official OpenAI nested format
        assertEquals(
            12,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2,"prompt_tokens_details":{"cached_tokens":12}}""")?.cachedTokens
        )
        // Moonshot top-level cached_tokens
        assertEquals(
            7,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2,"cached_tokens":7}""")?.cachedTokens
        )
        // DeepSeek top-level prompt_cache_hit_tokens
        assertEquals(
            5,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2,"prompt_cache_hit_tokens":5,"prompt_cache_miss_tokens":3}""")?.cachedTokens
        )
        // Nested fields take precedence over the top-level fallback
        assertEquals(
            12,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2,"prompt_tokens_details":{"cached_tokens":12},"cached_tokens":7}""")?.cachedTokens
        )
        // 0 when neither is present
        assertEquals(
            0,
            usage("""{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}""")?.cachedTokens
        )
    }
}
