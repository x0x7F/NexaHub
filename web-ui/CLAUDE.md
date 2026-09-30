# CLAUDE.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

web-ui is the embedded web frontend of the NexaHub project, a single-page app (SPA) built on React Router 7. The build output is copied by the `copy.ts` script into `../web/src/main/resources/static`, where the Kotlin backend's Ktor server serves it as static files.

## Technology Stack

- **React Router 7** (7.12.0): file-based routing + Vite build (SPA mode: `ssr: false`)
- **React 19** (19.2.4): UI framework
- **TypeScript** (5.9.2): strict type checking, fully aligned with the Kotlin backend types
- **Tailwind CSS v4**: utility-first CSS framework + CSS variable theming
- **shadcn/ui** (New York style): component library based on Radix UI
- **Zustand** (5.0.11): lightweight state management (composed slices pattern)
- **ky** (1.14.3): fetch-based HTTP client
- **i18next + react-i18next**: internationalization (en-US)
- **pnpm**: package manager

## Development Commands

```bash
# Dev server (HMR + API proxied to localhost:8080)
pnpm run dev

# Production build (build + copy to the backend)
pnpm run build

# Type generation and checking
pnpm run typecheck

# Format code
pnpm run fmt

# Check formatting
pnpm run fmt:check

# Production server (runs build/server/index.js)
pnpm run start
```

## Architecture

### Directory Structure

```
app/
├── routes/                   # React Router 7 file routes
│   ├── home.tsx             # / route (re-exports conversations.tsx)
│   ├── c.$id.tsx            # /c/:id route (re-exports conversations.tsx)
│   └── conversations.tsx     # Main conversation page implementation (650+ lines)
│
├── components/               # Components
│   ├── ui/                   # shadcn/ui base components (36+ components)
│   ├── message/              # Message components (chat-message, parts/)
│   ├── markdown/             # Markdown rendering (markdown.tsx + code-block.tsx)
│   ├── input/                # Input components (chat-input, model-list, pickers)
│   ├── workbench/            # Code execution workbench
│   ├── extended/             # Extended components (conversation, infinite-scroll-area)
│   ├── conversation-sidebar.tsx      # Conversation list sidebar
│   ├── conversation-search-button.tsx
│   ├── conversation-quick-jump.tsx
│   ├── custom-theme-dialog.tsx
│   └── theme-provider.tsx
│
├── stores/                   # Zustand state management (composed slices)
│   ├── app-store.ts          # Main store (composes all slices)
│   ├── settings.ts           # Exports useSettingsStore
│   ├── chat-input.ts         # Exports useChatInputStore
│   ├── slices/
│   │   ├── types.ts          # Store type definitions
│   │   ├── settings-slice.ts # Settings slice (updated from SSE)
│   │   └── chat-input-slice.ts # Chat input slice (per-conversation drafts)
│   └── hooks/
│       └── use-settings-subscription.ts # SSE subscription to the settings stream
│
├── hooks/                    # Custom React hooks
│   ├── use-conversation-list.ts  # Conversation list management (paging/live updates)
│   ├── use-current-assistant.ts  # Current assistant info
│   ├── use-current-model.ts      # Current model info
│   ├── use-mobile.ts             # Mobile device detection
│   └── use-picker-popover.ts     # Picker popover management
│
├── services/                 # API service layer
│   └── api.ts                # ky HTTP client + SSE implementation (176 lines)
│
├── types/                    # TypeScript types (aligned with Kotlin)
│   ├── core.ts               # MessageRole, TokenUsage
│   ├── parts.ts              # UIMessagePart (union of 7 part types)
│   ├── annotations.ts        # UIMessageAnnotation (UrlCitationAnnotation)
│   ├── message.ts            # UIMessage
│   ├── conversation.ts       # MessageNode, Conversation
│   ├── dto.ts                # API DTOs (ConversationDto, MessageDto, etc.)
│   ├── settings.ts           # Settings, DisplaySetting, AssistantProfile, etc.
│   ├── helpers.ts            # Type guards and helpers
│   └── index.ts              # Barrel exports
│
├── lib/                      # Utilities
│   ├── utils.ts              # cn() - Tailwind + clsx
│   ├── display.ts            # Display name formatting
│   ├── files.ts              # File URL conversion
│   └── error.ts              # Error handling
│
├── locales/                  # Translation files (organized by namespace)
│   └── en-US/
│       ├── common.json       # Common UI (sidebar, theme, etc.)
│       ├── input.json        # Input (chat, model picker)
│       ├── markdown.json     # Markdown rendering
│       ├── message.json      # Message display (message parts, tools)
│       └── page.json
│
├── assets/                   # Static assets
├── i18n.ts                   # i18next configuration
├── root.tsx                  # Root layout (calls useSettingsSubscription)
├── routes.ts                 # Route configuration (type-safe)
└── app.css                   # Global styles (Tailwind + CSS variables)
```

