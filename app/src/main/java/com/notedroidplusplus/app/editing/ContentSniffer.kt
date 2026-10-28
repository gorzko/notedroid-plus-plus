package com.notedroidplusplus.app.editing

import com.notedroidplusplus.app.LanguageMode

/**
 * Content-based language detection, mirroring desktop editors' behavior when
 * a file has no (or a misleading) extension. Pure JVM logic: only a bounded
 * prefix of the content is inspected (first [MAX_SNIFF_BYTES] bytes worth of
 * characters), so detection never scans whole files.
 */
object ContentSniffer {

    const val MAX_SNIFF_BYTES: Int = 8192

    fun detect(content: String): LanguageMode {
        val head = if (content.length > MAX_SNIFF_BYTES) content.substring(0, MAX_SNIFF_BYTES) else content
        val lines = head.lines()

        // Shebang lines are the strongest signal.
        val first = lines.firstOrNull { it.isNotBlank() }?.trim() ?: return LanguageMode.NORMAL_TEXT
        if (first.startsWith("#!")) {
            val cmd = first.removePrefix("#!")
            return when {
                cmd.contains("bash") || cmd.contains("zsh") || cmd.contains("ksh") ||
                    cmd.contains("sh") || cmd.contains("dash") -> LanguageMode.SHELL
                cmd.contains("python") -> LanguageMode.PYTHON
                cmd.contains("perl") -> LanguageMode.PYTHON
                cmd.contains("node") || cmd.contains("bun") || cmd.contains("deno") -> LanguageMode.JAVASCRIPT
                cmd.contains("ruby") -> LanguageMode.RUBY
                cmd.contains("lua") -> LanguageMode.LUA
                cmd.contains("php") -> LanguageMode.PHP
                else -> LanguageMode.SHELL
            }
        }

        if (first.startsWith("<?xml", ignoreCase = true)) {
            return detectXmlDialect(head)
        }
        if (first.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            (first.startsWith("<") && head.contains("<html", ignoreCase = true))
        ) return LanguageMode.HTML

        if (first.startsWith("<?php")) return LanguageMode.PHP
        if (first.startsWith("\\documentclass") || first.startsWith("\\usepackage")) return LanguageMode.TEX

        // C/C++ preprocessor directives.
        val pp = Regex("^\\s*#\\s*(include|define|ifndef|ifdef|endif|pragma)\\b", RegexOption.MULTILINE)
        if (pp.containsMatchIn(head)) {
            val hasIncludeAngle = head.contains("#include <") || head.contains("#include \"")
            if (hasIncludeAngle || head.contains("::") || head.contains("class ") || head.contains("struct ")) {
                return if (head.contains("::") || head.contains("template") || head.contains("namespace ")) {
                    LanguageMode.CPP
                } else LanguageMode.C
            }
            return LanguageMode.C
        }

        // JSON: starts with { or [ and parses as JSON (bounded).
        val trimmed = head.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            if (looksLikeJson(trimmed)) return LanguageMode.JSON
        }

        // YAML: document start marker or key: value lines without braces/semicolons.
        if (head.startsWith("---")) return LanguageMode.YAML
        if (looksLikeYaml(lines)) return LanguageMode.YAML

        // INI: sections like [section] followed by key = value.
        if (looksLikeIni(head.lines())) return LanguageMode.INI

        // Markdown: headings, fenced code, links.
        val mdScore = markdownScore(lines)
        if (mdScore >= 2) return LanguageMode.MARKDOWN

        // SQL: SELECT ... FROM statements.
        if (Regex("\\b(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|CREATE\\s+TABLE)\\b", RegexOption.IGNORE_CASE).containsMatchIn(head) &&
            Regex("\\b(FROM|WHERE|SET|VALUES|TABLE)\\b", RegexOption.IGNORE_CASE).containsMatchIn(head)
        ) return LanguageMode.SQL

        // Python fallback: def/import at line starts.
        if (Regex("^\\s*(def |import |from \\w+ import )", RegexOption.MULTILINE).containsMatchIn(head)) return LanguageMode.PYTHON

        return LanguageMode.NORMAL_TEXT
    }

    /** Extension-based detection stays the default; content is used only when the extension is unknown. */
    fun detectWithFileName(fileName: String, content: String): LanguageMode {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val base = fileName.lowercase()
        if (ext.isNotEmpty() && LanguageMode.entries.any { ext in it.extensions }) {
            return LanguageMode.fromFileName(fileName)
        }
        if (base == "makefile" || base == "gnumakefile") return LanguageMode.MAKEFILE
        return detect(content)
    }

    private fun detectXmlDialect(head: String): LanguageMode {
        val lower = head.lowercase()
        return when {
            lower.contains("<svg") -> LanguageMode.XML
            else -> LanguageMode.XML
        }
    }

    private fun looksLikeJson(head: String): Boolean {
        // Bounded cheap structural check: balanced outer braces/brackets and
        // a colon-separated key after the opening brace.
        var depth = 0
        var inString = false
        var escape = false
        var colon = false
        for (c in head) {
            if (escape) { escape = false; continue }
            when {
                inString -> when (c) {
                    '\\' -> escape = true
                    '"' -> inString = false
                }
                c == '"' -> inString = true
                c == '{' || c == '[' -> depth++
                c == '}' || c == ']' -> { depth--; if (depth < 0) return false }
                c == ':' -> colon = true
            }
            if (depth == 0 && colon) return true
        }
        return depth == 0 && colon
    }

    private fun looksLikeYaml(lines: List<String>): Boolean {
        var kv = 0
        var total = 0
        var scanned = 0
        outer@ for (line in lines) {
            if (scanned++ > 60) break
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            if (t.startsWith("<") || t.startsWith("{") || t.startsWith("[") || t.endsWith(";")) return false
            total++
            if (Regex("^[A-Za-z_][\\w.-]*\\s*:(\\s|$)").containsMatchIn(t)) kv++
        }
        return total >= 2 && kv >= 2 && kv >= total / 2
    }

    private fun looksLikeIni(lines: List<String>): Boolean {
        var section = false
        var kv = 0
        var scanned = 0
        for (line in lines) {
            if (scanned++ > 60) break
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#") || t.startsWith(";")) continue
            if (Regex("^\\[[^\\]]+\\]$").containsMatchIn(t)) { section = true; continue }
            if (Regex("^[A-Za-z_][\\w.\\-]*\\s*=\\s*\\S").containsMatchIn(t)) kv++
        }
        return section && kv >= 1 || kv >= 2
    }

    private fun markdownScore(lines: List<String>): Int {
        var score = 0
        var scanned = 0
        for (line in lines) {
            if (scanned++ > 120) break
            val t = line.trimEnd()
            if (Regex("^#{1,6}\\s+\\S").containsMatchIn(t)) score++
            if (t.startsWith("```") || t.startsWith("~~~")) score++
            if (Regex("\\[[^]]+]\\([^)]+\\)").containsMatchIn(t)) score++
            if (Regex("^\\s*([-*+]|\\d+\\.)\\s+\\S").containsMatchIn(t)) score++
            if (Regex("^>{1,3}\\s").containsMatchIn(t)) score++
            if (Regex("^[=-]{3,}$").containsMatchIn(t)) score++
        }
        return score
    }
}
