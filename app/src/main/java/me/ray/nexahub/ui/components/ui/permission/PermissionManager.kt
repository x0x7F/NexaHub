package me.ray.nexahub.ui.components.ui.permission

import androidx.compose.runtime.Composable

/**
 * Permission manager component
 * Automatically shows and hides the permission request dialog
 *
 * Usage:
 * ```
 * val permissionState = rememberPermissionState(permissions)
 *
 * PermissionManager(permissionState = permissionState) {
 *     // Your UI content
 *     YourContent()
 * }
 * ```
 */
@Composable
fun PermissionManager(
    permissionState: PermissionState,
    content: @Composable () -> Unit = {},
) {
    // Show the permission rationale dialog
    if (permissionState.showRationaleDialog && permissionState.currentRationalePermissions.isNotEmpty()) {
        PermissionRationaleDialog(
            permissions = permissionState.currentRationalePermissions,
            permanentlyDeniedPermissions = permissionState.permanentlyDeniedPermissions,
            onProceed = {
                permissionState.proceedFromRationale()
            },
            onCancel = {
                permissionState.cancelPermissionRequest()
            },
            onOpenSettings = {
                permissionState.openAppSettings()
                permissionState.cancelPermissionRequest()
            }
        )
    }

    // Main content
    content()
}
