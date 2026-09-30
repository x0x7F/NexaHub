package me.ray.nexahub.data.ai.mcp

sealed class McpStatus {
    data object Idle : McpStatus()
    data object Connecting : McpStatus()
    data object Connected : McpStatus()
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : McpStatus()

    /**
     * Connection/sync error.
     *
     * @param message short summary, shown inline in the list
     * @param detail full error details (including the cause chain and stack trace) for expanding and copying; null when there's no exception source
     */
    data class Error(val message: String, val detail: String? = null) : McpStatus() {
        companion object {
            fun from(throwable: Throwable, fallbackMessage: String? = null): Error {
                val summary = throwable.message?.takeIf { it.isNotBlank() }
                    ?: fallbackMessage
                    ?: throwable.javaClass.simpleName
                return Error(message = summary, detail = throwable.stackTraceToString())
            }
        }
    }

    /** The server returned 401 and the user needs to complete OAuth authorization. */
    data object NeedsAuthorization : McpStatus()

    /** OAuth authorization in progress (waiting for the browser callback / exchanging tokens). */
    data object Authorizing : McpStatus()
}
