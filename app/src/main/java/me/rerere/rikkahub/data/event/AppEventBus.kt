package me.rerere.rikkahub.data.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    /**
     * Non-suspending send; drops the event and returns false when the buffer is full.
     * Used for high-frequency events that may be lost (such as streaming generation updates), to avoid back-pressuring the sender.
     */
    fun tryEmit(event: AppEvent): Boolean = _events.tryEmit(event)
}
