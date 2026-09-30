package me.rerere.ai.ui

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.core.merge
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.TextGenerationResult
import me.rerere.ai.util.json
import kotlin.time.Clock

/**
 * Merges the generic stream events produced by a Provider into a single [UIMessage].
 *
 * Text, reasoning and images may arrive interleaved, so updating only the last part is not enough. When this class receives
 * a `Start` event, it records the position of the event id in [UIMessage.parts]; later `Delta`, `Snapshot` and `End`
 * events use the same id to locate and update that part. Tool calls are located directly by
 * [UIMessagePart.Tool.toolCallId].
 *
 * [handle] never modifies the passed-in message list; it returns a new list with the updated message. If the list doesn't
 * end with an assistant message, the first event creates an empty assistant message. On [StreamChunk.Finish], the message
 * is marked finished and the internal state is cleared.
 *
 * ## How it works
 *
 * 1. Receive a [StreamChunk] and check that the message list is not empty.
 * 2. If the list doesn't end with an assistant message, create an empty assistant message with the current [model] id.
 * 3. Update the assistant message based on the event type:
 *    - `TextStart/Delta/End`: create, append to and finish a text part.
 *    - `ReasoningStart/Delta/End`: create, append to and mark the finish time of a reasoning part.
 *    - `ToolCallStart/Delta/End`: create a tool part by call id and incrementally build the tool name and input.
 *    - `ServerToolStart/InputDelta/InputEnd/End`: track a server tool's JSON input, result and status.
 *    - `ImageStart/Delta/End`: create an image part and incrementally append Base64 data.
 *    - `ImageSnapshot`: replace the old data of the image with the same id with the latest full Base64 snapshot.
 *    - `Annotations`: append and deduplicate message annotations.
 *    - `Usage`: merge this usage into the message's existing token usage.
 *    - `Finish`: set the message finish time, finish any open reasoning parts, and clear the event index.
 * 4. Build the updated assistant message with [UIMessage.copy], replace the end of the list with it and return the new list.
 *
 * A typical call sequence for a text stream:
 *
 * ```
 * TextStart(id) -> TextDelta(id, ...) -> TextDelta(id, ...) -> TextEnd(id) -> Finish
 * ```
 *
 * [StreamChunk.Finish] only means the response stream ended normally and is not guaranteed to arrive. On network errors,
 * protocol parse failures or when the caller cancels the Flow, the stream may end with an exception. Content merged so far
 * is kept, but the message's `finishedAt` may be null, and reasoning parts that never got `ReasoningEnd` are not finished
 * by this class. Callers should do any needed UI cleanup in the Flow's completion or error handling and discard this handler; don't reuse it for the next response stream.
 *
 * This class holds the merge state of one response stream; it is not a stateless transformer. Every concurrent response
 * stream must use its own instance, and events must be fed to the same instance in the order the Provider produced them.
 */
class StreamChunkHandler(private val model: Model? = null) {
    // The map value is the index of the corresponding part in the current assistant message's parts list.
    private val textPartIndexes = mutableMapOf<String, Int>()
    private val reasoningPartIndexes = mutableMapOf<String, Int>()
    private val imagePartIndexes = mutableMapOf<String, Int>()
    private val serverToolInputBuffers = mutableMapOf<String, StringBuilder>()

    /**
     * Merge a [chunk] into the assistant message at the end of the message list and return the new message list.
     *
     * @throws IllegalArgumentException if [messages] is empty
     */
    fun handle(messages: List<UIMessage>, chunk: StreamChunk): List<UIMessage> {
        require(messages.isNotEmpty()) { "messages must not be empty" }

        val targetMessages = if (messages.last().role != MessageRole.ASSISTANT) {
            messages + UIMessage(modelId = model?.id, role = MessageRole.ASSISTANT, parts = emptyList())
        } else {
            messages
        }
        val updatedMessage = append(targetMessages.last(), chunk)
        return targetMessages.dropLast(1) + updatedMessage
    }

