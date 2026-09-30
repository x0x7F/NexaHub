# Permission Library Usage

A complete Android runtime permission request library that automatically shows a dialog explaining why a permission is requested.

## Key features

- 🎯 **Declarative API**: Compose-style declarative permission management
- 🔄 **State management**: automatically tracks permission state changes
- 💬 **Smart dialog**: automatically explains why a permission is requested
- 🎨 **Material Design 3**: follows the latest design guidelines
- 🔧 **Flexible configuration**: supports required/optional permissions
- 🔄 **Lifecycle aware**: refreshes permission states when the app returns from the background
- ⚡ **Permanent denial handling**: handles permanently denied permissions and guides the user to the settings page

## Basic usage

### 1. Single permission

```kotlin
@Composable
fun CameraScreen() {
    val cameraPermission = rememberPermissionState(
        permission = Manifest.permission.CAMERA,
        usage = { 
            Text(
                text = "Camera permission is needed to take photos and record videos",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        required = true
    )
    
    PermissionManager(permissionState = cameraPermission) {
        PermissionCheck(
            permissionState = cameraPermission,
            onGranted = {
                // Permission granted: show the camera UI
                CameraContent()
            },
            onDenied = { state ->
                // Permission denied: show custom content
                Button(onClick = { state.requestPermissions() }) {
                    Text("Request camera permission")
                }
            }
        )
    }
}
```

### 2. Multiple permissions

```kotlin
@Composable
fun MediaScreen() {
    val mediaPermissions = rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = Manifest.permission.CAMERA,
                usage = { 
                    Text("Camera permission is needed to take photos")
                },
                required = true
            ),
            PermissionInfo(
                permission = Manifest.permission.RECORD_AUDIO,
                usage = { 
                    Text("Microphone permission is needed to record video")
                },
                required = false
            ),
            PermissionInfo(
                permission = Manifest.permission.READ_EXTERNAL_STORAGE,
                usage = { 
                    Text("Storage permission is needed to save media files")
                },
                required = true
            )
        )
    )
    
    PermissionManager(permissionState = mediaPermissions) {
        when {
            mediaPermissions.allRequiredPermissionsGranted -> {
                // All required permissions granted
                MediaContent(hasAudioPermission = mediaPermissions.permissionStates[Manifest.permission.RECORD_AUDIO] == PermissionStatus.Granted)
            }
            else -> {
                // Show the permission request UI
                Column {
                    Text("Permissions are required to continue")
                    Button(onClick = { mediaPermissions.requestPermissions() }) {
                        Text("Request permissions")
                    }
                }
            }
        }
    }
}
```

### 3. Checking permission state

```kotlin
@Composable
fun PermissionStatusExample() {
    val permissionState = rememberPermissionState(...)
    
    // Check all permissions
    if (permissionState.allPermissionsGranted) {
        Text("All permissions granted")
    }
    
    // Check required permissions
    if (permissionState.allRequiredPermissionsGranted) {
        Text("Required permissions granted")
    }
    
    // Check a single permission
    when (permissionState.permissionStates[Manifest.permission.CAMERA]) {
        PermissionStatus.Granted -> Text("Camera permission granted")
        PermissionStatus.Denied -> Text("Camera permission denied")
        PermissionStatus.DeniedPermanently -> Text("Camera permission permanently denied")
        PermissionStatus.NotRequested -> Text("Camera permission not requested")
        null -> Text("Permission state unknown")
    }
}
```

### 4. Manual permission management

```kotlin
@Composable
fun ManualPermissionExample() {
    val permissionState = rememberPermissionState(...)
    
    Column {
        Button(onClick = { 
            // Request all permissions
            permissionState.requestPermissions() 
        }) {
            Text("Request all permissions")
        }
        
        Button(onClick = { 
            // Request specific permissions
            permissionState.requestPermission(Manifest.permission.CAMERA) 
        }) {
            Text("Request camera permission")
        }
        
        Button(onClick = { 
            // Open the app settings
            permissionState.openAppSettings() 
        }) {
            Text("Go to settings")
        }
        
        Button(onClick = { 
            // Update the permission states
            permissionState.updatePermissionStates() 
        }) {
            Text("Refresh permission states")
        }
    }
}
```

## API reference

### PermissionInfo

```kotlin
data class PermissionInfo(
    val permission: String,        // Android permission string
    val usage: @Composable () -> Unit,  // Permission usage explanation
    val required: Boolean = false  // Whether the permission is required
)
```

### PermissionState

Main properties:
- `permissionStates: Map<String, PermissionStatus>` - permission state map
- `allPermissionsGranted: Boolean` - whether all permissions are granted
- `allRequiredPermissionsGranted: Boolean` - whether all required permissions are granted
- `deniedPermissions: List<PermissionInfo>` - list of denied permissions

Main methods:
- `requestPermissions()` - request all permissions that aren't granted
- `requestPermission(permission: String)` - request a specific permission
- `updatePermissionStates()` - update the permission states
- `refreshPermissionStates()` - force-refresh the permission states (used on lifecycle changes)
- `openAppSettings()` - open the app settings page

### PermissionStatus

```kotlin
enum class PermissionStatus {
    NotRequested,      // Not requested
    Granted,          // Granted
    Denied,           // Denied but can be requested again
    DeniedPermanently // Denied and the user chose "Don't ask again"
}
```

## Notes

1. **Activity requirement**: `rememberPermissionState` must be used in a `ComponentActivity`
2. **Permission declarations**: make sure the required permissions are declared in `AndroidManifest.xml`
3. **Lifecycle awareness**: permission states refresh automatically when the app returns from the background
4. **Dialog**: the permission rationale dialog is shown automatically when needed
5. **Settings redirect**: for permanently denied permissions, an option to open the settings is offered
6. **State sync**: after the user changes permissions in settings, the state updates as soon as they return to the app

## Best practices

1. **Request on demand**: only request a permission when the feature needs it
2. **Explain clearly**: clearly explain what the permission is for in `usage`
3. **Categorize**: set the `required` flag appropriately to distinguish required and optional permissions
4. **Graceful degradation**: provide a degraded experience when a permission is denied
5. **Persistent state**: permission states survive configuration changes
