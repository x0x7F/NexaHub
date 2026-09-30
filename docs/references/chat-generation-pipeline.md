# Message Generation Pipeline

This document describes the full data flow from the user sending a message to the AI reply finishing, along with the core classes and processing stages involved.

## Core classes

| Class                      | Responsibility                |
|----------------------------|-----------------------------|
| `ChatService`              | Entry and orchestration layer; manages all conversations and exposes the operation API |
| `ConversationSession`      | State container for a single conversation (ref count, generation Job, processing status) |
| `GenerationHandler`        | Core generation logic; drives the step loop and tool calls |
| `InputMessageTransformer`  | Transformation pipeline applied to the message list before sending to the API |
| `OutputMessageTransformer` | Transformation pipeline applied to the message list after receiving stream chunks |

---

## Full generation pipeline

```
User sends a message
    │
    ▼
ChatService.sendMessage()
    ├── Cancel the previous Job (cancel + join)
    ├── finishInterruptedPendingTools()  // Complete Tool output interrupted last time
    ├── preprocessUserInputParts()       // Apply the assistant's regex replacements to the user text
    ├── Append UIMessage(USER) to Conversation.messageNodes
    └── handleMessageComplete()
            │
            ▼
        GenerationHandler.generateText()   ← Flow<GenerationChunk>
            │  (at most maxSteps=256 iterations)
            │
            ├─ [if no pending Tool] generateInternal()
            │       ├── Build internalMessages
            │       │       ├── System message (system prompt + memory + tool.systemPrompt)
            │       │       ├── limitContext() trims history in steps by contextMessageLimit
            │       │       └── InputTransformers pipeline
            │       ├── Build TextGenerationParams
            │       └── Call the Provider
            │               ├── stream=true → providerImpl.streamText() emits chunk by chunk
            │               └── stream=false → providerImpl.generateText() returns once
            │
            ├─ On every chunk → OutputTransformers.transforms() (live)
            │                  → OutputTransformers.visualTransforms() → emit GenerationChunk
            │
            ├─ Generation done → OutputTransformers.visualTransforms()
            │           → OutputTransformers.onGenerationFinish()
            │           → set message.finishedAt
            │
            ├─ Check whether the latest message has unexecuted Tools
            │       ├── No Tool → break (generation ends)
            │       ├── Tool needing approval → set ToolApprovalState.Pending
            │       │                        → emit → break (wait for user approval)
            │       └── Approved Tool → execute the tool (see below)
            │
            ├─ Tool execution
            │       ├── Denied  → output {"error": "denied by user"}
            │       ├── Answered → use the answer the user provided directly
            │       └── Auto / Approved → toolDef.execute(args)
            │               ├── If output exceeds 32KB and shell access is available → truncate and write to a file
            │               └── CancellationException must propagate (never swallowed)
            │
            └─ Write the results back to messages → emit → continue with the next step
                    (Tool results are inlined in the ASSISTANT message parts; no TOOL-role message is created)

    ▼
onCompletion (Flow finished or cancelled)
    ├── cancelLiveUpdateNotification()
    ├── finishReasoning() on all messages (fallback)
    └── If the app isn't in the foreground → sendGenerationDoneNotification()

    ▼
onSuccess
    ├── saveConversation()
    ├── generateTitle()    (async, using titleModel)
    └── generateSuggestion() (async, using suggestionModel)
```

---

## Stage 1: User message preprocessing

**Entry**: `ChatService.preprocessUserInputParts()`

Applies `Assistant.replaceRegexes(scope=USER)` to the user's `UIMessagePart.Text`, i.e. the `AffectScope.USER`
regex replacement rules in the assistant config, used to normalize or redact input text. Non-text parts (images, documents, etc.) are not processed.

---

## Stage 2: InputMessage transformation pipeline

**When**: after `generateInternal()` builds `internalMessages`, before sending to the API.

Transformers run in order (`fold`), each receiving the previous one's output:

| Order | Transformer                   | Description                                |
|----|--------------------------------|------------------------------------------|
| 1  | `TimeReminderTransformer`      | Injects the current time/date into the system message |
| 2  | `PromptInjectionTransformer`   | Injects prompts triggered by ModeInjections and Lorebooks |
| 3  | `PlaceholderTransformer`       | Replaces `{{placeholder}}` placeholders in messages |
| 4  | `DocumentAsPromptTransformer`  | Converts document attachments to text injected into the message |
| 5  | `OcrTransformer`               | Runs OCR on image parts and attaches the result as text |
| 6  | `TemplateTransformer`          | Renders messages with the Pebble template engine (with time/date/role variables) |
| 7  | `WorkspaceReminderTransformer` | If the conversation is linked to a Workspace, injects a workspace path hint |

`PromptInjectionTransformer` supports these injection positions:

- `BEFORE_SYSTEM_PROMPT` / `AFTER_SYSTEM_PROMPT` — inserted before/after the system message
- `TOP_OF_CHAT` — inserted before the first user message
- `BOTTOM_OF_CHAT` — inserted before the last message
- `AT_DEPTH` — inserted N messages back from the newest message