    private fun append(message: UIMessage, chunk: StreamChunk): UIMessage = with(message) {
        when (chunk) {
            is StreamChunk.TextStart -> {
                if (chunk.id in textPartIndexes) this
                else copy(parts = parts + UIMessagePart.Text("", chunk.metadata)).also {
                    textPartIndexes[chunk.id] = parts.size
                }
            }
            is StreamChunk.TextDelta -> {
                val index = textPartIndexes[chunk.id]
                // Tolerate providers that don't send Start: create the part on the first Delta.
                if (index == null || parts.getOrNull(index) !is UIMessagePart.Text) {
                    copy(parts = parts + UIMessagePart.Text(chunk.text, chunk.metadata)).also {
                        textPartIndexes[chunk.id] = parts.size
                    }
                } else {
                    copy(parts = parts.toMutableList().apply {
                        val text = get(index) as UIMessagePart.Text
                        set(
                            index, text.copy(
                                text = text.text + chunk.text,
                                metadata = chunk.metadata ?: text.metadata,
                            )
                        )
                    })
                }
            }

            is StreamChunk.TextEnd -> this.also { textPartIndexes.remove(chunk.id) }
            is StreamChunk.ReasoningStart -> {
                if (chunk.id in reasoningPartIndexes) this
                else copy(parts = parts + UIMessagePart.Reasoning(
                    reasoning = "",
                    createdAt = Clock.System.now(),
                    finishedAt = null,
                    metadata = chunk.metadata,
                    reasoningType = chunk.reasoningType,
                )).also { reasoningPartIndexes[chunk.id] = parts.size }
            }

            is StreamChunk.ReasoningDelta -> {
                val index = reasoningPartIndexes[chunk.id]
                if (index == null || parts.getOrNull(index) !is UIMessagePart.Reasoning) {
                    copy(parts = parts + UIMessagePart.Reasoning(
                        reasoning = chunk.text,
                        createdAt = Clock.System.now(),
                        finishedAt = null,
                        metadata = chunk.metadata,
                        reasoningType = chunk.reasoningType,
                    )).also { reasoningPartIndexes[chunk.id] = parts.size }
                } else {
                    copy(parts = parts.toMutableList().apply {
                        val reasoning = get(index) as UIMessagePart.Reasoning
                        set(index, reasoning.copy(
                            reasoning = reasoning.reasoning + chunk.text,
                            metadata = chunk.metadata ?: reasoning.metadata,
                            reasoningType = chunk.reasoningType,
                        ))
                    })
                }
            }

            is StreamChunk.ReasoningEnd -> {
                val index = reasoningPartIndexes.remove(chunk.id)
                if (index == null || parts.getOrNull(index) !is UIMessagePart.Reasoning) this
                else copy(parts = parts.toMutableList().apply {
                    val reasoning = get(index) as UIMessagePart.Reasoning
                    set(index, reasoning.copy(
                        finishedAt = Clock.System.now(),
                        metadata = chunk.metadata ?: reasoning.metadata,
                    ))
                })
            }

            is StreamChunk.ToolCallStart -> {
                if (parts.any { it is UIMessagePart.Tool && it.toolCallId == chunk.id }) this
                else copy(parts = parts + UIMessagePart.Tool(
                    toolCallId = chunk.id,
                    toolName = chunk.toolName,
                    input = "",
                    metadata = chunk.metadata,
                ))
            }

            is StreamChunk.ToolCallDelta -> copy(parts = parts.map { part ->
                // Tool calls may be generated in parallel, so the target is identified by toolCallId, not by part position.
                if (part is UIMessagePart.Tool && part.toolCallId == chunk.id) {
                    part.copy(
                        toolName = part.toolName + chunk.toolNameDelta,
                        input = part.input + chunk.inputDelta,
                        metadata = chunk.metadata ?: part.metadata,
                    )
                } else part
            })

            is StreamChunk.ToolCallEnd -> this
            is StreamChunk.ServerToolStart -> {
                val index = parts.indexOfFirst {
                    it is UIMessagePart.ServerTool && it.toolCallId == chunk.id
                }
                if (index < 0) {
                    copy(parts = parts + UIMessagePart.ServerTool(
                        toolCallId = chunk.id,
                        toolName = chunk.toolName,
                        input = chunk.input,
                        status = ServerToolStatus.IN_PROGRESS,
                        metadata = chunk.metadata,
                    ))
                } else {
                    copy(parts = parts.toMutableList().apply {
                        val tool = get(index) as UIMessagePart.ServerTool
                        set(index, tool.copy(
                            toolName = chunk.toolName.ifBlank { tool.toolName },
                            input = chunk.input ?: tool.input,
                            metadata = mergeMetadata(tool.metadata, chunk.metadata),
                        ))
                    })
                }
            }

            is StreamChunk.ServerToolInputDelta -> {
                val buffer = serverToolInputBuffers.getOrPut(chunk.id) { StringBuilder() }
                buffer.append(chunk.inputDelta)
                updateServerTool(chunk.id) { tool ->
                    tool.copy(metadata = mergeMetadata(tool.metadata, chunk.metadata))
                }
            }

            is StreamChunk.ServerToolInputEnd -> {
                val input = serverToolInputBuffers.remove(chunk.id)?.toString()?.takeIf { it.isNotBlank() }
                    ?.let(::parseServerToolJson)
                if (input == null) this else updateServerTool(chunk.id) { it.copy(input = input) }
            }

            is StreamChunk.ServerToolEnd -> {
                val bufferedInput = serverToolInputBuffers.remove(chunk.id)?.toString()?.takeIf { it.isNotBlank() }
                    ?.let(::parseServerToolJson)
                val index = parts.indexOfFirst {
                    it is UIMessagePart.ServerTool && it.toolCallId == chunk.id
                }
                if (index < 0) {
                    copy(parts = parts + UIMessagePart.ServerTool(
                        toolCallId = chunk.id,
                        toolName = "",
                        input = chunk.input ?: bufferedInput,
                        output = chunk.output,
                        status = chunk.status,
                        metadata = chunk.metadata,
                    ))
                } else {
                    updateServerTool(chunk.id) { tool ->
                        tool.copy(
                            input = chunk.input ?: bufferedInput ?: tool.input,
                            output = chunk.output ?: tool.output,
                            status = chunk.status,
                            metadata = mergeMetadata(tool.metadata, chunk.metadata),
                        )
                    }
                }
            }
            is StreamChunk.ImageStart -> {
                if (chunk.id in imagePartIndexes) this
                else copy(parts = parts + UIMessagePart.Image(
                    url = "data:${chunk.mimeType};base64,",
                    metadata = chunk.metadata,
                )).also { imagePartIndexes[chunk.id] = parts.size }
            }

            is StreamChunk.ImageDelta -> {
                val index = imagePartIndexes[chunk.id]
                if (index == null || parts.getOrNull(index) !is UIMessagePart.Image) {
                    copy(parts = parts + UIMessagePart.Image(chunk.data, chunk.metadata)).also {
                        imagePartIndexes[chunk.id] = parts.size
                    }
                } else {
                    copy(parts = parts.toMutableList().apply {
                        val image = get(index) as UIMessagePart.Image
                        set(index, image.copy(
                            url = image.url + chunk.data,
                            metadata = chunk.metadata ?: image.metadata,
                        ))
                    })
                }
            }

            is StreamChunk.ImageSnapshot -> {
                val index = imagePartIndexes[chunk.id]
                if (index == null || parts.getOrNull(index) !is UIMessagePart.Image) {
                    copy(
                        parts = parts + UIMessagePart.Image(
                            url = "data:image/png;base64,${chunk.data}",
                            metadata = chunk.metadata,
                        )
                    ).also { imagePartIndexes[chunk.id] = parts.size }
                } else {
                    copy(parts = parts.toMutableList().apply {
                        val image = get(index) as UIMessagePart.Image
                        // A Snapshot is a complete renderable image; only the data portion of the data URL is replaced;
                        // unlike ImageDelta, it is not appended after the previous frame.
                        val dataUrlPrefix = image.url.substringBefore(",").takeIf { it.startsWith("data:") }
                            ?: "data:image/png;base64"
                        set(index, image.copy(
                            url = "$dataUrlPrefix,${chunk.data}",
                            metadata = chunk.metadata ?: image.metadata,
                        ))
                    })
                }
            }

            is StreamChunk.ImageEnd -> this.also { imagePartIndexes.remove(chunk.id) }
            is StreamChunk.Annotations -> copy(annotations = (annotations + chunk.annotations).distinct())
            is StreamChunk.Usage -> copy(usage = usage.merge(chunk.usage))
            is StreamChunk.Finish -> copy(
                finishedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            ).finishReasoning().also {
                // Finish also ends any reasoning not explicitly ended and releases this stream's index state.
                textPartIndexes.clear()
                reasoningPartIndexes.clear()
                imagePartIndexes.clear()
                serverToolInputBuffers.clear()
            }
        }
    }

