package me.rerere.rikkahub.ui.components.ui.permission

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Create and remember a permission state
 *
 * @param permissions the set of permission infos
 * @return the PermissionState holder
 *
 * Example:
 * ```
 * val permissionState = rememberPermissionState(
 *     permissions = setOf(
 *         PermissionInfo(
 *             permission = Manifest.permission.CAMERA,
 *             usage = { Text("Camera permission is needed to take photos") },
 *             required = true
 *         ),
 *         PermissionInfo(
 *             permission = Manifest.permission.RECORD_AUDIO,
 *             usage = { Text("Microphone permission is needed to record video") },
 *             required = false
 *         )
 *     )
 * )
 *
 * // Request permissions
 * Button(onClick = { permissionState.requestPermissions() }) {
 *     Text("Request permissions")
 * }
 *
 * // Check the permission state
 * if (permissionState.allRequiredPermissionsGranted) {
 *     Text("All required permissions granted")
 * }
 * ```
 */
@Composable
fun rememberPermissionState(
    permissions: Set<PermissionInfo>
): PermissionState {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: throw IllegalStateException("rememberPermissionState must be used in a ComponentActivity")

    // Create the permission state object
    val permissionState = remember(permissions) {
        PermissionState(permissions, context, activity)
    }

    // Launcher for multiple permission requests
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionState.handlePermissionResult(results)
    }

    // Launcher for a single permission request
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Get the last requested permission (inferred from the current rationale permission or denied permission)
        val lastRequestedPermission = permissionState.currentRationalePermissions.firstOrNull()?.permission
            ?: permissionState.deniedPermissions.firstOrNull()?.permission

        lastRequestedPermission?.let { permission ->
            permissionState.handleSinglePermissionResult(permission, granted)
        }
    }

    // Set the launchers
    LaunchedEffect(multiplePermissionLauncher, singlePermissionLauncher) {
        permissionState.setPermissionLaunchers(multiplePermissionLauncher, singlePermissionLauncher)
    }

    // Watch lifecycle changes and update the permission states
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // Force-refresh the permission states when the app returns from the background
                    // refreshPermissionStates handles permissions the user may have changed in settings
                    permissionState.refreshPermissionStates()
                }

                Lifecycle.Event.ON_RESUME -> {
                    // Also refresh on resume to keep the state current
                    permissionState.refreshPermissionStates()
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update the permission states on initialization
    LaunchedEffect(Unit) {
        permissionState.updatePermissionStates()
    }

    return permissionState
}

/**
 * Create and remember a single permission state
 *
 * @param permission the permission string
 * @param usage the permission usage explanation
 * @param required whether the permission is required
 * @return the PermissionState holder
 */
@Composable
fun rememberPermissionState(
    permission: String,
    displayName: @Composable () -> Unit,
    usage: @Composable () -> Unit,
    required: Boolean = false
): PermissionState {
    return rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = permission,
                displayName = displayName,
                usage = usage,
                required = required,
            )
        )
    )
}

@Composable
fun rememberPermissionState(
    info: PermissionInfo
): PermissionState {
    return rememberPermissionState(
        permissions = setOf(info)
    )
}
