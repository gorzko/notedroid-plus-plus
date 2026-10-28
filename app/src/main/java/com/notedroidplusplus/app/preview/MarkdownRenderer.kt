package com.notedroidplusplus.app.preview

/**
 * Minimal Markdown-to-HTML renderer used by the preview. Pure Kotlin, unit-testable;
 * covers headings, bold/italic, inline code, fenced code, lists, links and quotes.
 */
object MarkdownRenderer {

    fun render(src: String): String {
        val sb = StringBuilder()
        val lines = src.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        var inCode = false
        var inList = false
        var inQuote = false
        val paragraph = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                sb.append("<p>").append(inline(paragraph.toString().trim())).append("</p>\n")
                paragraph.setLength(0)
            }
        }

        for (raw in lines) {
            val line = raw
            if (line.trimStart().startsWith("```")) {
                flushParagraph()
                if (inList) { sb.append("</ul>\n"); inList = false }
                if (inQuote) { sb.append("</blockquote>\n"); inQuote = false }
                sb.append(if (inCode) "</code></pre>\n" else "<pre><code>")
                inCode = !inCode
                continue
            }
            if (inCode) { sb.append(escape(line)).append('\n'); continue }
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> { flushParagraph(); if (inList) { sb.append("</ul>\n"); inList = false }; if (inQuote) { sb.append("</blockquote>\n"); inQuote = false } }
                trimmed.startsWith("#") -> {
                    flushParagraph(); if (inList) { sb.append("</ul>\n"); inList = false }; if (inQuote) { sb.append("</blockquote>\n"); inQuote = false }
                    val level = trimmed.takeWhile { it == '#' }.length.coerceAtMost(6)
                    sb.append("<h").append(level).append('>')
                        .append(inline(trimmed.drop(level).trim()))
                        .append("</h").append(level).append(">\n")
                }
                trimmed.startsWith("> ") -> {
                    flushParagraph(); if (!inQuote) { sb.append("<blockquote>\n"); inQuote = true }
                    sb.append("<p>").append(inline(trimmed.drop(2))).append("</p>\n")
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    flushParagraph(); if (!inList) { sb.append("<ul>\n"); inList = true }
                    sb.append("<li>").append(inline(trimmed.drop(2))).append("</li>\n")
                }
                Regex("^\\d+\\. ").containsMatchIn(trimmed) -> {
                    flushParagraph(); if (!inList) { sb.append("<ul>\n"); inList = true }
                    sb.append("<li>").append(inline(trimmed.substringAfter(". "))).append("</li>\n")
                }
                else -> {
                    if (inList) { sb.append("</ul>\n"); inList = false }
                    if (inQuote) { sb.append("</blockquote>\n"); inQuote = false }
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
        flushParagraph()
        if (inList) sb.append("</ul>\n")
        if (inQuote) sb.append("</blockquote>\n")
        if (inCode) sb.append("</code></pre>\n")
        return sb.toString()
    }

    private fun inline(s: String): String {
        var r = escape(s)
        r = Regex("`([^`]+)`").replace(r) { "<code>${it.groupValues[1]}</code>" }
        r = Regex("\\*\\*([^*]+)\\*\\*").replace(r) { "<b>${it.groupValues[1]}</b>" }
        r = Regex("\\*([^*]+)\\*").replace(r) { "<i>${it.groupValues[1]}</i>" }
        r = Regex("\\[([^]]+)]\\(([^()]*(?:\\([^()]*\\)[^()]*)*)\\)").replace(r) { m ->
            val url = sanitizeUrl(m.groupValues[2])
            if (url != null) "<a href=\"" + url + "\">" + m.groupValues[1] + "</a>" else m.groupValues[1]
        }
        return r
    }

    /**
     * Percent-encode attribute-breaking characters and allow only safe URL
     * schemes (http, https, mailto, relative). javascript:/data:/file: and
     * anything else are rejected by returning null (fail closed).
     */
    internal fun sanitizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        val scheme = trimmed.substringBefore(':', "").lowercase()
        val allowedScheme = scheme.isEmpty() || scheme == "http" || scheme == "https" || scheme == "mailto"
        if (!allowedScheme) return null
        return trimmed
            .replace("%", "%25")
            .replace("&", "%26")
            .replace("\"", "%22")
            .replace("<", "%3C")
            .replace(">", "%3E")
            .replace(" ", "%20")
            .replace("'", "%27")
    }

    fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