    private fun UIMessage.updateServerTool(
        id: String,
        transform: (UIMessagePart.ServerTool) -> UIMessagePart.ServerTool,
    ): UIMessage = copy(parts = parts.map { part ->
        if (part is UIMessagePart.ServerTool && part.toolCallId == id) transform(part) else part
    })
}

private fun parseServerToolJson(value: String) = runCatching {
    json.parseToJsonElement(value)
}.getOrElse { JsonPrimitive(value) }

private fun mergeMetadata(old: JsonObject?, new: JsonObject?): JsonObject? = when {
    old == null -> new
    new == null -> old
    else -> JsonObject(old + new)
}

fun List<UIMessage>.handleTextGenerationResult(
    result: TextGenerationResult,
    model: Model? = null,
): List<UIMessage> {
    require(isNotEmpty()) { "messages must not be empty" }
    val incoming = result.message.copy(
        modelId = model?.id,
        usage = result.usage,
        finishedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    ).finishReasoning()
    return if (last().role != incoming.role) {
        this + incoming
    } else {
        dropLast(1) + last().appendMessage(incoming).copy(
            modelId = model?.id ?: last().modelId,
            usage = last().usage.merge(result.usage ?: TokenUsage()),
            finishedAt = incoming.finishedAt,
        ).finishReasoning()
    }
}