### Key Concepts

#### Type System (fully aligned with the Kotlin backend)

Every type under `app/types/` corresponds strictly to a Kotlin backend type:

| TypeScript type | Kotlin type | Backend location |
|----------------|------------|---------|
| `MessageRole` | `MessageRole` | `ai/src/main/java/me/ray/nexahub/ai/core/MessageRole.kt` |
| `TokenUsage` | `Usage` | `ai/src/main/java/me/ray/nexahub/ai/core/Usage.kt` |
| `UIMessagePart` | `UIMessagePart` | `ai/src/main/java/me/ray/nexahub/ai/ui/Message.kt` |
| `UIMessage` | `UIMessage` | `ai/src/main/java/me/ray/nexahub/ai/ui/Message.kt` |
| `MessageNode` | `MessageNode` | `app/src/main/java/me/ray/nexahub/data/model/Conversation.kt` |
| `Conversation` | `Conversation` | `app/src/main/java/me/ray/nexahub/data/model/Conversation.kt` |
| `ConversationDto` | `ConversationDto` | `app/src/main/java/me/ray/nexahub/web/dto/WebDto.kt` |
| `Settings` | `Settings` | `app/src/main/java/me/ray/nexahub/data/datastore/PreferencesStore.kt` |

**When a type changes, update both the frontend and the backend!**

#### Message Parts

A message is made of several `UIMessagePart`s (a union type), supporting mixed content:

```typescript
type UIMessagePart =
  | TextPart         // { type: "text", text: string }
  | ImagePart        // { type: "image", url: string }
  | VideoPart        // { type: "video", url: string }
  | AudioPart        // { type: "audio", url: string }
  | DocumentPart     // { type: "document", url, fileName, mime }
  | ReasoningPart    // { type: "reasoning", reasoning, steps[] }
  | ToolPart         // { type: "tool", toolCallId, input, output, approvalState }
```

Each part is rendered by its own component (`app/components/message/parts/`).

#### Message Branching

`MessageNode` supports conversation branching; each node holds several alternative messages:

```typescript
interface MessageNode {
  id: string;
  messages: UIMessage[];     // Alternative branches
  selectIndex: number;       // Currently selected branch
}
```

Users can:
- Regenerate a response to create a new branch
- Switch between branches
- Edit a message to fork

#### State Management (Zustand Slices)

Uses the composed slices pattern; all slices share one store:

```typescript
// app-store.ts
const useAppStore = create<AppStoreState>()((...args) => ({
  ...createSettingsSlice(...args),      // Global settings
  ...createChatInputSlice(...args),     // Chat input
}));

// Export specific store hooks
export const useSettingsStore = useAppStore;
export const useChatInputStore = useAppStore;
```

**Settings Slice**:
- Updated live from the backend SSE stream (`/api/settings/stream`)
- Holds global config such as assistants, models, providers and display settings
- Subscribed in `root.tsx` via `useSettingsSubscription`

**Chat Input Slice**:
- Keeps a separate input draft per conversation
- Supports text + media attachments (images, videos, audio, documents)
- Keeps the source parts when editing a message, for comparison

#### API Client (app/services/api.ts)

`ky`-based HTTP client supporting REST APIs and SSE streams:

```typescript
// REST API
await api.get<T>(url, options)
await api.post<T>(url, data, options)
await api.postMultipart<T>(url, formData)  // File upload
await api.put<T>(url, data)
await api.patch<T>(url, data)
await api.delete<T>(url, options)

// SSE stream (hand-written, supports event types and multi-line data)
sse<T>(url, {
  onMessage: ({ data, event, id }) => { ... },
  onError: (error) => { ... },
  onOpen: () => { ... },
  onClose: () => { ... },
}, { signal: abortController.signal })
```

**Configuration**:
- Default prefix: `/api` (proxied to `http://localhost:8080` in development)
- Timeout: 30 seconds
- Automatic error handling: converted to the `ApiError` class

**Dev proxy** (`vite.config.ts`):
```typescript
server: {
  proxy: {
    "/api": {
      target: "http://localhost:8080",
      changeOrigin: true,
    },
  },
}
```

#### Routing (React Router 7)

```typescript
// app/routes.ts - type-safe route config
[
  index("routes/home.tsx"),           // / route
  route("c/:id", "routes/c.$id.tsx")  // /c/:id route
]
```

**Notes**:
- SPA mode (`react-router.config.ts: ssr: false`)
- Route types are generated automatically (`.react-router/types/`)
- The actual UI lives in `routes/conversations.tsx`; the other route files re-export it
- Error boundaries and loading placeholders are in `root.tsx`

#### Internationalization (i18next)

**Supported languages**: en-US

