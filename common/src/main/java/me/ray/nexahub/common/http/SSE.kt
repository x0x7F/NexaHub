package me.ray.nexahub.common.http

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * Represents the events of an SSE connection
 */
sealed class SseEvent {
    /**
     * The connection opened successfully
     */
    data object Open : SseEvent()

    /**
     * A specific event was received
     * @param id event ID
     * @param type event type
     * @param data event data
     */
    data class Event(val id: String?, val type: String?, val data: String) : SseEvent()

    /**
     * The connection was closed
     */
    data object Closed : SseEvent()

    /**
     * An error occurred
     * @param throwable the exception
     * @param response the response at the time of the error (may be null)
     */
    data class Failure(val throwable: Throwable?, val response: Response?) : SseEvent()
}


/**
 * Extension function that creates an SSE (Server-Sent Events) connection for an OkHttpClient
 * 
 * Wraps OkHttp's EventSource in a Kotlin Flow, providing a reactive stream of SSE events
 * 
 * @param request the HTTP request used to establish the SSE connection
 * @return a Flow<SseEvent> reactive stream of SSE events
 */
fun OkHttpClient.sseFlow(request: Request): Flow<SseEvent> {
    return callbackFlow {
        // 1. Create the EventSourceListener
        // Listens for SSE connection events and converts them into Flow events
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                // Safely send events from the callback into the Flow
                // Triggered when the connection is established
                trySend(SseEvent.Open)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                // Triggered when a data event is received from the server
                // Wrap the event data and send it into the Flow
                trySend(SseEvent.Event(id, type, data))
            }

            override fun onClosed(eventSource: EventSource) {
                // Triggered when the connection closes normally
                trySend(SseEvent.Closed)
                channel.close() // Close the Flow channel
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // Triggered when a connection error occurs
                trySend(SseEvent.Failure(t, response))
                channel.close(t) // Close the Flow channel with the exception
            }
        }

        // 2. Create the EventSource
        // Create an EventSource factory with the current OkHttpClient
        val factory = EventSources.createFactory(this@sseFlow)
        val eventSource = factory.newEventSource(request, listener)

        // 3. awaitClose runs cleanup when the Flow is cancelled
        // This block is called when the coroutine collecting the Flow is cancelled
        awaitClose {
            // Close the SSE connection and release resources
            eventSource.cancel()
        }
    }
}
