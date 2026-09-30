package me.rerere.rikkahub.utils

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

private const val DEFAULT_CONTEXT_LINES = 3

/**
 * Generate the unified diff text from [oldText] to [newText]; returns null when the content is identical
 */
fun generateUnifiedDiff(
    oldText: String,
    newText: String,
    path: String,
    contextLines: Int = DEFAULT_CONTEXT_LINES,
): String? {
    if (oldText == newText) return null
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    val patch = DiffUtils.diff(oldLines, newLines)
    if (patch.deltas.isEmpty()) return null
    return UnifiedDiffUtils
        .generateUnifiedDiff("a/$path", "b/$path", oldLines, patch, contextLines)
        .joinToString("\n")
}
