package me.ray.nexahub.ui.pages.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ray.nexahub.ai.core.MessageRole
import me.ray.nexahub.ai.ui.UIMessage
import me.ray.nexahub.ai.ui.UIMessagePart
import me.ray.nexahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.ray.nexahub.data.datastore.Settings
import me.ray.nexahub.data.datastore.SettingsStore
import me.ray.nexahub.data.model.Assistant
import me.ray.nexahub.data.model.Conversation
import me.ray.nexahub.data.model.MessageNode
import me.ray.nexahub.data.repository.ConversationRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.random.Random
import kotlin.uuid.Uuid

class DebugVM(
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    private val _conversationCount = MutableStateFlow<Int?>(null)
    val conversationCount: StateFlow<Int?> = _conversationCount.asStateFlow()

    // Assistant IDs referenced in chat history -> conversation count
    private val _conversationAssistants = MutableStateFlow<Map<Uuid, Int>?>(null)
    val conversationAssistants: StateFlow<Map<Uuid, Int>?> = _conversationAssistants.asStateFlow()

    init {
        refreshConversationCount()
        scanConversationAssistants()
    }

    fun refreshConversationCount() {
        viewModelScope.launch {
            _conversationCount.value = conversationRepository.countConversations()
        }
    }

    fun scanConversationAssistants() {
        viewModelScope.launch {
            _conversationAssistants.value = conversationRepository.countConversationsByAssistant()
        }
    }

    /**
     * Create placeholder assistants for assistant IDs referenced in chat history but missing from settings, so those chats become visible again
     * @return the number of restored assistants, or null if settings haven't loaded
     */
    suspend fun recoverAssistantsFromConversations(): Int? {
        val settings = settingsStore.settingsFlow.value
        if (settings.init) return null
        val conversationAssistants = conversationRepository.countConversationsByAssistant()
        _conversationAssistants.value = conversationAssistants
        val existingIds = settings.assistants.map { it.id }.toSet()
        val missing = conversationAssistants
            .filterKeys { it !in existingIds }
            .entries
            .sortedByDescending { it.value }
        if (missing.isEmpty()) return 0
        val recovered = missing.mapIndexed { index, (id, _) ->
            Assistant(id = id, name = "Restored assistant ${index + 1}")
        }
        settingsStore.update(settings.copy(assistants = settings.assistants + recovered))
        return recovered.size
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    /**
     * Create a very large conversation to test the CursorWindow limit
     * @param sizeMB target size (MB)
     */
    fun createOversizedConversation(sizeMB: Int = 3) {
        viewModelScope.launch {
            val targetSize = sizeMB * 1024 * 1024
            val messageNodes = mutableListOf<MessageNode>()
            var currentSize = 0

            // Generate lots of messages until the target size is reached
            var index = 0
            while (currentSize < targetSize) {
                // Generate a message with lots of text (about 100KB each)
                val largeText = buildString {
                    repeat(100) {
                        append("This is a long piece of test text used to test the CursorWindow size limit. ")
                        append("The Row too big to fit into CursorWindow error usually happens when a single row exceeds 2MB. ")
                        append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ")
                        append("Index: $index, Block: $it. ")
                    }
                }

                val userMessage = UIMessage(
                    id = Uuid.random(),
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(largeText)),
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                )
                val assistantMessage = UIMessage(
                    id = Uuid.random(),
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("Reply: $largeText")),
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                )

                messageNodes.add(MessageNode.of(userMessage))
                messageNodes.add(MessageNode.of(assistantMessage))

                currentSize += largeText.length * 2 * 2 // Rough estimate
                index++
            }

            val conversation = Conversation(
                id = Uuid.random(),
                assistantId = DEFAULT_ASSISTANT_ID,
                title = "Very large conversation test (${sizeMB}MB)",
                messageNodes = messageNodes,
            )

            conversationRepository.insertConversation(conversation)
        }
    }

    fun createConversationWithMessages(messageCount: Int = 1024) {
        viewModelScope.launch {
            val messageNodes = ArrayList<MessageNode>(messageCount)
            val timeZone = TimeZone.currentSystemDefault()
            repeat(messageCount) { index ->
                val role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT
                val message = UIMessage(
                    id = Uuid.random(),
                    role = role,
                    parts = listOf(UIMessagePart.Text(randomMessageText(index, role))),
                    createdAt = Clock.System.now().toLocalDateTime(timeZone),
                )
                messageNodes.add(MessageNode.of(message))
            }

            val conversation = Conversation(
                id = Uuid.random(),
                assistantId = DEFAULT_ASSISTANT_ID,
                title = "${messageCount} message test",
                messageNodes = messageNodes,
            )

            conversationRepository.insertConversation(conversation)
        }
    }

    private fun randomMessageText(index: Int, role: MessageRole): String {
        val fragments = listOf(
            "quick", "random", "message", "sample", "for", "testing", "list", "rendering", "scrolling", "performance",
            "chat", "conversation", "content", "structure", "verify", "paging", "order", "stable", "system",
        )
        val wordCount = Random.nextInt(6, 14)
        val prefix = if (role == MessageRole.USER) "User" else "Assistant"
        val body = List(wordCount) { fragments.random() }.joinToString(" ")
        return "$prefix#${index + 1}: $body"
    }
}
