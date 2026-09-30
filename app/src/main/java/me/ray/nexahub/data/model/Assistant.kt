package me.ray.nexahub.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.ray.nexahub.ai.core.MessageRole
import me.ray.nexahub.ai.provider.CustomBody
import me.ray.nexahub.ai.provider.CustomHeader
import me.ray.nexahub.ai.ui.UIMessage
import me.ray.nexahub.ai.core.ReasoningLevel
import me.ray.nexahub.data.ai.tools.local.LocalToolOption
import me.ray.nexahub.utils.SimpleCache
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val chatModelId: Uuid? = null, // If null, the global default model is used
    val name: String = "",
    val avatar: Avatar = Avatar.Dummy,
    val useAssistantAvatar: Boolean = false, // Use the assistant avatar instead of the model avatar
    val tags: List<Uuid> = emptyList(),
    val systemPrompt: String = "",
    val temperature: Float? = null,
    val topP: Float? = null,
    // Maximum number of context messages; beyond it, truncate in steps; 0 means no limit
    val contextMessageLimit: Int = 0,
    val streamOutput: Boolean = true,
    val enableMemory: Boolean = false,
    val useGlobalMemory: Boolean = false, // Use the globally shared memory instead of per-assistant memory
    val enableRecentChatsReference: Boolean = false,
    val messageTemplate: String = "{{ message }}",
    val presetMessages: List<UIMessage> = emptyList(),
    val quickMessageIds: Set<Uuid> = emptySet(),
    val regexes: List<AssistantRegex> = emptyList(),
    val reasoningLevel: ReasoningLevel = ReasoningLevel.AUTO,
    val maxTokens: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val mcpServers: Set<Uuid> = emptySet(),
    val localTools: List<LocalToolOption> = listOf(LocalToolOption.TimeInfo),
    val enableWebSearch: Boolean = false, // Web search toggle (per assistant)
    val workspaceId: Uuid? = null,
    val background: String? = null, // Chat page background image (local file URI or web URL); null means no background
    val backgroundOpacity: Float = 1.0f, // Background image opacity (0~1)
    val useGradientBackground: Boolean = false, // When on, the chat page uses an animated gradient background
    val modeInjectionIds: Set<Uuid> = emptySet(),      // Linked mode injection IDs
    val lorebookIds: Set<Uuid> = emptySet(),            // Linked Lorebook IDs
    val enabledSkills: Set<String> = emptySet(),        // Names of enabled skills
    val enableTimeReminder: Boolean = false,            // Time gap reminder injection
    val timeReminderIntervalMinutes: Int = 60,          // Time reminder interval (minutes, at least 1)
    val allowConversationSystemPrompt: Boolean = false, // Allow a conversation to override the system prompt
    val allowConversationPromptInjection: Boolean = false, // Allow a conversation to bind its own prompt injections
)

@Serializable
data class QuickMessage(
    val id: Uuid = Uuid.random(),
    val title: String = "",
    val content: String = "",
)

@Serializable
data class AssistantMemory(
    val id: Int,
    val content: String = "",
)

@Serializable
enum class AssistantAffectScope {
    USER,
    ASSISTANT,
}

@Serializable
data class AssistantRegex(
    val id: Uuid,
    val name: String = "",
    val enabled: Boolean = true,
    val findRegex: String = "", // Regular expression
    val replaceString: String = "", // Replacement string
    val affectingScope: Set<AssistantAffectScope> = setOf(),
    val visualOnly: Boolean = false, // Whether it only affects the display
)

// replaceRegexes is called for every chunk during streaming, so compiled regexes must be cached,
// otherwise they'd be recompiled tens of thousands of times during a long reply; compile failures are cached too, to avoid repeatedly building exceptions
private val regexCache = SimpleCache.builder<String, Result<Regex>>()
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build()

private fun compileRegexCached(pattern: String): Regex? {
    regexCache.getIfPresent(pattern)?.let { return it.getOrNull() }
    val result = runCatching { Regex(pattern) }.onFailure { it.printStackTrace() }
    regexCache.put(pattern, result)
    return result.getOrNull()
}

