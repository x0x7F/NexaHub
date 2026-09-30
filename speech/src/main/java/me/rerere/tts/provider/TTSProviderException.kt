package me.rerere.tts.provider

/**
 * HTTP error returned by a TTS provider. Keeps the status code so upper layers can decide whether a retry is worthwhile.
 */
class TTSProviderException(
    message: String,
    val statusCode: Int,
    cause: Throwable? = null
) : Exception(message, cause) {
    val isRetryable: Boolean
        get() = statusCode == 408 || statusCode == 429 || statusCode in 500..599
}
