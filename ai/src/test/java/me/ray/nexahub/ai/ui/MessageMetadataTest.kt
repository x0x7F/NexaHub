package me.ray.nexahub.ai.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies two-way compatibility between typed metadata and old hand-written JsonObject data
 */
class MessageMetadataTest {

    private fun reasoningWith(metadata: JsonObject?) = UIMessagePart.Reasoning(
        reasoning = "thinking...",
        metadata = metadata,
    )

    // ===== Reading old data (the exact format old code for each provider wrote) =====

    @Test
    fun `parses legacy claude metadata`() {
        // Old code: buildJsonObject { put("signature", signature) }
        val part = reasoningWith(buildJsonObject { put("signature", "sig-abc") })
        assertEquals("sig-abc", part.metadataAs<ClaudeReasoningMetadata>()?.signature)
    }

    @Test
    fun `parses legacy openai metadata`() {
        // Old code: buildJsonObject { put("encrypted_content", ...); put("reasoning_id", ...) }
        val part = reasoningWith(buildJsonObject {
            put("encrypted_content", "enc-xyz")
            put("reasoning_id", "rs_123")
        })
        val meta = part.metadataAs<OpenAIReasoningMetadata>()
        assertEquals("rs_123", meta?.reasoningId)
        assertEquals("enc-xyz", meta?.encryptedContent)
    }

    @Test
    fun `parses legacy openai metadata with json null encrypted content`() {
        // Old code put("encrypted_content", null) writes a JsonNull
        val part = reasoningWith(buildJsonObject {
            put("encrypted_content", null as String?)
            put("reasoning_id", "rs_123")
        })
        val meta = part.metadataAs<OpenAIReasoningMetadata>()
        assertEquals("rs_123", meta?.reasoningId)
        assertNull(meta?.encryptedContent)
    }

    @Test
    fun `parses legacy google metadata with json null thought signature`() {
        // Old code always wrote metadata; thoughtSignature was JsonNull when empty
        val withValue = reasoningWith(buildJsonObject { put("thoughtSignature", "ts-1") })
        assertEquals("ts-1", withValue.metadataAs<GoogleThoughtMetadata>()?.thoughtSignature)

        val withNull = reasoningWith(buildJsonObject { put("thoughtSignature", null as String?) })
        assertNull(withNull.metadataAs<GoogleThoughtMetadata>()?.thoughtSignature)
    }

    // ===== Fault tolerance =====

    @Test
    fun `returns null when metadata is absent`() {
        assertNull(reasoningWith(null).metadataAs<ClaudeReasoningMetadata>())
    }

    @Test
    fun `cross provider metadata does not interfere`() {
        // After switching providers, metadata written by OpenAI is parsed by Claude: no exception, signature is null
        val part = reasoningWith(buildJsonObject {
            put("encrypted_content", "enc-xyz")
            put("reasoning_id", "rs_123")
        })
        assertNull(part.metadataAs<ClaudeReasoningMetadata>()?.signature)
    }

    @Test
    fun `malformed metadata returns null instead of throwing`() {
        // Type mismatch (signature is an object, not a string)
        val part = reasoningWith(buildJsonObject {
            put("signature", buildJsonObject { put("nested", "value") })
        })
        assertNull(part.metadataAs<ClaudeReasoningMetadata>())
    }

    // ===== Write format keeps the old keys (newly written data can be read the old way) =====

    @Test
    fun `written metadata uses legacy keys`() {
        val openai = OpenAIReasoningMetadata(reasoningId = "rs_1", encryptedContent = "enc").toMetadata()
        assertEquals("rs_1", openai["reasoning_id"]?.jsonPrimitive?.content)
        assertEquals("enc", openai["encrypted_content"]?.jsonPrimitive?.content)

        val claude = ClaudeReasoningMetadata(signature = "sig").toMetadata()
        assertEquals("sig", claude["signature"]?.jsonPrimitive?.content)

        val google = GoogleThoughtMetadata(thoughtSignature = "ts").toMetadata()
        assertEquals("ts", google["thoughtSignature"]?.jsonPrimitive?.content)
    }

    @Test
    fun `null fields are omitted when writing`() {
        // explicitNulls = false: null fields are not written, so there's no "thoughtSignature": null
        val metadata = GoogleThoughtMetadata(thoughtSignature = null).toMetadata()
        assertFalse(metadata.containsKey("thoughtSignature"))
    }

    @Test
    fun `metadata round trip via persistence is stable`() {
        // Simulate persistence: serialize the part to a JSON string and read it back; metadata is neither lost nor altered
        val json = Json { ignoreUnknownKeys = true }
        val part: UIMessagePart = reasoningWith(
            OpenAIReasoningMetadata(reasoningId = "rs_1", encryptedContent = "enc").toMetadata()
        )
        val restored = json.decodeFromString<UIMessagePart>(json.encodeToString(part))
        val meta = restored.metadataAs<OpenAIReasoningMetadata>()
        assertEquals("rs_1", meta?.reasoningId)
        assertEquals("enc", meta?.encryptedContent)
    }

    @Test
    fun `legacy json null thought signature does not survive rewrite`() {
        // Old data with JsonNull -> parse -> write again: the JsonNull is dropped rather than kept
        val legacy = reasoningWith(buildJsonObject { put("thoughtSignature", JsonNull) })
        val rewritten = legacy.metadataAs<GoogleThoughtMetadata>()?.toMetadata()
        assertEquals(JsonObject(emptyMap()), rewritten)
    }

    @Test
    fun `diff metadata round trip`() {
        val diff = "--- a/file.txt\n+++ b/file.txt\n@@ -1,1 +1,1 @@\n-old\n+new"
        val part = UIMessagePart.Text(
            text = "{}",
            metadata = DiffMetadata(diff = diff).toMetadata(),
        )
        assertEquals(diff, part.metadataAs<DiffMetadata>()?.diff)
    }
}
