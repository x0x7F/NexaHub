package me.rerere.rikkahub.ui.pages.extensions.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.compose.koinInject

/**
 * Workspace text file editor/preview page.
 *
 * Files in the FILES area can be edited and saved; files in the LINUX (rootfs) area are read-only previews (readOnly), to avoid modifying system files by mistake.
 */
@Composable
fun WorkspaceFileEditorPage(
    id: String,
    area: WorkspaceStorageArea,
    path: String,
) {
    val repository = koinInject<WorkspaceRepository>()
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val editable = area == WorkspaceStorageArea.FILES
    val fileName = path.substringAfterLast('/').ifBlank { path }
    val extension = fileName.substringAfterLast('.', "").lowercase()
    val supportsPreview = extension in setOf("html", "htm", "svg")
    var showPreview by rememberSaveable(id, area, path) { mutableStateOf(supportsPreview) }

    // Can't use rememberTextFieldState: it stores the full text in the saved state Bundle, which triggers TransactionTooLargeException for large files when backgrounded (#1953).
    // The content is loaded from disk by the LaunchedEffect below anyway, so it doesn't need to be saveable.
    val textState = remember(id, area, path) { TextFieldState() }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id, area, path) {
        loading = true
        loadError = null
        runCatching {
            repository.readTextForPreview(id, area, path)
        }.onSuccess { content ->
            textState.setTextAndPlaceCursorAtEnd(content)
            loading = false
        }.onFailure {
            loadError = it.message ?: "Failed to read the file"
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (supportsPreview && !loading && loadError == null) {
                        TextButton(onClick = { showPreview = !showPreview }) {
                            Text(if (showPreview) "Source" else "Preview")
                        }
                    }
                    if (editable && !loading && loadError == null) {
                        TextButton(
                            onClick = {
                                if (saving) return@TextButton
                                saving = true
                                scope.launch {
                                    runCatching {
                                        repository.writeText(
                                            id = id,
                                            path = path,
                                            text = textState.text.toString(),
                                            overwrite = true,
                                        )
                                    }.onSuccess {
                                        toaster.show("Saved", type = ToastType.Success)
                                    }.onFailure {
                                        toaster.show(it.message ?: "Save failed", type = ToastType.Error)
                                    }
                                    saving = false
                                }
                            },
                            enabled = !saving,
                        ) {
                            Text("Save")
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            loadError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
            ) {
                Text(
                    text = loadError ?: "",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            showPreview -> WorkspaceWebPreview(
                content = textState.text.toString(),
                isSvg = extension == "svg",
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            else -> TextField(
                state = textState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
                readOnly = !editable,
                lineLimits = TextFieldLineLimits.MultiLine(),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = JetbrainsMono,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
            )
        }
    }
}

@Composable
private fun WorkspaceWebPreview(
    content: String,
    isSvg: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberWebViewState(
        data = content,
        baseUrl = "https://workspace-preview.invalid/",
        mimeType = if (isSvg) "image/svg+xml" else "text/html",
        settings = {
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        },
    )
    WebView(state = state, modifier = modifier)
}
