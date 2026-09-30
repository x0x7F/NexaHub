package me.ray.nexahub.ui.pages.extensions.workspace

import me.ray.nexahub.workspace.WorkspaceFileEntry

/**
 * Rough classification of workspace files, deciding what happens when a file is tapped:
 * - TEXT: in-app text editing/preview
 * - IMAGE: in-app zoomable image preview
 * - OTHER: opened by a system app (video/audio/documents, etc.)
 */
enum class WorkspaceFileType { TEXT, IMAGE, OTHER }

private val IMAGE_EXTENSIONS = setOf(
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "heic", "heif", "avif", "ico",
)

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "json5", "xml", "yaml", "yml", "toml", "ini", "conf", "cfg",
    "properties", "env", "csv", "tsv", "log", "html", "htm", "css", "scss", "sass", "less",
    "js", "mjs", "cjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "rb", "go", "rs", "c", "h",
    "cpp", "hpp", "cc", "cs", "swift", "sh", "bash", "zsh", "gradle", "sql", "gitignore",
    "dockerfile", "lua", "php", "pl", "r", "dart", "vue", "svelte", "gql", "graphql", "proto",
    "diff", "patch", "srt", "vtt",
)

fun WorkspaceFileEntry.detectFileType(): WorkspaceFileType {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext.isEmpty() -> WorkspaceFileType.OTHER
        ext in IMAGE_EXTENSIONS -> WorkspaceFileType.IMAGE
        ext in TEXT_EXTENSIONS -> WorkspaceFileType.TEXT
        else -> WorkspaceFileType.OTHER
    }
}
