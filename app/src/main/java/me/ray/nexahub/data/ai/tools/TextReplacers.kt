package me.ray.nexahub.data.ai.tools

/**
 * Text replacers used by workspace_edit_file, tried in [WorkspaceEditReplacers] order;
 * a looser matching strategy is only used when the previous one finds no match.
 */
interface TextReplacer {
    val name: String

    fun findMatches(content: String, oldText: String, newText: String): List<Match>

    data class Match(
        val start: Int,
        val endExclusive: Int,
        val replacement: String,
    )
}

data class ReplaceTextResult(
    val updated: String,
    val replacements: Int,
    val occurrences: Int,
    val strategy: String,
)

val WorkspaceEditReplacers: List<TextReplacer> = listOf(
    ExactReplacer,
    LineTrimmedReplacer,
    BlockAnchorReplacer,
)

/**
 * Try [replacers] in order and use the first one that produces a match.
 * When there are multiple matches and [replaceAll] is false, throws [IllegalArgumentException] instead of guessing which one to replace.
 */
fun replaceText(
    content: String,
    oldText: String,
    newText: String,
    replaceAll: Boolean,
    replacers: List<TextReplacer> = WorkspaceEditReplacers,
): ReplaceTextResult {
    require(oldText.isNotEmpty()) { "old_text must not be empty" }
    for (replacer in replacers) {
        val matches = replacer.findMatches(content, oldText, newText)
        if (matches.isEmpty()) continue
        if (!replaceAll) {
            require(matches.size == 1) {
                "old_text matches ${matches.size} locations (strategy: ${replacer.name}); " +
                    "add more surrounding context to make it unique, or set replace_all=true"
            }
        }
        val applied = if (replaceAll) matches.sortedBy { it.start } else listOf(matches.minBy { it.start })
        val builder = StringBuilder(content.length)
        var cursor = 0
        for (match in applied) {
            builder.append(content, cursor, match.start)
            builder.append(match.replacement)
            cursor = match.endExclusive
        }
        builder.append(content, cursor, content.length)
        return ReplaceTextResult(
            updated = builder.toString(),
            replacements = applied.size,
            occurrences = matches.size,
            strategy = replacer.name,
        )
    }
    throw IllegalArgumentException(
        "old_text was not found, even with whitespace-tolerant matching; " +
            "read the file again and copy old_text exactly from its current content"
    )
}

/**
 * Level 1: exact match with non-overlapping counting, same semantics as String.replace.
 */
object ExactReplacer : TextReplacer {
    override val name: String = "exact"

    override fun findMatches(content: String, oldText: String, newText: String): List<TextReplacer.Match> {
        val matches = mutableListOf<TextReplacer.Match>()
        var index = content.indexOf(oldText)
        while (index >= 0) {
            matches += TextReplacer.Match(index, index + oldText.length, newText)
            index = content.indexOf(oldText, index + oldText.length)
        }
        return matches
    }
}

/**
 * Common skeleton for line-window matching: split content and old_text into lines, compare a sliding window,
 * and on a hit re-indent new_text using the real indentation of the first matched line.
 */
abstract class LineWindowReplacer : TextReplacer {

    protected abstract fun windowMatches(windowTrimmed: List<String>, oldTrimmed: List<String>): Boolean

    /** Loose matching is disabled when old_text is all blank lines, to avoid matching arbitrary whitespace */
    protected open fun isApplicable(oldTrimmed: List<String>): Boolean =
        oldTrimmed.any { it.isNotEmpty() }

    override fun findMatches(content: String, oldText: String, newText: String): List<TextReplacer.Match> {
        val rawOldLines = oldText.lines()
        // "foo\n" is semantically one line; drop the empty trailing line from the final newline, and do the same for new_text to keep them symmetric
        val dropTrailingEmpty = rawOldLines.size > 1 && rawOldLines.last().isEmpty()
        val oldLines = if (dropTrailingEmpty) rawOldLines.dropLast(1) else rawOldLines
        val oldTrimmed = oldLines.map { it.trim() }
        if (!isApplicable(oldTrimmed)) return emptyList()
        val adjustedNewText = if (dropTrailingEmpty) newText.removeOneTrailingNewline() else newText

        val contentLines = splitLinesWithOffsets(content)
        val matches = mutableListOf<TextReplacer.Match>()
        var index = 0
        while (index + oldLines.size <= contentLines.size) {
            val window = contentLines.subList(index, index + oldLines.size)
            if (windowMatches(window.map { it.text.trim() }, oldTrimmed)) {
                val replacement = reindent(
                    text = adjustedNewText,
                    oldIndent = indentOf(oldLines.first()),
                    newIndent = indentOf(window.first().text),
                )
                matches += TextReplacer.Match(window.first().start, window.last().endExclusive, replacement)
                index += oldLines.size
            } else {
                index++
            }
        }
        return matches
    }
}

/**
 * Level 2: compare line by line after trimming, tolerating indentation / trailing whitespace / CRLF differences.
 */
object LineTrimmedReplacer : LineWindowReplacer() {
    override val name: String = "line_trimmed"

    override fun windowMatches(windowTrimmed: List<String>, oldTrimmed: List<String>): Boolean =
        windowTrimmed == oldTrimmed
}

/**
 * Level 3: when old_text has at least 3 lines, match using only the first and last lines as anchors, tolerating small differences in the middle lines.
 */
object BlockAnchorReplacer : LineWindowReplacer() {
    override val name: String = "block_anchor"

    override fun isApplicable(oldTrimmed: List<String>): Boolean =
        oldTrimmed.size >= 3 && oldTrimmed.first().isNotEmpty() && oldTrimmed.last().isNotEmpty()

    override fun windowMatches(windowTrimmed: List<String>, oldTrimmed: List<String>): Boolean =
        windowTrimmed.first() == oldTrimmed.first() && windowTrimmed.last() == oldTrimmed.last()
}

private class LineWithOffset(
    val start: Int,
    val endExclusive: Int,
    val text: String,
)

private fun splitLinesWithOffsets(content: String): List<LineWithOffset> {
    val lines = mutableListOf<LineWithOffset>()
    var start = 0
    for (index in content.indices) {
        if (content[index] == '\n') {
            val end = if (index > start && content[index - 1] == '\r') index - 1 else index
            lines += LineWithOffset(start, end, content.substring(start, end))
            start = index + 1
        }
    }
    lines += LineWithOffset(start, content.length, content.substring(start))
    return lines
}

private fun indentOf(line: String): String = line.takeWhile { it == ' ' || it == '\t' }

private fun reindent(text: String, oldIndent: String, newIndent: String): String {
    if (oldIndent == newIndent) return text
    return text.lines().joinToString("\n") { line ->
        when {
            line.isBlank() -> line
            line.startsWith(oldIndent) -> newIndent + line.removePrefix(oldIndent)
            else -> line
        }
    }
}

private fun String.removeOneTrailingNewline(): String = when {
    endsWith("\r\n") -> dropLast(2)
    endsWith("\n") -> dropLast(1)
    else -> this
}
