package me.ray.nexahub.tts.controller

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.ray.nexahub.tts.model.PlaybackState
import me.ray.nexahub.tts.model.PlaybackStatus
import me.ray.nexahub.tts.model.TTSResponse
import me.ray.nexahub.tts.provider.TTSManager
import me.ray.nexahub.tts.provider.TTSProviderException
import me.ray.nexahub.tts.provider.TTSProviderSetting
import java.io.IOException
import java.util.UUID

private const val TAG = "TtsController"
private const val MAX_SYNTHESIS_ATTEMPTS = 3
private const val SYNTHESIS_RETRY_BASE_DELAY_MS = 500L

/**
 * TTS controller (refactored)
 * - Handles text chunking, prefetching synthesis, queued playback and status reporting
 * - The public API is compatible with the original
 */
class TtsController(
    context: Context,
    private val ttsManager: TTSManager
) {
    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Components
    private val chunker = TextChunker(maxChunkLength = 160)
    private val synthesizer = TtsSynthesizer(ttsManager)
    private val audio = AudioPlayer(context)

    // Provider & jobs
    private var currentProvider: TTSProviderSetting? = null
    private var workerJob: Job? = null
    private var isPaused = false

    // Queue and cache (based on stable IDs)
    private val queue: java.util.concurrent.ConcurrentLinkedQueue<TtsChunk> = java.util.concurrent.ConcurrentLinkedQueue()
    private val allChunks: MutableList<TtsChunk> = mutableListOf()
    private val cache = java.util.concurrent.ConcurrentHashMap<UUID, kotlinx.coroutines.Deferred<TTSResponse>>()

    // Behavior parameters
    private val chunkDelayMs = 120L
    private val prefetchCount = 2

    // State flow (keeps the StateFlow compatible with the old version)
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentChunk = MutableStateFlow(0)
    val currentChunk: StateFlow<Int> = _currentChunk.asStateFlow()

    private val _totalChunks = MutableStateFlow(0)
    val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

    // Unified playback state (audio playback + chunk progress combined)
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        // Sync the underlying player state into the unified state and add chunk info
        scope.launch {
            audio.playbackState.collectLatest { audioState ->
                _playbackState.update {
                    audioState.copy(
                        currentChunkIndex = _currentChunk.value,
                        totalChunks = _totalChunks.value,
                        status = if (!_isAvailable.value) PlaybackStatus.Idle else audioState.status
                    )
                }
            }
        }
    }

    /** Select/deselect a Provider */
    fun setProvider(provider: TTSProviderSetting?) {
        currentProvider = provider
        _isAvailable.update { provider != null }
        if (provider == null) stop()
    }

    /**
     * Speak text
     * - flush=true: clear the current progress and start over
     * - flush=false: continue the queue and append the text
     */
    fun speak(text: String, flush: Boolean = true) {
        if (text.isBlank()) return
        val provider = currentProvider
        if (provider == null) {
            _error.update { "No TTS provider selected" }
            return
        }

        val newChunks = chunker.split(text)
        if (newChunks.isEmpty()) return

        if (flush) {
            internalReset()
            allChunks.addAll(newChunks)
            queue.addAll(newChunks)
            _currentChunk.update { 0 }
        } else {
            // When appending, remap the index to keep the global order
            val startIndex = (allChunks.lastOrNull()?.index ?: -1) + 1
            val remapped = newChunks.mapIndexed { i, c -> c.copy(index = startIndex + i) }
            allChunks.addAll(remapped)
            queue.addAll(remapped)
        }
        _totalChunks.update { queue.size }
        _error.update { null }

        _playbackState.update {
            it.copy(
                currentChunkIndex = _currentChunk.value,
                totalChunks = _totalChunks.value,
                status = PlaybackStatus.Buffering
            )
        }

        if (workerJob?.isActive != true) startWorker()
    }

    private fun internalReset() {
        // Reset current session while keeping provider availability
        workerJob?.cancel()
        audio.stop()
        audio.clear()
        isPaused = false
        queue.clear()
        allChunks.clear()
        cache.values.forEach { it.cancel(CancellationException("Reset")) }
        cache.clear()
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        _error.update { null }
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    /** Pause playback (keeping progress) */
    fun pause() {
        isPaused = true
        audio.pause()
        _playbackState.update { it.copy(status = PlaybackStatus.Paused) }
    }

    /** Resume playback */
    fun resume() {
        isPaused = false
        audio.resume()
        _playbackState.update { it.copy(status = PlaybackStatus.Playing) }
    }

    /** Fast-forward the current audio */
    fun fastForward(ms: Long = 5_000) {
        audio.seekBy(ms)
    }

    /** Set the playback speed */
    fun setSpeed(speed: Float) {
        audio.setSpeed(speed)
    }

    /** Skip the next chunk (without interrupting the one currently playing) */
    fun skipNext() {
        if (queue.isNotEmpty()) {
            queue.poll()
            _totalChunks.update { queue.size }
        }
    }

    /** Stop and clear the state */
    fun stop() {
        workerJob?.cancel()
        audio.stop()
        audio.clear()
        isPaused = false
        queue.clear()
        allChunks.clear()
        cache.values.forEach { it.cancel(CancellationException("Stopped")) }
        cache.clear()
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        _playbackState.update { PlaybackState(status = PlaybackStatus.Idle) }
    }

    /** Release resources */
    fun dispose() {
        stop()
        scope.cancel()
        audio.release()
    }

    // region Internal: playback scheduling
    private fun startWorker() {
        val provider = currentProvider
        if (provider == null) {
            _error.update { "No TTS provider selected" }
            return
        }

        workerJob = scope.launch {
            _isSpeaking.update { true }
            var processedCount = _currentChunk.value
            try {
                while (isActive) {
                    if (isPaused) {
                        delay(80)
                        continue
                    }

                    val chunk = queue.poll() ?: break

                    // Update the state (1-based)
                    _currentChunk.update { processedCount + 1 }
                    _totalChunks.update { queue.size + 1 }
                    _playbackState.update {
                        it.copy(
                            currentChunkIndex = _currentChunk.value,
                            totalChunks = _totalChunks.value
                        )
                    }

                    // Only prefetch a fixed window after the current chunk
                    prefetchNextChunks(chunk.index)

                    val response = try {
                        awaitOrCreate(chunk, provider)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Synthesis error", e)
                        _error.update { e.message ?: "TTS synthesis error" }
                        processedCount++
                        continue
                    }

                    // Play
                    try {
                        audio.play(response)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Playback error", e)
                        _error.update { e.message ?: "Audio playback error" }
                    }

                    if (queue.isNotEmpty()) delay(chunkDelayMs)

                    processedCount++
                }
            } finally {
                _isSpeaking.update { false }
                if (queue.isEmpty()) {
                    _playbackState.update { it.copy(status = PlaybackStatus.Ended) }
                }
            }
        }
    }

    private fun prefetchNextChunks(currentIndex: Int) {
        val provider = currentProvider ?: return
        val begin = currentIndex + 1
        val endExclusive = (begin + prefetchCount).coerceAtMost(allChunks.size)
        if (begin >= endExclusive) return

        for (i in begin until endExclusive) {
            val chunk = allChunks.getOrNull(i) ?: continue
            getOrCreateSynthesis(chunk, provider)
        }
    }

    private suspend fun awaitOrCreate(chunk: TtsChunk, provider: TTSProviderSetting): TTSResponse {
        val deferred = getOrCreateSynthesis(chunk, provider)
        return try {
            deferred.await()
        } catch (e: Exception) {
            // Avoid reusing a Deferred that has already failed or been cancelled
            cache.remove(chunk.id, deferred)
            throw e
        }
    }

    private fun getOrCreateSynthesis(
        chunk: TtsChunk,
        provider: TTSProviderSetting
    ): kotlinx.coroutines.Deferred<TTSResponse> {
        return cache.computeIfAbsent(chunk.id) {
            scope.async(Dispatchers.IO) {
                synthesizeWithRetry(provider, chunk)
            }
        }
    }

    private suspend fun synthesizeWithRetry(
        provider: TTSProviderSetting,
        chunk: TtsChunk
    ): TTSResponse {
        var attempt = 1
        while (true) {
            try {
                return synthesizer.synthesize(provider, chunk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.isRetryableSynthesisError() || attempt >= MAX_SYNTHESIS_ATTEMPTS) throw e

                val retryDelayMs = SYNTHESIS_RETRY_BASE_DELAY_MS * (1L shl (attempt - 1))
                Log.w(
                    TAG,
                    "Synthesis attempt $attempt/$MAX_SYNTHESIS_ATTEMPTS failed for chunk ${chunk.index}; " +
                        "retrying in ${retryDelayMs}ms",
                    e
                )
                delay(retryDelayMs)
                attempt++
            }
        }
    }
    // endregion
}

private fun Exception.isRetryableSynthesisError(): Boolean {
    return this is IOException || this is TTSProviderException && isRetryable
}