**Namespaces** (`app/i18n.ts`):
Translations are split into namespaces by feature to keep files small:
- `common`: common UI (sidebar, theme, quick jump, etc.)
- `input`: input (chat input, model picker, file picker, etc.)
- `markdown`: Markdown rendering (code blocks, copy button, etc.)
- `message`: message display (message parts, tool calls, reasoning steps, etc.)
- `page`: page-level strings

**Usage**:
```typescript
import { useTranslation } from "react-i18next";

function MyComponent() {
  // Default namespace (common)
  const { t } = useTranslation();
  return <div>{t("chat.send_hint_enter")}</div>;

  // Specific namespace
  const { t: tInput } = useTranslation("input");
  return <div>{tInput("model_list.title")}</div>;

  // Namespace prefix (recommended)
  return <div>{t("input:model_list.title")}</div>;
}
```

**Adding a translation key**:
1. Pick the right namespace (common/input/markdown/message/page)
2. Add the key to the matching `en-US/*.json`
3. Access it with `t("namespace:key")` or `t("key")` (default namespace)

## Build Process

The build has two stages:

### 1. React Router Build

```bash
react-router build
# Output:
#  - build/client/  → static assets (HTML + JS + CSS)
#  - build/server/  → SSR server code (unused in SPA mode)
```

### 2. Copy to Backend

```bash
pnpm run copy.ts
# Copies build/client/ to ../web/src/main/resources/static/
# The Kotlin backend's Ktor server serves static files from that directory
```

**Full build command**: `pnpm run build` (runs both steps)

## Development Guidelines

### Component Development

**shadcn/ui components**:
- Import base components from `~/components/ui/`
- Style: New York (`components.json: style: "new-york"`)
- Icons: `import { IconName } from "lucide-react"`
- Path alias: `~` → the `app/` directory

**Component pattern**:
```typescript
import type { ComponentProps } from "react";

interface MyComponentProps extends ComponentProps<"div"> {
  value: string;
  onChange: (value: string) => void;
}

export function MyComponent({ value, onChange, ...props }: MyComponentProps) {
  return <div {...props}>{value}</div>;
}
```

### State Management Patterns

**Global state (Zustand)**:
```typescript
// Read state
const settings = useSettingsStore((state) => state.settings);
const currentModelId = useSettingsStore((state) => state.settings?.currentModelId);

// Update state
const setSettings = useSettingsStore((state) => state.setSettings);
const setText = useChatInputStore((state) => state.setText);

// Use selectors to avoid unnecessary re-renders
const currentAssistant = useSettingsStore(
  (state) => state.settings?.assistants.find(a => a.id === state.settings?.currentAssistantId)
);
```

**Local state**:
```typescript
// Use useState for UI-specific state
const [isOpen, setIsOpen] = useState(false);
const [selectedIndex, setSelectedIndex] = useState(0);
```

### Markdown Rendering

`app/components/markdown/markdown.tsx` provides enhanced Markdown rendering:

**Features**:
- **LaTeX math**: inline `\(...\)` and block `\[...\]` (converted to `$...$` and `$$...$$`)
- **GFM support**: tables, strikethrough, task lists, etc.
- **Code highlighting**: Shiki-based, with a copy button
- **`<think>` tags**: rendered as blockquotes
- **Citation links**: handles the `[citation,domain](id)` format
- **Theming**: switches automatically between light and dark themes

**Preprocessing**:
1. Find all code block positions (to avoid replacing inside code blocks)
2. Replace LaTeX syntax and `<think>` tags
3. Pass to `react-markdown` + rehype plugins

### Message Component System

Message rendering uses a dispatcher pattern:

```
ChatMessage (container)
  └── MessageParts (iterates the parts array)
      └── MessagePart (dispatcher - by part.type)
          ├── TextPart
          ├── ImagePart
          ├── VideoPart
          ├── AudioPart
          ├── DocumentPart
          ├── ReasoningPart (expandable reasoning steps)
          └── ToolPart (tool call display and approval)
```

**Adding a new part type**:
1. Add the type definition in `app/types/parts.ts`
2. Add it to the Kotlin backend too (`ai/.../ui/Message.kt`)
3. Create the component in `app/components/message/parts/`
4. Add the dispatch logic in `app/components/message/message-part.tsx`
5. Add a type guard in `app/types/helpers.ts`

### File URL Handling

`app/lib/files.ts` provides URL conversion:

```typescript
resolveFileUrl(url: string): string
// Handles:
// - data: URL (base64) → returned as-is
// - http/https → returned as-is
// - file:// (Android) → /api/files/path/...
// - relative path → /api/files/path/...
```

Used by `ImagePart`, `VideoPart`, `AudioPart` and `DocumentPart` to resolve URLs.

### Type Safety

