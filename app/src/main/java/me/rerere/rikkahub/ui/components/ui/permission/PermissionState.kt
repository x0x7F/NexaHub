package me.rerere.rikkahub.ui.components.ui.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

/**
 * Permission state holder
 */
@Stable
class PermissionState internal constructor(
    private val permissions: Set<PermissionInfo>,
    private val context: Context,
    private val activity: ComponentActivity
) {
    // Permission state map
    private val _permissionStates = mutableStateMapOf<String, PermissionStatus>()
    val permissionStates: Map<String, PermissionStatus> = _permissionStates

    // Whether to show the permission rationale dialog
    var showRationaleDialog by mutableStateOf(false)
        private set

    // Permissions whose rationale currently needs to be shown
    var currentRationalePermissions by mutableStateOf<List<PermissionInfo>>(emptyList())
        private set

    // Permission request launcher
    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    // Single permission request launcher
    private var singlePermissionLauncher: ActivityResultLauncher<String>? = null

    init {
        // Initialize the permission states
        updatePermissionStates()
    }

    /**
     * Set the permission request launchers
     */
    internal fun setPermissionLaunchers(
        multiplePermissionLauncher: ActivityResultLauncher<Array<String>>,
        singlePermissionLauncher: ActivityResultLauncher<String>
    ) {
        this.permissionLauncher = multiplePermissionLauncher
        this.singlePermissionLauncher = singlePermissionLauncher
    }

    /**
     * Update all permission states
     */
    fun updatePermissionStates() {
        permissions.forEach { permissionInfo ->
            val oldStatus = _permissionStates[permissionInfo.permission]
            val newStatus = getPermissionStatus(permissionInfo.permission, oldStatus)
            _permissionStates[permissionInfo.permission] = newStatus
        }
    }

    /**
     * Get the state of a single permission
     */
    private fun getPermissionStatus(permission: String, oldStatus: PermissionStatus? = null): PermissionStatus {
        return when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
                PermissionStatus.Granted
            }
            activity.shouldShowRequestPermissionRationale(permission) -> {
                PermissionStatus.Denied
            }
            // If it was denied before (including permanently) and now shows no rationale and isn't granted, it's permanently denied
            (oldStatus == PermissionStatus.Denied || oldStatus == PermissionStatus.DeniedPermanently) -> {
                PermissionStatus.DeniedPermanently
            }
            else -> {
                PermissionStatus.NotRequested
            }
        }
    }

    /**
     * Check whether all permissions are granted
     */
    val allPermissionsGranted: Boolean
        get() = permissions.all { permissionStates[it.permission] == PermissionStatus.Granted }

    /**
     * Check whether all required permissions are granted
     */
    val allRequiredPermissionsGranted: Boolean
        get() = permissions.filter { it.required }.all { permissionStates[it.permission] == PermissionStatus.Granted }

    /**
     * Get the permissions that aren't granted
     */
    val deniedPermissions: List<PermissionInfo>
        get() = permissions.filter { permissionStates[it.permission] != PermissionStatus.Granted }

    /**
     * Get the permissions whose rationale needs to be shown (including permanently denied ones)
     */
    private val permissionsNeedRationale: List<PermissionInfo>
        get() = permissions.filter {
            val status = permissionStates[it.permission]
            status == PermissionStatus.Denied && activity.shouldShowRequestPermissionRationale(it.permission) ||
            status == PermissionStatus.DeniedPermanently
        }

    /**
     * Get the permanently denied permissions
     */
    val permanentlyDeniedPermissions: List<PermissionInfo>
        get() = permissions.filter { permissionStates[it.permission] == PermissionStatus.DeniedPermanently }

    /**
     * Request all permissions that aren't granted
     */
    fun requestPermissions() {
        val deniedPerms = deniedPermissions
        if (deniedPerms.isEmpty()) return

        val rationalePerms = permissionsNeedRationale
        if (rationalePerms.isNotEmpty()) {
            // Show the permission rationale dialog
            currentRationalePermissions = rationalePerms
            showRationaleDialog = true
        } else {
            // Request the permissions directly
            launchPermissionRequest(deniedPerms)
        }
    }

    /**
     * Request specific permissions
     */
    fun requestPermission(permission: String) {
        val permissionInfo = permissions.find { it.permission == permission } ?: return
        val status = permissionStates[permission] ?: return

        if (status == PermissionStatus.Granted) return

        when (status) {
            PermissionStatus.Denied -> {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    // Show the permission rationale dialog
                    currentRationalePermissions = listOf(permissionInfo)
                    showRationaleDialog = true
                } else {
                    // Request the permissions directly
                    singlePermissionLauncher?.launch(permission)
                }
            }
            PermissionStatus.DeniedPermanently -> {
                // Permanently denied: show the rationale dialog and guide the user to settings
                currentRationalePermissions = listOf(permissionInfo)
                showRationaleDialog = true
            }
            else -> {
                // NotRequested state: request the permission directly
                singlePermissionLauncher?.launch(permission)
            }
        }
    }

    /**
     * Continue requesting permissions from the rationale dialog
     */
    fun proceedFromRationale() {
        showRationaleDialog = false

        // Check for permanently denied permissions
        val permanentlyDenied = currentRationalePermissions.filter {
            permissionStates[it.permission] == PermissionStatus.DeniedPermanently
        }

        if (permanentlyDenied.isNotEmpty()) {
            // Some permissions are permanently denied: open the settings page directly
            openAppSettings()
        } else {
            // No permanently denied permissions: request normally
            launchPermissionRequest(currentRationalePermissions)
        }

        currentRationalePermissions = emptyList()
    }

    /**
     * Cancel the permission request
     */
    fun cancelPermissionRequest() {
        showRationaleDialog = false
        currentRationalePermissions = emptyList()
    }

    /**
     * Launch the permission request
     */
    private fun launchPermissionRequest(permissionInfos: List<PermissionInfo>) {
        val permissionsToRequest = permissionInfos.map { it.permission }.toTypedArray()
        if (permissionsToRequest.size == 1) {
            singlePermissionLauncher?.launch(permissionsToRequest[0])
        } else {
            permissionLauncher?.launch(permissionsToRequest)
        }
    }

    /**
     * Open the app settings page
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        activity.startActivity(intent)
    }

    /**
     * Force-refresh the permission states (used when returning from the background)
     * Re-checks all permission states, especially permissions the user may have changed in settings
     */
    fun refreshPermissionStates() {
        permissions.forEach { permissionInfo ->
            val currentSystemStatus = ContextCompat.checkSelfPermission(context, permissionInfo.permission)
            val oldStatus = _permissionStates[permissionInfo.permission]

            val newStatus = when {
                // The system reports it as granted
                currentSystemStatus == PackageManager.PERMISSION_GRANTED -> {
                    PermissionStatus.Granted
                }
                // The system reports it as not granted, but a rationale can be shown
                activity.shouldShowRequestPermissionRationale(permissionInfo.permission) -> {
                    PermissionStatus.Denied
                }
                // The system reports it as not granted, and no rationale can be shown
                else -> {
                    // If it was previously not requested, keep it not requested
                    // Otherwise, treat it as permanently denied
                    if (oldStatus == PermissionStatus.NotRequested || oldStatus == null) {
                        PermissionStatus.NotRequested
                    } else {
                        PermissionStatus.DeniedPermanently
                    }
                }
            }

            _permissionStates[permissionInfo.permission] = newStatus
        }
    }

    /**
     * Handle the permission request result
     */
    internal fun handlePermissionResult(results: Map<String, Boolean>) {
        results.forEach { (permission, granted) ->
            _permissionStates[permission] = if (granted) {
                PermissionStatus.Granted
            } else {
                if (activity.shouldShowRequestPermissionRationale(permission)) {
                    PermissionStatus.Denied
                } else {
                    PermissionStatus.DeniedPermanently
                }
            }
        }
    }

    /**
     * Handle the result of a single permission request
     */
    internal fun handleSinglePermissionResult(permission: String, granted: Boolean) {
        handlePermissionResult(mapOf(permission to granted))
    }

    /**
     * Get the permission result
     */
    fun getPermissionResults(): MultiplePermissionResult {
        val results = permissions.associate { permissionInfo ->
            val status = permissionStates[permissionInfo.permission] ?: PermissionStatus.NotRequested
            permissionInfo.permission to PermissionResult(
                permission = permissionInfo.permission,
                status = status
            )
        }

        return MultiplePermissionResult(
            results = results,
            allRequiredGranted = permissions.filter { it.required }
                .all { permissionStates[it.permission] == PermissionStatus.Granted }
        )
    }
}
