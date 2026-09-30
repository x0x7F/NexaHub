package me.ray.nexahub.ui.hooks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Create a debounced function wrapper
 *
 * @param delayMillis the delay (milliseconds)
 * @param function the function to run
 * @return the debounced function
 */
@Composable
fun <T> useDebounce(
    delayMillis: Long = 300,
    function: (T) -> Unit
): (T) -> Unit {
    val scope = rememberCoroutineScope()
    val debounceJob = remember { mutableStateOf<Job?>(null) }

    return remember {
        { param: T ->
            debounceJob.value?.cancel()
            debounceJob.value = scope.launch {
                delay(delayMillis)
                function(param)
            }
        }
    }
}

/**
 * Create a throttled function wrapper
 *
 * @param intervalMillis the interval (milliseconds)
 * @param function the function to run
 * @return the throttled function
 */
@Composable
fun <T> useThrottle(
    intervalMillis: Long = 300,
    function: (T) -> Unit
): (T) -> Unit {
    val scope = rememberCoroutineScope()
    val isThrottling = remember { AtomicBoolean(false) }
    val latestParam = remember { mutableStateOf<T?>(null) }

    return remember {
        { param: T ->
            latestParam.value = param

            if (!isThrottling.getAndSet(true)) {
                function(param)

                scope.launch {
                    delay(intervalMillis)
                    isThrottling.set(false)

                    // If new arguments arrived during the throttle window, run once more after it ends
                    latestParam.value?.let { latestValue ->
                        // Reset the arguments
                        latestParam.value = null
                        // Call the throttled function again with the latest arguments
                        function(latestValue)
                    }
                }
            }
        }
    }
}
