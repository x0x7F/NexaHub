package me.ray.nexahub.utils

/**
 * Remove Markdown formatting from a string
 * @return the plain text without Markdown formatting
 */
fun String.stripMarkdown(): String {
    return this
        // Remove code blocks (```...``` and `...`)
        .replace(Regex("```[\\s\\S]*?```|`[^`]*?`"), "")
        // Remove images and links, keeping their text
        .replace(Regex("!?\\[([^\\]]+)\\]\\([^\\)]*\\)"), "$1")
        // Remove bold and italics (double asterisks first)
        .replace(Regex("\\*\\*([^*]+?)\\*\\*"), "$1")
        .replace(Regex("\\*([^*]+?)\\*"), "$1")
        // Remove underscores
        .replace(Regex("__([^_]+?)__"), "$1")
        .replace(Regex("_([^_]+?)_"), "$1")
        // Remove strikethrough
        .replace(Regex("~~([^~]+?)~~"), "$1")
        // Remove heading markers (multiline mode)
        .replace(Regex("(?m)^#+\\s*"), "")
        // Remove list markers (multiline mode)
        .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
        .replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
        // Remove blockquote markers (multiline mode)
        .replace(Regex("(?m)^>\\s*"), "")
        // Remove horizontal rules
        .replace(Regex("(?m)^(\\s*[-*_]){3,}\\s*$"), "")
        // Collapse multiple newlines, keeping paragraphs
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

fun String.extractThinkingTitle(): String? {
    // Split the text into lines
    val lines = this.lines()

    // Search from the end for the last line that is bold text
    for (i in lines.indices.reversed()) {
        val line = lines[i].trim()

        // Check whether it's bold and takes up the whole line
        val boldPattern = Regex("^\\*\\*(.+?)\\*\\*$")
        val match = boldPattern.find(line)

        if (match != null) {
            // Return the text inside the bold markers
            return match.groupValues[1].trim().takeUnless { it.isBlank() }
        }
    }

    return null
}