fun String.replaceRegexes(
    assistant: Assistant?,
    scope: AssistantAffectScope,
    visual: Boolean = false
): String {
    if (assistant == null) return this
    if (assistant.regexes.isEmpty()) return this
    return assistant.regexes.fold(this) { acc, regex ->
        if (regex.enabled && regex.visualOnly == visual && regex.affectingScope.contains(scope)) {
            val compiled = compileRegexCached(regex.findRegex) ?: return@fold acc
            try {
                acc.replace(
                    regex = compiled,
                    replacement = regex.replaceString,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // The replacement string may reference a group that doesn't exist; return the original string on failure
                acc
            }
        } else {
            acc
        }
    }
}

/**
 * Injection position
 */
@Serializable
enum class InjectionPosition {
    @SerialName("before_system_prompt")
    BEFORE_SYSTEM_PROMPT,   // Before the system prompt

    @SerialName("after_system_prompt")
    AFTER_SYSTEM_PROMPT,    // After the system prompt (most common)

    @SerialName("top_of_chat")
    TOP_OF_CHAT,            // Very start of the conversation (before the first user message)

    @SerialName("bottom_of_chat")
    BOTTOM_OF_CHAT,         // Before the newest message (before the current user input)

    @SerialName("at_depth")
    AT_DEPTH,               // Insert at the given depth (counting back from the newest message)
}

/**
 * Prompt injection
 *
 * - ModeInjection: injection based on a mode toggle (e.g. learning mode)
 * - RegexInjection: injection based on regex matching (Lorebook)
 */
@Serializable
sealed class PromptInjection {
    abstract val id: Uuid
    abstract val name: String
    abstract val enabled: Boolean
    abstract val priority: Int
    abstract val position: InjectionPosition
    abstract val content: String
    abstract val injectDepth: Int  // Used when position is AT_DEPTH: the position counting back from the newest message
    abstract val role: MessageRole  // Injection role: USER or ASSISTANT

    /**
     * Mode injection - triggered by a toggle
     */
    @Serializable
    @SerialName("mode")
    data class ModeInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
    ) : PromptInjection()

    /**
     * Regex injection - triggered by content matching (world book)
     */
    @Serializable
    @SerialName("regex")
    data class RegexInjection(
        override val id: Uuid = Uuid.random(),
        override val name: String = "",
        override val enabled: Boolean = true,
        override val priority: Int = 0,
        override val position: InjectionPosition = InjectionPosition.AFTER_SYSTEM_PROMPT,
        override val content: String = "",
        override val injectDepth: Int = 4,
        override val role: MessageRole = MessageRole.USER,
        val keywords: List<String> = emptyList(),  // Trigger keywords
        val useRegex: Boolean = false,             // Whether to use regex matching
        val caseSensitive: Boolean = false,        // Case sensitive
        val scanDepth: Int = 4,                    // Scan the latest N messages
        val constantActive: Boolean = false,       // Always active (no match needed)
    ) : PromptInjection()
}

/**
 * Lorebook - organizes multiple RegexInjections
 */
@Serializable
data class Lorebook(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val enabled: Boolean = true,
    val entries: List<PromptInjection.RegexInjection> = emptyList(),
)

/**
 * Check whether a RegexInjection is triggered
 *
 * @param context the context text to scan
 * @return whether it is triggered
 */
fun PromptInjection.RegexInjection.isTriggered(context: String): Boolean {
    if (!enabled) return false
    if (constantActive) return true
    if (keywords.isEmpty()) return false

    return keywords.any { keyword ->
        if (useRegex) {
            try {
                val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(keyword, options).containsMatchIn(context)
            } catch (e: Exception) {
                false
            }
        } else {
            if (caseSensitive) {
                context.contains(keyword)
            } else {
                context.contains(keyword, ignoreCase = true)
            }
        }
    }
}

/**
 * Extract the context text used for matching from a message list
 *
 * @param messages the message list
 * @param scanDepth the scan depth (latest N messages)
 * @return the concatenated text
 */
fun extractContextForMatching(
    messages: List<UIMessage>,
    scanDepth: Int
): String {
    return messages
        .takeLast(scanDepth)
        .joinToString("\n") { it.toText() }
}

/**
 * Get all triggered injections, sorted by priority
 *
 * @param injections all injection rules
 * @param context the context text
 * @return the triggered injections, sorted by priority in descending order
 */
fun getTriggeredInjections(
    injections: List<PromptInjection.RegexInjection>,
    context: String
): List<PromptInjection.RegexInjection> {
    return injections
        .filter { it.isTriggered(context) }
        .sortedByDescending { it.priority }
}
