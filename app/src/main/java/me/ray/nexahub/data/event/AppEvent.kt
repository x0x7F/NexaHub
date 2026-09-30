package me.ray.nexahub.data.event

import me.ray.nexahub.ai.ui.UIMessage
import kotlin.uuid.Uuid

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
    data object OpenUsageAccessSettings : AppEvent()

    /** Streaming updates during chat generation, consumed by ChatNotificationManager for Live Update notifications. */
    data class ChatGenerationUpdate(
        val conversationId: Uuid,
        val lastMessage: UIMessage,
        val senderName: String,
    ) : AppEvent()

    /**
     * Chat generation ended (finished, failed or cancelled).
     * When [contentPreview] is null, only the Live Update notification is cancelled and no completion notification is sent.
     */
    data class ChatGenerationEnded(
        val conversationId: Uuid,
        val senderName: String,
        val contentPreview: String?,
    ) : AppEvent()
}
