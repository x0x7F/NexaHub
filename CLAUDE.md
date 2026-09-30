# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

The imported `AGENTS.md` covers the project overview, module list, core concepts (Assistant, Conversation,
UIMessage, MessageNode, Message Transformers) and i18n rules. This file adds only what isn't there.

## Build prerequisites

- Git submodule: `material3/material-color-utilities` — run `git submodule update --init --recursive`.
- The `web` module's `preBuild` depends on `buildWebUi`, which runs `pnpm run build` in `web-ui/` (it does
  **not** install deps). Run `pnpm install` in `web-ui/` first, with Node + pnpm on PATH, or any Android build fails.
- JDK 17 (CI uses Temurin 17). Release builds read signing config from `local.properties`.

## Commands

```bash
./gradlew assembleDebug                          # debug APK
./gradlew test                                   # all JVM unit tests
./gradlew :ai:testDebugUnitTest                  # one module's tests
./gradlew :ai:testDebugUnitTest --tests "me.ray.nexahub.ai.provider.providers.claude.ClaudeRequestMessageTest"
./gradlew :app:testDebugUnitTest --tests "*SomeTest.someMethod"       # single test method (wildcards ok)
./gradlew lint
```

Sub-projects with their own tooling and guidance (read their CLAUDE.md/README before editing):

- `web-ui/` — React Router 7 SPA served by the `web` module's Ktor server (`web-ui/CLAUDE.md`).
  `pnpm run dev | build | typecheck | lint | fmt`. Build output is copied into `web/src/main/resources/static`.
- `locale-tui/` — Python (uv) TUI/CLI for translating `strings.xml` (`locale-tui/CLAUDE.md`; use the
  `locale-tui-localization` skill).
- `trace-cli/` — Bun CLI that records real provider SSE streams into
  `ai/src/test/resources/stream-traces/**/events.jsonl`, which `ai` module tests replay.

Additional Gradle modules not listed in AGENTS.md: `videogen` (async video-generation provider adapters),
`oauth`, `app:baselineprofile`, and `build-logic` (convention plugins for Android library modules).

## Architecture

- **Stack**: Kotlin, Jetpack Compose, Koin DI (`app/.../di/*Module.kt`), Room, DataStore, Navigation 3
  (`NavDisplay` in `RouteActivity.kt`), OkHttp, kotlinx.serialization. Package root: `me.ray.nexahub`.
  Library modules use `me.ray.nexahub.<module>` (e.g. `me.ray.nexahub.ai`, `.search`, `.tts`/`.asr` in `speech`).
- **Legacy `me.rerere.*` strings are intentional**: the string literals in `data/datastore/migration/` and
  `data/db/migrations/MigrationUtils.kt`, and the `@SerialName`s on `data/model/Avatar.kt`, match class names that
  the original RikkaHub app persisted. Don't rename them, or old settings and backups stop decoding. The
  `me.rerere.hugeicons` imports come from an external library and must also stay.
- **Provider layer (`ai`)**: `ProviderManager` selects a `Provider` implementation (`provider/providers/{openai,
  google,claude}`) from a `ProviderSetting`; each converts `UIMessage`s to the vendor wire format and parses
  streaming responses back into `UIMessage` chunks. `registry/ModelRegistry` + `ModelDsl` describe known models'
  capabilities.
- **Generation flow (`app`)**: `ChatService` → `ConversationSessionManager`/`ConversationSession` (per-conversation
  state, jobs, `MessageQueue` for queued user messages) → `data/ai/GenerationLoop`, which runs input transformers,
  calls the provider, streams chunks, executes tool calls (built by `data/ai/tools/ChatToolFactory`: search,
  memory, workspace, skills, MCP from `data/ai/mcp`, etc.) and loops until no more tool calls, then runs output
  transformers. `ChatGenerationForegroundService` keeps generation alive in the background.
- **Settings**: a single `Settings` object persisted as JSON in DataStore via `SettingsStore`
  (`data/datastore/PreferencesStore.kt`). Schema changes to persisted settings need a migration in
  `data/datastore/migration/`.
- **Database**: Room `AppDatabase` (exported schemas in `app/schemas/`). Bumping the version requires a
  migration in `data/db/migrations/` (or an auto-migration) and committing the new schema JSON.
- **Web access**: `WebServerService` starts the `web` module's Ktor server, exposing app APIs
  (`app/.../web/`) consumed by `web-ui`; keep TypeScript types in `web-ui` aligned with the Kotlin models.
