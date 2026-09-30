package me.rerere.rikkahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Remembers the current Lifecycle.State of the application's LifecycleOwner
 * (usually the Activity or Fragment hosting the Compose UI).
 *
 * The returned State object will update whenever the lifecycle state changes
 * (e.g., from STARTED to RESUMED).
 *
 * @return A State object holding the current Lifecycle.State.
 */
@Composable
fun rememberAppLifecycleState(): State<Lifecycle.State> {
    // 1. Get the current LifecycleOwner.
    // LocalLifecycleOwner is a CompositionLocal that provides the LifecycleOwner of the current composition.
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    // 2. Use remember to create a MutableState that stores the current lifecycle state.
    // Initialize it to the current lifecycle state.
    val lifecycleState = remember { mutableStateOf(lifecycleOwner.lifecycle.currentState) }
    // 3. Use DisposableEffect to add and remove a LifecycleObserver.
    // DisposableEffect suits side effects that need setup and cleanup when a Composable enters or leaves the composition.
    // The key is lifecycleOwner.lifecycle, so the effect is handled correctly if the lifecycle object itself changes (rare).
    DisposableEffect(lifecycleOwner.lifecycle) {
        // Create a LifecycleEventObserver.
        // onStateChanged is called whenever any lifecycle event happens.
        val observer = LifecycleEventObserver { _, _ ->
            // After a lifecycle event, update lifecycleState to the current lifecycle state.
            // This recomposes the Composables that use lifecycleState.
            lifecycleState.value = lifecycleOwner.lifecycle.currentState
        }
        // Add the observer to the lifecycle.
        lifecycleOwner.lifecycle.addObserver(observer)
        // The onDispose block runs when the Composable leaves the composition, to clean up resources.
        onDispose {
            // Remove the observer to avoid memory leaks.
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // 4. Return the State object holding the lifecycle state.
    return lifecycleState
}
