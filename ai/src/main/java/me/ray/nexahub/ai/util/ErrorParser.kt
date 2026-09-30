package me.ray.nexahub.ai.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class HttpException(
    message: String
) : RuntimeException(message)

fun JsonElement.parseErrorDetail(): HttpException {
    return when (this) {
        is JsonObject -> {
            // Try the common error fields
            val errorFields = listOf("error", "detail", "message", "description")

            // Find the first error field that exists
            val foundField = errorFields.firstOrNull { this[it] != null }

            if (foundField != null) {
                // Recursively parse the value of the field found
                this[foundField]!!.parseErrorDetail()
            } else {
                // If no error field is found, serialize the whole object
                HttpException(Json.encodeToString(JsonElement.serializer(), this))
            }
        }

        is JsonArray -> {
            if (this.isEmpty()) {
                HttpException("Unknown error: Empty JSON array")
            } else {
                // Recursively parse the first element of the array
                this.first().parseErrorDetail()
            }
        }

        is JsonPrimitive -> {
            // For primitives, use their content directly
            HttpException(this.jsonPrimitive.content)
        }

        else -> {
            // Otherwise, serialize the whole element
            HttpException(Json.encodeToString(JsonElement.serializer(), this))
        }
    }
}