**Strict type checking**:
```bash
pnpm run typecheck  # React Router type generation + tsc
```

**Keeping in sync with Kotlin**:
- Any change to a type under `app/types/` must also be made in the Kotlin backend
- Use the type mapping table above to find the matching Kotlin file
- Run `pnpm run typecheck` to make sure the frontend types are correct

### Data Flow and Real-time Communication

#### App startup

```
root.tsx renders
  ↓
useSettingsSubscription() subscribes to /api/settings/stream (SSE)
  ↓
Backend pushes the Settings object
  ↓
useSettingsStore.setSettings() updates global state
  ↓
All components update reactively (assistants, models, providers, etc.)
```

#### Loading a conversation

```
User selects/creates a conversation
  ↓
GET /api/conversations/:id (initial snapshot)
  ↓
Returns ConversationDto (full message tree)
  ↓
Opens an SSE connection: GET /api/conversations/:id/stream
  ↓
Backend pushes events:
  - ConversationSnapshotEventDto (large update - full snapshot)
  - ConversationNodeUpdateEventDto (incremental update - node changes)
  ↓
UI renders messages and generation progress live
```

#### Sending a message

```
User clicks send
  ↓
useChatInputStore.getSubmitParts(conversationId)  // Build the message parts
  ↓
POST /api/conversations/:id/send
  body: { parts: UIMessagePart[] }
  ↓
Backend processes it and starts generation
  ↓
SSE stream pushes:
  - node_update events (per token or part)
  - conversation.isGenerating = true → false
  ↓
UI shows the streamed response live
  ↓
After generation useChatInputStore.clearDraft(conversationId)
```

### Custom Hooks Patterns

#### useConversationList

Conversation list hook with paging and live updates:

```typescript
const {
  conversations,      // Conversation list
  activeId,           // Currently selected conversation ID
  setActiveId,        // Set the current conversation
  loading,            // Loading state
  error,              // Error message
  hasMore,            // Whether there are more
  loadMore,           // Load more
  refreshList,        // Refresh the list
  updateConversationSummary,  // Incremental update
} = useConversationList({
  currentAssistantId,
  routeId,            // Conversation ID of the current route
  autoSelectFirst: true,
  pageSize: 30,
});
```

**Notes**:
- Automatically refreshes when the assistant changes
- Supports infinite-scroll paging
- Incremental updates driven by SSE events
- Sorting: pinned conversations first, the rest by update time descending

#### useCurrentAssistant / useCurrentModel

Extract the current assistant and model from the Settings store:

```typescript
const { currentAssistant, currentAssistantId } = useCurrentAssistant();
const { currentModel, currentProvider } = useCurrentModel();
```

## Integration with Kotlin Backend

### Static File Serving

```
Build output flow:
web-ui/build/client/
├── index.html
├── assets/*.js
└── assets/*.css
    ↓ (copy.ts)
../web/src/main/resources/static/
    ↓ (Ktor static file route)
User visits http://localhost:8080/
```

### API Endpoints

All API endpoints are defined in the Kotlin code of the `web` module:
- `GET /api/settings/stream` - settings SSE stream
- `GET /api/conversations` - conversation list
- `GET /api/conversations/:id` - get a conversation
- `GET /api/conversations/:id/stream` - conversation SSE stream
- `POST /api/conversations/:id/send` - send a message
- `POST /api/files/upload` - file upload
- `GET /api/files/path/*` - file access

### Type Synchronization Checklist

When changing types:

- [ ] Update the TypeScript types (`app/types/`)
- [ ] Update the matching Kotlin types (see the mapping table above)
- [ ] Run `pnpm run typecheck` to check the frontend types
- [ ] Run type checks on the Kotlin side
- [ ] Test serialization/deserialization between frontend and backend
- [ ] Update related type guards (`app/types/helpers.ts`)

## Performance Optimization

- **Code splitting**: React Router 7 splits by route automatically
- **Tree shaking**: Tailwind v4 + Vite remove unused styles and code
- **Selective subscriptions**: Zustand selectors avoid unnecessary re-renders
- **Virtual scrolling**: the conversation list uses `react-infinite-scroll-component`
- **SSE streaming updates**: fewer API polls, data pushed in real time

## Troubleshooting

### Dev server fails to start

Check whether port 5173 is in use:
```bash
lsof -ti:5173 | xargs kill -9  # Kill the process using it
pnpm run dev
```

### API requests fail (development)

Make sure the Kotlin backend is running on port 8080:
```bash
# In the Kotlin project directory
./gradlew :web:run
```

### Type errors

Run type generation and checking:
```bash
pnpm run typecheck
# Check the generated types under .react-router/types/
```

### Build fails

Clear caches and reinstall dependencies:
```bash
rm -rf node_modules .react-router build
pnpm install
pnpm run build
```
