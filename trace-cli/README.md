# trace-cli

Generates replayable `events.jsonl` files for the `ai` module from real provider SSE responses. The CLI only records
the `id`, `event`, `data` and `retryMillis` after SSE framing; it never records request headers or API keys.

Supported providers:

- `openai-responses`
- `openai-chat`
- `claude`
- `google-generateContent`
- `google-interactions`

## Installation

```bash
cd trace-cli
bun install
cp .env.example .env
```

Fill in the keys you need in `.env`:

```dotenv
OPENAI_API_KEY=...
ANTHROPIC_API_KEY=...
GEMINI_API_KEY=...
DEEPSEEK_API_KEY=...
```

`.env` is excluded by the subproject's `.gitignore`. A single trace can also use `apiKeyEnv`
to name a different environment variable.

## Generating traces

Edit the providers, models and request bodies to record directly in `traces.yml`.

List the cases after config resolution:

```bash
bun run trace -- traces.yml --list
```

Inspect the redacted actual request without touching the network:

```bash
bun run trace -- traces.yml --case openai-responses-tool --dry-run
```

Generate one or all traces:

```bash
bun run trace -- traces.yml --case openai-responses-tool
bun run trace -- traces.yml
```

Existing files are not overwritten by default. When you do want to re-record, use:

```bash
bun run trace -- traces.yml --case openai-responses-tool --force
```

The CLI writes to a temp file first and atomically replaces the target `events.jsonl` only after the request and SSE parsing both succeed.

## YAML format

```yaml
version: 1
defaults:
  outputRoot: ../ai/src/test/resources/stream-traces/generated
  timeoutMs: 120000
  headers: {}

traces:
  - name: responses-tool
    provider: openai-responses
    model: gpt-5.6
    apiKeyEnv: OPENAI_API_KEY
    # Optional: override the provider's default auth, e.g. OpenRouter Anthropic Messages uses Bearer.
    auth:
      header: Authorization
      scheme: Bearer
    baseUrl: https://api.openai.com/v1
    # endpoint is optional; it can be a path relative to baseUrl or a full URL.
    endpoint: /responses
    # output is optional; defaults to outputRoot/provider/name/events.jsonl.
    output: ../ai/src/test/resources/stream-traces/openai-responses/responses-tool/events.jsonl
    headers: {}
    timeoutMs: 120000
    body:
      store: false
      input:
        - role: user
          content: Call the weather tool for Paris.
      tools:
        - type: function
          name: weather
          description: Get weather for a city.
          parameters:
            type: object
            properties:
              city:
                type: string
            required: [city]
            additionalProperties: false
```

The CLI automatically adds the auth headers each provider needs and forces `stream: true` for OpenAI, Claude and Google Interactions
requests. `google-generateContent` uses
`:streamGenerateContent?alt=sse`, and the model name is only used in the URL; `google-interactions` uses
`/interactions`, and the model name is written into the request body. Interactions also supports omitting `model` and specifying the agent
directly in `body.agent`.

After generating, you still need to create the matching `expected.json` by hand and add the directory to `StreamTraceReplayTest`.
