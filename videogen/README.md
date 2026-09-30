# videogen abstraction layer

`videogen` unifies the async video generation protocols of Alibaba Cloud Bailian Wan, Volcengine Ark Seedance and MiniMax H3.

## Design boundaries

All three APIs follow the same task lifecycle:

1. Submit a generation request and get a task ID;
2. Poll the task status;
3. On success, get a time-limited video URL, which the upper layer downloads or re-stores in time.

The module only adapts vendor protocols; it does not persist tasks, download videos, upload local assets or manage UI state.

The shared models are in `model/VideoGeneration.kt`:

- `VideoGenerationRequest`: common fields such as the prompt, multimodal inputs, resolution, aspect ratio, duration, audio and watermark;
- `VideoGenerationInput`: first frame, last frame, reference images, reference videos, reference audio, files, web pages and raw vendor inputs;
- `VideoGenerationTask`: unified queued, running, succeeded, failed, cancelled and expired states and results;
- `extraParameters` / `Raw`: carry vendor-specific fields from fast-moving models, avoiding frequent changes to the public API.

`VideoGenerationProvider` only exposes `create` and `query`. `VideoGenerationManager.watch` provides a cancellable polling
Flow that the UI or a task repository can subscribe to directly.

## Capability differences

| Capability | Alibaba Wan 3.0 | Volcengine Seedance 2.0 | MiniMax H3 |
|-----------|-----------------|-----------------|----------------|
| Text-to-video | Yes | Yes | Yes, and the prompt is required |
| First/last frame | Yes | Yes | Yes |
| Reference image/video/audio | Yes | Yes | Yes |
| File/web page input | Yes | No | No |
| Smart duration `-1` | Yes | Some models | No |
| Per-request callback URL | No, uses the account-level async callback config | Yes | Yes |
| Audio output toggle | Yes | Some models | Not exposed by the API |

Duration, resolution, asset count and format limits change quickly across models, so the common layer doesn't hard-code a model capability table; adapters only do protocol-level validation, and the server remains the final authority on valid model parameters.

## Usage example

```kotlin
val manager = VideoGenerationManager(okHttpClient)
val setting = VideoGenerationProviderSetting.MiniMax(apiKey = apiKey)

val submitted = manager.create(
    setting = setting,
    request = VideoGenerationRequest(
        prompt = "A vintage sports car slowly drives down a neon-lit street in a rainy city at night",
        resolution = "2K",
        aspectRatio = "16:9",
        durationSeconds = 5,
    ),
).getOrThrow()

manager.watch(setting, submitted.id).collect { task ->
    // Persist the task or update the UI; the Flow ends automatically after a terminal state.
}
```

## Official docs

- [Alibaba Cloud Bailian Wan 3.0](https://help.aliyun.com/zh/model-studio/wan3-video-generation-api-reference)
- [Volcengine Ark video generation API](https://www.volcengine.com/docs/82379/1520757)
- [MiniMax H3 create video task](https://platform.minimaxi.com/docs/api-reference/video-generation-v2-create)
- [MiniMax H3 query task](https://platform.minimaxi.com/docs/api-reference/video-generation-v2-query)