private fun UIMessage.appendMessage(delta: UIMessage): UIMessage {
    var newParts = delta.parts.fold(parts) { acc, deltaPart ->
        when (deltaPart) {
            is UIMessagePart.Text -> {
                if (deltaPart.text.isEmpty()) {
                    acc
                } else {
                    val lastPart = acc.lastOrNull()
                    if (lastPart is UIMessagePart.Text) {
                        acc.dropLast(1) + lastPart.copy(text = lastPart.text + deltaPart.text)
                    } else {
                        acc + deltaPart
                    }
                }
            }

            // Non-streaming parsing already produced a complete renderable URL (data URI or http);
            // each image here is a standalone complete image, so URLs must not be concatenated or given a data prefix.
            is UIMessagePart.Image -> acc + deltaPart

            is UIMessagePart.Reasoning -> {
                if (deltaPart.reasoning.isEmpty() && deltaPart.metadata == null) {
                    acc
                } else {
                    val lastPart = acc.lastOrNull()
                    if (lastPart is UIMessagePart.Reasoning &&
                        lastPart.reasoningType == deltaPart.reasoningType
                    ) {
                        acc.dropLast(1) + UIMessagePart.Reasoning(
                            reasoning = lastPart.reasoning + deltaPart.reasoning,
                            createdAt = lastPart.createdAt,
                            finishedAt = null,
                            metadata = deltaPart.metadata ?: lastPart.metadata,
                            reasoningType = lastPart.reasoningType,
                        )
                    } else {
                        acc + deltaPart
                    }
                }
            }

            is UIMessagePart.Tool -> {
                if (deltaPart.toolCallId.isBlank()) {
                    val lastTool = acc.lastOrNull { it is UIMessagePart.Tool } as? UIMessagePart.Tool
                    if (lastTool != null) {
                        acc.map { part -> if (part === lastTool) part.merge(deltaPart) else part }
                    } else {
                        acc + deltaPart.copy()
                    }
                } else {
                    val existingPart = acc.find {
                        it is UIMessagePart.Tool && it.toolCallId == deltaPart.toolCallId
                    } as? UIMessagePart.Tool
                    if (existingPart == null) {
                        acc + deltaPart.copy()
                    } else {
                        acc.map { part ->
                            if (part is UIMessagePart.Tool && part.toolCallId == deltaPart.toolCallId) {
                                part.merge(deltaPart)
                            } else {
                                part
                            }
                        }
                    }
                }
            }

            is UIMessagePart.ServerTool -> {
                val existingPart = acc.find {
                    it is UIMessagePart.ServerTool && it.toolCallId == deltaPart.toolCallId
                } as? UIMessagePart.ServerTool
                if (existingPart == null) {
                    acc + deltaPart
                } else {
                    acc.map { part ->
                        if (part is UIMessagePart.ServerTool && part.toolCallId == deltaPart.toolCallId) {
                            part.copy(
                                toolName = deltaPart.toolName.ifBlank { part.toolName },
                                input = deltaPart.input ?: part.input,
                                output = deltaPart.output ?: part.output,
                                status = deltaPart.status,
                                metadata = mergeMetadata(part.metadata, deltaPart.metadata),
                            )
                        } else part
                    }
                }
            }

            else -> acc
        }
    }

    if (parts.filterIsInstance<UIMessagePart.Reasoning>().isNotEmpty() &&
        delta.parts.filterIsInstance<UIMessagePart.Reasoning>().isEmpty()
    ) {
        newParts = newParts.map { part ->
            if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                part.copy(finishedAt = Clock.System.now())
            } else {
                part
            }
        }
    }

    return copy(
        parts = newParts,
        annotations = delta.annotations.ifEmpty { annotations },
    )
}
