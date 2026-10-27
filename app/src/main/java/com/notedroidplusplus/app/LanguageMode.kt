package com.notedroidplusplus.app

/** Language modes mirroring Notepad++ Languages menu. */
enum class LanguageMode(val displayName: String, val extensions: Set<String>) {
    NORMAL_TEXT("Normal text", emptySet()),
    C("C", setOf("c", "h")),
    CPP("C++", setOf("cpp", "cc", "cxx", "hpp", "hh")),
    CSHARP("C#", setOf("cs")),
    JAVA("Java", setOf("java")),
    KOTLIN("Kotlin", setOf("kt", "kts")),
    PYTHON("Python", setOf("py", "pyw")),
    JAVASCRIPT("JavaScript", setOf("js", "jsx", "mjs")),
    TYPESCRIPT("TypeScript", setOf("ts", "tsx")),
    HTML("HTML", setOf("html", "htm")),
    XML("XML", setOf("xml", "xsl", "xsd", "svg", "plist")),
    CSS("CSS", setOf("css")),
    JSON("JSON", setOf("json")),
    YAML("YAML", setOf("yaml", "yml")),
    MARKDOWN("Markdown", setOf("md", "markdown")),
    SQL("SQL", setOf("sql")),
    SHELL("Shell", setOf("sh", "bash", "zsh")),
    BATCH("Batch", setOf("bat", "cmd")),
    INI("Ini", setOf("ini", "cfg", "conf", "properties")),
    PHP("PHP", setOf("php")),
    GO("Go", setOf("go")),
    RUST("Rust", setOf("rs")),
    SWIFT("Swift", setOf("swift")),
    RUBY("Ruby", setOf("rb")),
    LUA("Lua", setOf("lua")),
    MAKEFILE("Makefile", setOf("mk")),
    TEX("TeX", setOf("tex"));

    companion object {
        fun fromFileName(name: String): LanguageMode {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                entries.firstOrNull { ext in it.extensions }?.let { return it }
            }
            val base = name.lowercase()
            if (base == "makefile" || base == "gnumakefile") return MAKEFILE
            return NORMAL_TEXT
        }
    }
}

/** A single syntax token: [start, end) offsets into the text plus its style class. */
data class SyntaxToken(val start: Int, val end: Int, val kind: TokenKind)

enum class TokenKind {
    KEYWORD,
    TYPE,
    NUMBER,
    STRING,
    COMMENT,
    OPERATOR,
    PREPROCESSOR,
    TAG,
    ATTRIBUTE,
    VALUE,
    HEADING,
    LINK,
    ESCAPE;

    val spanFlag: Int get() = 1 shl ordinal
}
