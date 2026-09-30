package me.ray.nexahub.ai.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.ray.nexahub.ai.provider.CustomBody
import me.ray.nexahub.ai.provider.CustomHeader
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.internal.http.RealResponseBody

fun List<CustomHeader>.toHeaders(): Headers {
    return Headers.Builder().apply {
        this@toHeaders
            .filter { it.name.isNotBlank() }
            .forEach {
                add(it.name, it.value)
            }
    }.build()
}

fun Request.Builder.configureReferHeaders(url: String): Request.Builder {
    val httpUrl = url.toHttpUrl()
    return when (httpUrl.host) {
        "openrouter.ai" -> {
            addHeader("X-Title", "NexaHub")
        }

        else -> this
    }
}

fun Request.Builder.configureSessionHeaders(url: String, sessionId: String?): Request.Builder = apply {
    if (sessionId != null) {
        header("X-Session-ID", sessionId)
        if (url.toHttpUrl().host == "opencode.ai") {
            header("x-opencode-session", sessionId)
        }
    }
}

fun ResponseBody.stringSafe(): String? {
    return when (this) {
        is RealResponseBody -> string()
        else -> null
    }
}

fun JsonObject.mergeCustomBody(bodies: List<CustomBody>): JsonObject {
    if (bodies.isEmpty()) return this

    val content = toMutableMap()
    bodies.forEach { body ->
        if (body.key.isNotBlank()) {
            // If the same key already exists and both are JsonObjects, merge recursively
            val existingValue = content[body.key]
            val newValue = body.value

            if (existingValue is JsonObject && newValue is JsonObject) {
                // Recursively merge the two JsonObjects
                content[body.key] = mergeJsonObjects(existingValue, newValue)
            } else {
                // Replace or add directly
                content[body.key] = newValue
            }
        }
    }
    return JsonObject(content)
}

/**
 * Recursively merge two JsonObjects
 */
private fun mergeJsonObjects(base: JsonObject, overlay: JsonObject): JsonObject {
    val result = base.toMutableMap()

    for ((key, value) in overlay) {
        val baseValue = result[key]

        result[key] = if (baseValue is JsonObject && value is JsonObject) {
            // If both are JsonObjects, merge recursively
            mergeJsonObjects(baseValue, value)
        } else {
            // Otherwise replace the old value with the new one
            value
        }
    }

    return JsonObject(result)
}

/**
 * Remove or keep the given keys in a JsonElement
 * @param keys the keys to operate on
 * @param keepOnly if true, keep only the given keys; if false, remove the given keys
 * @return the processed JsonElement
 */
fun JsonElement.removeElements(keys: List<String>, keepOnly: Boolean = false): JsonElement {
    return when (this) {
        is JsonObject -> {
            val newContent = if (keepOnly) {
                // Keep only the given keys (that exist)
                keys.mapNotNull { key ->
                    get(key)?.let { key to it }
                }.toMap()
            } else {
                // Remove the given keys
                toMap().filterKeys { key -> key !in keys }
            }

            // Recursively process nested JsonElements
            JsonObject(newContent.mapValues { (_, value) ->
                value.removeElements(keys, keepOnly)
            })
        }

        is JsonArray -> {
            JsonArray(map { it.removeElements(keys, keepOnly) })
        }

        else -> this // Primitives are returned as-is
    }
}