---

## Stage 3: OutputMessage transformation pipeline

Called at three points:

| When                      | Method                 | Description                    |
|---------------------|------------------------|--------------------------------|
| Stream chunk arrives (live)       | `transforms()`         | Real transformation, used for internal message storage |
| Stream chunk arrives (UI display) | `visualTransforms()`   | Visual transformation that doesn't affect storage (e.g. streaming think tag conversion) |
| After generation fully ends       | `onGenerationFinish()` | Final post-processing, e.g. saving base64 images to disk |

Current output transformers:

| Transformer                         | transforms            | visualTransform               | onGenerationFinish |
|-------------------------------------|-----------------------|-------------------------------|--------------------|
| `ThinkTagTransformer`               | —                     | ✓ (`<think>` → Reasoning Part) | ✓ (final extraction) |
| `Base64ImageToLocalFileTransformer` | —                     | —                             | ✓ (base64 → local file) |
| `RegexOutputTransformer`            | ✓ (assistant regex OUTPUT replacement) | —            | —                  |

---

## Stage 4: Tool system

### Tool registration order

`handleMessageComplete()` builds the tool list in this order:

1. **Search Tools** (`createSearchTools`) — when `settings.enableWebSearch = true`
2. **Local Tools** (`localTools.getTools(assistant.localTools)`) — enabled per the assistant config:
  - `JavascriptEngine`: run JS code snippets
  - `TimeInfo`: get the current time
  - `Clipboard`: read/write the clipboard
  - `Tts`: text to speech
  - `AskUser`: ask the user a question (requires approval)
  - `ScreenTime`: get screen time
3. **Conversation Tools** (`createConversationTools`) — when `enableRecentChatsReference = true`, query past conversations
4. **Workspace Tools** (`createWorkspaceToolsIfReady`) — injected when the Workspace shell is ready, including `workspace_shell`
5. **Skill Tools** (`createSkillTools`) — the skills enabled for the assistant
6. **MCP Tools** — tools from all connected MCP servers, named `mcp__{serverName}__{toolName}`
7. **Memory Tools** (`buildMemoryTools`, built into GenerationHandler) — when `enableMemory = true`, supports adding, editing and deleting memories

### Tool approval state machine

```
Auto (default)
    │ toolDef.needsApproval() == true
    ▼
Pending ──── user action ────► Approved → execute the tool
                       ──► Denied   → return a denial error
                       ──► Answered → use the text the user provided as the result
```

Approval is triggered by `ChatService.handleToolApproval()`, which updates the state and calls `handleMessageComplete()` again; when `GenerationHandler` detects
a Tool with `canResumeExecution`, it skips generation for this round and goes straight to tool execution.

### Tool output truncation

When the Workspace shell tool is available and a tool's output exceeds **32KB**, the output is truncated:

- The first 4KB stay in the message
- The full output is written to `filesDir/tool_outputs/{toolCallId}.txt`
- The message includes a hint on how to read it with the shell

---

## Stage 5: Conversation lifecycle management

`ConversationSession` manages its memory lifecycle with an **atomic reference count**:

- `acquire()` / `release()` — called when a UI page opens/closes
- `refCount == 0 && !isGenerating` — after a 5-second idle timeout, `ChatService.removeSession()` cleans up the session
- `setJob()` — sets the generation Job; it's set back to null when done and triggers an idle check

---

## Stage 6: Background notifications

| Notification type      | Trigger          | Channel                                    |
|----------------------|----------------|--------------------------------------------|
| Live Update (ongoing)  | While generating with the app in the background | `CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID` |
| Generation complete    | Generation finished with the app in the background | `CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID`   |

The Live Update notification content updates dynamically with the current generation state:

- Running a tool → shows the tool name and an input preview
- Reasoning → shows a snippet of the reasoning
- Writing the reply → shows a snippet of the text

---

## Key file paths

```
app/src/main/java/me/ray/nexahub/
├── service/
│   ├── ChatService.kt              # Orchestration entry
│   └── ConversationSession.kt      # Conversation state container
└── data/ai/
    ├── GenerationHandler.kt        # Core generation logic
    ├── transformers/
    │   ├── Transformer.kt          # Interface definitions and extension functions
    │   ├── PromptInjectionTransformer.kt
    │   ├── TemplateTransformer.kt
    │   ├── TimeReminderTransformer.kt
    │   ├── ThinkTagTransformer.kt
    │   ├── RegexOutputTransformer.kt
    │   ├── DocumentAsPromptTransformer.kt
    │   ├── OcrTransformer.kt
    │   ├── Base64ImageToLocalFileTransformer.kt
    │   ├── PlaceholderTransformer.kt
    │   └── WorkspaceReminderTransformer.kt
    └── tools/
        ├── SearchTools.kt
        ├── ConversationTools.kt
        ├── WorkspaceTools.kt
        ├── SkillsTools.kt
        ├── MemoryTools.kt
        └── local/
            └── LocalTools.kt       # Local tool registry
```
