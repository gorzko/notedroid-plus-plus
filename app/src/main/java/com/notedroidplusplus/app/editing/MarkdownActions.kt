package com.notedroidplusplus.app.editing

/**
 * Pure Markdown text transforms used by the formatting toolbar. Each action
 * maps a document text + selection range to a new text + selection, so the
 * caller applies them like ordinary edits (and they flow through the
 * normal undo history).
 */
object MarkdownActions {

    data class Result(val text: String, val newStart: Int, val newEnd: Int)

    private const val MAX_HEADING_LEVEL = 6

    fun toggleSpan(text: String, start: Int, end: Int, marker: String): Result {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val sel = text.substring(s, e)
        val before = text.substring(0, s)
        val after = text.substring(e)
        val outside = before.endsWith(marker) && after.startsWith(marker)
        val inside = sel.startsWith(marker) && sel.endsWith(marker) && sel.length >= 2 * marker.length
        val caretInsideSpan = s == e && outside &&
            s - marker.length >= 0 && e + marker.length <= text.length &&
            sel.isNotEmpty()
        return when {
            caretInsideSpan -> Result(text, s, e)
            outside -> Result(
                before.substring(0, before.length - marker.length) + sel + after.substring(marker.length),
                s - marker.length,
                e - marker.length
            )
            inside -> {
                val body = sel.substring(marker.length, sel.length - marker.length)
                val newText = before + body + after
                Result(newText, s, s + body.length)
            }
            else -> Result(before + marker + sel + marker + after, s + marker.length, e + marker.length)
        }
    }

    fun bold(text: String, start: Int, end: Int): Result = toggleSpan(text, start, end, "**")
    fun italic(text: String, start: Int, end: Int): Result = toggleSpan(text, start, end, "*")
    fun strike(text: String, start: Int, end: Int): Result = toggleSpan(text, start, end, "~~")
    fun inlineCode(text: String, start: Int, end: Int): Result = toggleSpan(text, start, end, "`")

    fun heading(text: String, start: Int, end: Int, level: Int): Result {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val lineStart = text.lastIndexOf('\n', s - 1).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', s).let { if (it == -1) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val existing = Regex("^#{1,6}(?:\\s|$)").find(line)?.value?.length ?: 0
        val body = if (existing > 0) line.substring(existing) else line
        val lvl = level.coerceIn(1, MAX_HEADING_LEVEL)
        val prefix = "${"#".repeat(lvl)} "
        val newLine = (prefix + body).trimEnd()
        val newText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val prefixDelta = prefix.length - existing
        val bodyStart = lineStart + prefix.length.coerceAtMost(newLine.length)
        val lo = (s + prefixDelta).coerceIn(bodyStart, lineStart + newLine.length)
        val hi = (e + prefixDelta).coerceIn(lo, lineStart + newLine.length)
        return Result(newText, lo, hi)
    }

    fun list(text: String, start: Int, end: Int, ordered: Boolean): Result {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val lineStart = text.lastIndexOf('\n', s - 1).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', e).let { if (it == -1) text.length else it }
        val block = text.substring(lineStart, lineEnd)
        var index = 0
        val markerRegex = Regex("^(\\s*)(?:[-*+]|\\d+\\.)\\s+")
        val lines = block.lines()
        val anyMarked = lines.any { markerRegex.containsMatchIn(it) }
        val newBlock = lines.joinToString("\n") { line ->
            if (anyMarked) {
                line.replaceFirst(markerRegex, "$1")
            } else if (line.isBlank()) {
                line
            } else {
                index++
                val indent = line.substring(0, line.length - line.trimStart().length)
                indent + (if (ordered) "$index. " else "- ") + line.trimStart()
            }
        }
        val newText = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
        return Result(newText, lineStart, (lineStart + newBlock.length).coerceAtMost(newText.length))
    }

    fun link(text: String, start: Int, end: Int, url: String, label: String? = null): Result {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val selected = text.substring(s, e)
        val linkLabel = label ?: selected.ifEmpty { "link" }
        val inserted = "[$linkLabel](${url.ifEmpty { "https://" }})"
        return Result(
            text.substring(0, s) + inserted + text.substring(e),
            s,
            s + inserted.length
        )
    }

    fun codeBlock(text: String, start: Int, end: Int): Result {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val sel = text.substring(s, e)
        val nl = if (s > 0 && text[s - 1] != '\n') "\n" else ""
        val inserted = "${nl}```\n$sel\n```"
        return Result(
            text.substring(0, s) + inserted + text.substring(e),
            s,
            s + inserted.length
        )
    }

    fun table(text: String, start: Int): Result {
        val s = start.coerceIn(0, text.length)
        val nl = if (s > 0 && text[s - 1] != '\n') "\n\n" else ""
        val inserted = "${nl}| Column 1 | Column 2 |\n| --- | --- |\n|  |  |"
        return Result(
            text.substring(0, s) + inserted + text.substring(s),
            s,
            s + inserted.length
        )
    }
}
