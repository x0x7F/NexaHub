# Stream trace fixtures

Each directory contains two files:

- `events.jsonl`: one serialized `SseEvent` per line, recorded after the HTTP client has done SSE framing and before the Provider
  decodes it. Don't record request headers such as Authorization.
- `expected.json`: a stable semantic snapshot after `StreamChunkDecoder` and `StreamChunkHandler` have processed the events.
  Volatile fields such as timestamps and random message IDs are not included in the snapshot.

When adding or updating a trace, keep the `id`, `event` and `data` returned by the Provider, and manually review
`expected.json` before the first commit. Regular unit tests only replay these files offline and never touch the network.

After recording a new trace, regenerate the semantic snapshot by running this command in the `ai` module directory:

```bash
UPDATE_STREAM_TRACE_SNAPSHOTS=true ../gradlew testDebugUnitTest \
  --tests me.ray.nexahub.ai.provider.stream.StreamTraceReplayTest
```

Snapshots keep tool call IDs, full metadata, thinking text, tool names and arguments, and token usage. Image data stays in
`events.jsonl`; the snapshot only records the MIME type, decoded byte count and SHA-256, to avoid storing large Base64 blobs twice.
Gemini image traces also compare the Provider's raw signature with the decoded metadata byte by byte, verifying the signature
isn't modified during stream conversion.
