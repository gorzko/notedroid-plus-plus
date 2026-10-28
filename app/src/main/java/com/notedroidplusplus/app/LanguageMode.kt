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
    TEX("TeX", setOf("tex")),
    TOML("TOML", setOf("toml")),
    POWERSHELL("PowerShell", setOf("ps1", "psm1", "psd1")),
    PERL("Perl", setOf("pl", "pm", "cgi")),
    CMAKE("CMake", setOf("cmake")),
    DIFF("Diff", setOf("diff", "patch")),
    VHDL("VHDL", setOf("vhd", "vhdl")),
    VERILOG("Verilog", setOf("v", "sv")),
    ASSEMBLY("Assembly", setOf("asm", "s")),
    AUTOIT("AutoIt", setOf("au3")),
    COFFEESCRIPT("CoffeeScript", setOf("coffee")),
    LISP("Lisp", setOf("lisp", "lsp", "cl")),
    SCHEME("Scheme", setOf("scm", "ss")),
    PASCAL("Pascal", setOf("pas", "pp", "dpr")),
    ADA("Ada", setOf("adb", "ads", "ada")),
    FORTRAN("Fortran", setOf("f", "for", "f90", "f95")),
    HASKELL("Haskell", setOf("hs", "lhs")),
    ERLANG("Erlang", setOf("erl", "hrl")),
    R("R", setOf("r", "rdata")),
    TCL("Tcl", setOf("tcl", "tk")),
    MATLAB("Matlab", setOf("m")),
    OBJECTIVE_C("Objective-C", setOf("mm", "m")),
    VISUALBASIC("Visual Basic", setOf("vb", "bas", "frm")),
    COBOL("COBOL", setOf("cbl", "cob")),
    SMALLTALK("Smalltalk", setOf("st")),
    POSTSCRIPT("PostScript", setOf("ps", "eps")),
    D("D", setOf("d", "di")),
    NIM("Nim", setOf("nim", "nims")),
    FORTH("Forth", setOf("4th", "ft", "fth")),
    ACTIONSCRIPT("ActionScript", setOf("as")),
    NFO("NFO", setOf("nfo"));

    companion object {
        fun fromFileName(name: String): LanguageMode {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isNotEmpty()) {
                entries.firstOrNull { ext in it.extensions }?.let { return it }
            }
            val base = name.lowercase()
            if (base == "makefile" || base == "gnumakefile") return MAKEFILE
            if (base == "cmakelists.txt") return CMAKE
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
}
