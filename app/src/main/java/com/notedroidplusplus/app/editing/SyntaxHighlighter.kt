package com.notedroidplusplus.app.editing

import com.notedroidplusplus.app.LanguageMode
import com.notedroidplusplus.app.SyntaxToken
import com.notedroidplusplus.app.TokenKind

/**
 * Pure-Kotlin syntax tokenizer, mirroring Notepad++'s Scintilla lexers closely enough
 * for the classic look: keywords, types, numbers, strings, comments, operators and
 * per-language structures (tags/attributes for markup, headings/links for Markdown).
 *
 * Design notes:
 * - Deterministic single-pass tokenizer; unit-testable with no Android dependencies.
 * - Per-language keyword/type sets modeled on Notepad++ stylers.model.xml defaults.
 */
object SyntaxHighlighter {

    /** Public read access for auto-completion keyword candidates. */
    fun keywordsFor(mode: LanguageMode): List<String> =
        (KEYWORDS[mode] ?: emptySet()).sorted()

    private val KEYWORDS: Map<LanguageMode, Set<String>> = mapOf(
        LanguageMode.C to setOf(
            "auto", "break", "case", "const", "continue", "default", "do", "else", "enum",
            "extern", "for", "goto", "if", "inline", "register", "restrict", "return",
            "sizeof", "static", "struct", "switch", "typedef", "union", "volatile", "while",
            "NULL"
        ),
        LanguageMode.CPP to setOf(
            "alignas", "alignof", "and", "asm", "auto", "bool", "break", "case", "catch",
            "char", "class", "const", "constexpr", "continue", "decltype", "default",
            "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit", "export",
            "extern", "false", "float", "for", "friend", "goto", "if", "inline", "int",
            "long", "mutable", "namespace", "new", "noexcept", "not", "nullptr",
            "operator", "or", "private", "protected", "public", "register", "return",
            "short", "signed", "sizeof", "static", "static_assert", "static_cast",
            "struct", "switch", "template", "this", "throw", "true", "try", "typedef",
            "typeid", "typename", "union", "unsigned", "using", "virtual", "void",
            "volatile", "wchar_t", "while", "NULL"
        ),
        LanguageMode.CSHARP to setOf(
            "abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char",
            "checked", "class", "const", "continue", "decimal", "default", "delegate", "do",
            "double", "else", "enum", "event", "explicit", "extern", "false", "finally",
            "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in", "int",
            "interface", "internal", "is", "lock", "long", "namespace", "new", "null",
            "object", "operator", "out", "override", "params", "private", "protected",
            "public", "readonly", "ref", "return", "sbyte", "sealed", "short", "sizeof",
            "stackalloc", "static", "string", "struct", "switch", "this", "throw", "true",
            "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort", "using",
            "virtual", "void", "volatile", "while", "var", "async", "await", "when",
            "where", "get", "set", "value"
        ),
        LanguageMode.JAVA to setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "null",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "var",
            "record", "sealed", "permits", "yield"
        ),
        LanguageMode.KOTLIN to setOf(
            "as", "break", "by", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return", "super",
            "this", "throw", "true", "try", "typealias", "val", "var", "when", "while",
            "import", "init", "override", "public", "private", "protected", "internal",
            "suspend", "data", "sealed", "open", "abstract", "companion", "lateinit",
            "crossinline", "noinline", "reified", "out", "where", "get", "set", "it",
            "constructor", "operator", "inline", "external", "annotation", "enum"
        ),
        LanguageMode.PYTHON to setOf(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
            "del", "elif", "else", "except", "finally", "for", "from", "global", "if",
            "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise",
            "return", "try", "while", "with", "yield", "True", "False", "None", "self"
        ),
        LanguageMode.JAVASCRIPT to setOf(
            "async", "await", "break", "case", "catch", "class", "const", "continue",
            "debugger", "default", "delete", "do", "else", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof", "let", "new",
            "null", "of", "return", "static", "super", "switch", "this", "throw", "true",
            "try", "typeof", "undefined", "var", "void", "while", "with", "yield"
        ),
        LanguageMode.TYPESCRIPT to setOf(
            "abstract", "any", "as", "async", "await", "boolean", "break", "case", "catch",
            "class", "const", "continue", "declare", "default", "delete", "do", "else",
            "enum", "export", "extends", "false", "finally", "for", "from", "function",
            "if", "implements", "import", "in", "infer", "instanceof", "interface", "is",
            "keyof", "let", "namespace", "never", "new", "null", "number", "object",
            "of", "private", "protected", "public", "readonly", "return", "satisfies",
            "static", "string", "super", "switch", "this", "throw", "true", "try", "type",
            "typeof", "undefined", "union", "unknown", "var", "void", "while", "yield"
        ),
        LanguageMode.SQL to setOf(
            "add", "all", "alter", "and", "any", "as", "asc", "authorization", "backup",
            "begin", "between", "break", "browse", "bulk", "by", "cascade", "case",
            "check", "checkpoint", "close", "clustered", "coalesce", "collate", "column",
            "commit", "compute", "constraint", "contains", "containstable", "continue",
            "convert", "create", "cross", "current", "current_date", "current_time",
            "current_timestamp", "current_user", "cursor", "database", "databases", "day",
            "dbcc", "deallocate", "declare", "default", "delete", "deny", "desc", "disk",
            "distinct", "distributed", "double", "drop", "dummy", "dump", "else", "end",
            "errlvl", "escape", "except", "exec", "execute", "exists", "exit", "fetch",
            "file", "fillfactor", "for", "foreign", "freetext", "freetexttable", "from",
            "full", "function", "goto", "grant", "group", "having", "holdlock", "identity",
            "identity_insert", "identitycol", "if", "in", "index", "inner", "insert",
            "intersect", "into", "is", "join", "key", "kill", "left", "like", "lineno",
            "load", "match", "merge", "national", "nocheck", "nonclustered", "not",
            "null", "nullif", "of", "off", "offsets", "on", "open", "opendatasource",
            "openquery", "openrowset", "openxml", "option", "or", "order", "outer",
            "over", "percent", "plan", "precision", "primary", "print", "proc",
            "procedure", "public", "raiserror", "read", "readtext", "reconfigure",
            "references", "replication", "restore", "restrict", "return", "revoke",
            "right", "rollback", "rowcount", "rowguidcol", "rule", "save", "schema",
            "select", "session_user", "set", "setuser", "shutdown", "some", "statistics",
            "sum", "system_user", "table", "textsize", "then", "to", "top", "tran",
            "transaction", "trigger", "truncate", "tsequal", "union", "unique", "update",
            "updatetext", "use", "user", "values", "varying", "view", "waitfor", "when",
            "where", "while", "with", "writetext"
        ),
        LanguageMode.SHELL to setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done",
            "case", "esac", "function", "in", "select", "time", "return", "break",
            "continue", "local", "export", "readonly", "declare", "typeset", "unset",
            "shift", "eval", "exec", "exit", "source", "trap", "set", "unset", "echo",
            "cd", "read"
        ),
        LanguageMode.BATCH to setOf(
            "rem", "echo", "goto", "call", "exit", "if", "else", "for", "do", "set",
            "setlocal", "endlocal", "pause", "cls", "not", "exist", "defined", "equ",
            "neq", "lss", "leq", "gtr", "geq", "off", "on", "del", "copy", "move",
            "md", "mkdir", "rd", "rmdir", "start", "title"
        ),
        LanguageMode.INI to setOf("true", "false", "yes", "no", "on", "off", "null"),
        LanguageMode.PHP to setOf(
            "abstract", "and", "array", "as", "break", "callable", "case", "catch",
            "class", "clone", "const", "continue", "declare", "default", "do", "echo",
            "else", "elseif", "empty", "enddeclare", "endfor", "endforeach", "endif",
            "endswitch", "endwhile", "enum", "extends", "final", "finally", "fn", "for",
            "foreach", "function", "global", "goto", "if", "implements", "include",
            "include_once", "instanceof", "insteadof", "interface", "isset", "list",
            "match", "namespace", "new", "null", "or", "print", "private", "protected",
            "public", "readonly", "require", "require_once", "return", "static",
            "switch", "throw", "trait", "try", "unset", "use", "var", "while", "xor",
            "yield", "true", "false"
        ),
        LanguageMode.GO to setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
            "package", "range", "return", "select", "struct", "switch", "type", "var",
            "nil", "true", "false", "iota"
        ),
        LanguageMode.RUST to setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
            "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
            "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self",
            "static", "struct", "super", "trait", "true", "type", "unsafe", "use",
            "where", "while", "yield"
        ),
        LanguageMode.SWIFT to setOf(
            "associatedtype", "class", "deinit", "enum", "extension", "fileprivate",
            "func", "import", "init", "inout", "internal", "let", "open", "operator",
            "private", "protocol", "public", "rethrows", "static", "struct", "subscript",
            "typealias", "var", "break", "case", "catch", "continue", "default", "defer",
            "do", "else", "fallthrough", "for", "guard", "if", "in", "repeat", "return",
            "switch", "where", "while", "as", "false", "is", "nil", "self", "Self",
            "super", "throw", "throws", "true", "try"
        ),
        LanguageMode.RUBY to setOf(
            "BEGIN", "END", "alias", "and", "begin", "break", "case", "class", "def",
            "defined?", "do", "else", "elsif", "end", "ensure", "false", "for", "if",
            "in", "module", "next", "nil", "not", "or", "redo", "rescue", "retry",
            "return", "self", "super", "then", "true", "undef", "unless", "until",
            "when", "while", "yield", "attr_accessor", "attr_reader", "attr_writer",
            "require", "require_relative", "puts", "print", "lambda", "proc", "raise"
        ),
        LanguageMode.LUA to setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
            "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
            "true", "until", "while", "self"
        ),
        LanguageMode.MAKEFILE to setOf(
            "ifeq", "ifneq", "else", "endif", "include", "define", "endef", "export",
            "unexport", "override", "ifdef", "ifndef", ".PHONY", ".DEFAULT", ".PRECIOUS"
        ),
        LanguageMode.TOML to setOf(
            "true", "false", "inf", "nan"
        ),
        LanguageMode.POWERSHELL to setOf(
            "begin", "break", "catch", "class", "continue", "data", "define", "do",
            "dynamicparam", "else", "elseif", "end", "enum", "exit", "filter", "finally",
            "for", "foreach", "from", "function", "hidden", "if", "in", "param", "process",
            "return", "static", "switch", "throw", "trap", "try", "until", "using", "var",
            "while", "workflow", "yield", "alias", "new"
        ),
        LanguageMode.PERL to setOf(
            "my", "our", "local", "use", "no", "package", "sub", "BEGIN", "END", "if",
            "elsif", "else", "unless", "while", "until", "for", "foreach", "do", "last",
            "next", "redo", "return", "require", "eval", "and", "or", "not", "xor", "die",
            "warn", "print", "printf", "grep", "map", "sort", "keys", "values", "each", "qw"
        ),
        LanguageMode.CMAKE to setOf(
            "if", "elseif", "else", "endif", "foreach", "endforeach", "while", "endwhile",
            "function", "endfunction", "macro", "endmacro", "include", "return", "set",
            "unset", "cmake_minimum_required", "project", "add_executable", "add_library",
            "target_link_libraries", "target_include_directories", "find_package", "find_library",
            "find_path", "find_program", "message", "option", "add_subdirectory", "install",
            "set_target_properties", "target_sources", "add_custom_command", "TRUE", "FALSE"
        ),
        LanguageMode.VHDL to setOf(
            "abs", "access", "after", "alias", "all", "and", "architecture", "array", "assert",
            "attribute", "begin", "block", "body", "buffer", "bus", "case", "component",
            "configuration", "constant", "disconnect", "downto", "else", "elsif", "end",
            "entity", "exit", "file", "for", "function", "generate", "generic", "group",
            "guarded", "if", "impure", "in", "inertial", "inout", "is", "label", "library",
            "linkage", "literal", "loop", "map", "mod", "nand", "new", "next", "nor", "not",
            "null", "of", "on", "open", "or", "others", "out", "package", "port", "postponed",
            "procedure", "process", "pure", "range", "record", "register", "reject", "rem",
            "report", "return", "rol", "ror", "select", "severity", "signal", "shared",
            "sla", "sll", "sra", "srl", "subtype", "then", "to", "transport", "type",
            "unaffected", "units", "until", "use", "variable", "wait", "when", "while", "with",
            "xnor", "xor"
        ),
        LanguageMode.VERILOG to setOf(
            "always", "and", "assign", "begin", "buf", "bufif0", "bufif1", "case", "casex",
            "casez", "cmos", "deassign", "default", "defparam", "disable", "edge", "else",
            "end", "endcase", "endfunction", "endgenerate", "endmodule", "endprimitive",
            "endspecify", "endtable", "endtask", "event", "for", "forever", "fork", "function",
            "generate", "genvar", "if", "ifnone", "initial", "inout", "input", "integer",
            "join", "large", "localparam", "macromodule", "medium", "module", "nand", "negedge",
            "nmos", "nor", "not", "notif0", "notif1", "or", "output", "parameter", "pmos",
            "posedge", "primitive", "pull0", "pull1", "pulldown", "pullup", "rcmos", "real",
            "realtime", "reg", "release", "repeat", "rnmos", "rpmos", "rtran", "rtranif0",
            "rtranif1", "scalared", "small", "specify", "specparam", "strong0", "strong1",
            "supply0", "supply1", "table", "task", "time", "tran", "tranif0", "tranif1",
            "tri", "tri0", "tri1", "triand", "trior", "trireg", "vectored", "wait", "wand",
            "weak0", "weak1", "while", "wire", "wor", "xnor", "xor"
        ),
        LanguageMode.ASSEMBLY to setOf(
            "mov", "add", "sub", "mul", "div", "inc", "dec", "push", "pop", "call", "ret",
            "jmp", "cmp", "test", "je", "jne", "jz", "jnz", "jg", "jl", "jge", "jle", "ja",
            "jb", "jae", "jbe", "and", "or", "xor", "not", "shl", "shr", "lea", "int",
            "nop", "loop", "section", "segment", "global", "extern", "db", "dw", "dd", "dq",
            "resb", "resw", "equ", "times", "org", "bits"
        ),
        LanguageMode.AUTOIT to setOf(
            "And", "Or", "Not", "If", "Then", "Else", "ElseIf", "EndIf", "Select", "Case",
            "EndSelect", "Switch", "EndSwitch", "For", "Next", "To", "Step", "In", "While",
            "WEnd", "Do", "Until", "ContinueLoop", "ExitLoop", "Exit", "Dim", "Local", "Global",
            "Const", "ReDim", "Func", "EndFunc", "Return", "ByRef", "Volatile"
        ),
        LanguageMode.COFFEESCRIPT to setOf(
            "true", "false", "null", "this", "new", "delete", "typeof", "in", "instanceof",
            "super", "extends", "class", "return", "if", "else", "elseif", "then", "for",
            "while", "until", "loop", "break", "continue", "switch", "when", "unless", "and",
            "or", "not", "do", "try", "catch", "finally", "throw", "yes", "no", "on", "off"
        ),
        LanguageMode.LISP to setOf(
            "defun", "defvar", "defparameter", "defconstant", "setq", "setf", "let", "let*",
            "if", "cond", "when", "unless", "while", "do", "dolist", "dotimes", "loop",
            "progn", "lambda", "apply", "funcall", "return", "car", "cdr", "cons",
            "list", "atom", "eq", "null", "and", "or", "not", "t", "nil"
        ),
        LanguageMode.SCHEME to setOf(
            "define", "lambda", "let", "let*", "letrec", "if", "cond", "else", "when",
            "unless", "case", "do", "while", "loop", "begin", "set!", "and", "or", "not",
            "car", "cdr", "cons", "list", "null?", "eq?", "eqv?", "equal?", "apply", "map",
            "for-each", "quote", "quasiquote", "unquote"
        ),
        LanguageMode.PASCAL to setOf(
            "and", "array", "as", "asm", "begin", "case", "class", "const", "constructor",
            "destructor", "dispinterface", "div", "do", "downto", "else", "end", "except",
            "exports", "file", "finalization", "finally", "for", "function", "goto", "if",
            "implementation", "in", "inherited", "initialization", "inline", "interface", "is",
            "label", "library", "mod", "nil", "not", "object", "of", "or", "out", "packed",
            "procedure", "program", "property", "raise", "record", "repeat", "resourcestring",
            "set", "shl", "shr", "string", "then", "threadvar", "to", "try", "type", "unit",
            "until", "uses", "var", "while", "with", "xor"
        ),
        LanguageMode.ADA to setOf(
            "abort", "abs", "abstract", "accept", "access", "aliased", "all", "and", "array",
            "at", "begin", "body", "case", "constant", "declare", "delay", "delta", "digits",
            "do", "else", "elsif", "end", "entry", "exception", "exit", "for", "function",
            "generic", "goto", "if", "in", "interface", "is", "limited", "loop", "mod", "new",
            "not", "null", "of", "or", "others", "out", "overriding", "package", "pragma",
            "private", "procedure", "protected", "raise", "range", "record", "rem", "renames",
            "requeue", "return", "reverse", "select", "separate", "some", "subtype", "synchronized",
            "tagged", "task", "terminate", "then", "type", "until", "use", "when", "while",
            "with", "xor"
        ),
        LanguageMode.FORTRAN to setOf(
            "program", "end", "subroutine", "function", "if", "then", "else", "elseif",
            "endif", "do", "while", "enddo", "select", "case", "endselect", "implicit", "none",
            "integer", "real", "complex", "logical", "character", "dimension", "parameter",
            "intent", "in", "out", "inout", "call", "return", "stop", "pause", "cycle",
            "exit", "use", "module", "contains", "print", "read", "write", "format", "data",
            "common", "equivalence", "save", "allocatable", "allocate", "deallocate", "pointer",
            "target", "interface", "type", "public", "private", "namelist"
        ),
        LanguageMode.HASKELL to setOf(
            "case", "class", "data", "default", "deriving", "do", "else", "foreign", "if",
            "import", "in", "infix", "infixl", "infixr", "instance", "let", "module", "newtype",
            "of", "then", "type", "where", "mdo"
        ),
        LanguageMode.ERLANG to setOf(
            "after", "and", "andalso", "band", "begin", "bnot", "bor", "bsl", "bsr", "bxor",
            "case", "catch", "cond", "div", "end", "fun", "if", "let", "not", "of", "or",
            "orelse", "receive", "rem", "try", "when", "xor", "query"
        ),
        LanguageMode.R to setOf(
            "if", "else", "repeat", "while", "function", "for", "in", "next", "break", "TRUE",
            "FALSE", "NULL", "Inf", "NaN", "NA", "NA_integer_", "NA_real_", "NA_character_",
            "NA_complex_"
        ),
        LanguageMode.TCL to setOf(
            "proc", "if", "then", "elseif", "else", "switch", "default", "for", "foreach",
            "while", "break", "continue", "return", "eval", "catch", "error", "exec", "exit",
            "set", "unset", "global", "variable", "upvar", "uplevel", "source", "package",
            "require", "provide", "namespace", "incr", "append", "lappend", "puts", "open",
            "close", "gets", "read", "puts", "expr", "string", "list", "array", "regexp", "regsub"
        ),
        LanguageMode.MATLAB to setOf(
            "function", "end", "if", "else", "elseif", "switch", "case", "otherwise", "for",
            "while", "break", "continue", "return", "try", "catch", "global", "persistent",
            "classdef", "properties", "methods", "events", "arguments"
        ),
        LanguageMode.OBJECTIVE_C to setOf(
            "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
            "return", "goto", "id", "self", "super", "nil", "Nil", "YES", "NO", "interface",
            "implementation", "end", "class", "protocol", "property", "synthesize",
            "dynamic", "public", "private", "protected", "package", "required",
            "optional", "selector", "encode", "autoreleasepool", "try", "catch",
            "finally", "throw", "synchronized"
        ),
        LanguageMode.VISUALBASIC to setOf(
            "AddHandler", "AddressOf", "Alias", "And", "AndAlso", "As", "Boolean", "ByRef",
            "ByVal", "Call", "Case", "Catch", "Class", "Const", "Continue", "Dim", "Do",
            "Double", "Each", "Else", "ElseIf", "End", "Enum", "Erase", "Error", "Event",
            "Exit", "False", "Finally", "For", "Friend", "Function", "Get", "GetType", "GoTo",
            "Handles", "If", "Implements", "Imports", "In", "Inherits", "Integer", "Interface",
            "Is", "IsNot", "Let", "Lib", "Like", "Long", "Loop", "Me", "Mod", "Module",
            "MustInherit", "MustOverride", "MyBase", "MyClass", "Namespace", "New", "Next",
            "Not", "Nothing", "NotInheritable", "NotOverridable", "Object", "Of", "On",
            "Option", "Optional", "Or", "OrElse", "Overloads", "Overridable", "Overrides",
            "ParamArray", "Partial", "Private", "Property", "Protected", "Public", "RaiseEvent",
            "ReadOnly", "ReDim", "REM", "RemoveHandler", "Resume", "Return", "Select", "Set",
            "Shadows", "Shared", "Short", "Single", "Static", "Step", "Stop", "String",
            "Structure", "Sub", "SyncLock", "Then", "Throw", "To", "True", "Try", "TryCast",
            "TypeOf", "Until", "Using", "When", "While", "Widening", "With", "WithEvents",
            "WriteOnly", "Xor"
        ),
        LanguageMode.COBOL to setOf(
            "ACCEPT", "ACCESS", "ADD", "ADDRESS", "ADVANCING", "AFTER", "ALL", "ALPHANUMERIC",
            "ALTER", "ALTERNATE", "AND", "ARE", "AREA", "AREAS", "ARITHMETIC", "AS", "ASCENDING",
            "ASCII", "ASSIGN", "AT", "AUTHOR", "BINARY", "BLANK", "BLOCK", "BOTTOM", "BY",
            "CALL", "CANCEL", "CD", "CF", "CH", "CHARACTER", "CHARACTERS", "CLASS-DEF",
            "CLOCK-UNITS", "CLOSE", "COBOL", "CODE", "COLLATING", "COLUMN", "COMMA",
            "COMMON", "COMMUNICATION", "COMP", "COMP-1", "COMP-2", "COMP-3", "COMPUTATIONAL",
            "COMPUTE", "CONFIGURATION", "CONTAINS", "CONTENT", "CONTINUE", "CONTROL", "CONVERTING",
            "COPY", "CORR", "CORRESPONDING", "COUNT", "CURRENCY", "DATA", "DATE", "DATE-COMPILED",
            "DATE-WRITTEN", "DAY", "DEBUG", "DEBUGGING", "DECLARATIVES", "DELETE",
            "DELIMITED", "DELIMITER", "DEPENDING", "DESCENDING", "DESTINATION", "DETAIL",
            "DISPLAY", "DIVIDE", "DIVISION", "DOWN", "DUPLICATES", "DYNAMIC", "EGI", "ELSE",
            "EMI", "ENABLE", "END", "END-ADD", "END-CALL", "END-COMPUTE", "END-DELETE",
            "END-DIVIDE", "END-EVALUATE", "END-IF", "END-MULTIPLY", "END-OF-PAGE", "END-PERFORM",
            "END-READ", "END-RECEIVE", "END-RETURN", "END-REWRITE", "END-SEARCH", "END-START",
            "END-STRING", "END-SUBTRACT", "END-UNSTRING", "END-WRITE", "ENTER", "ENVIRONMENT",
            "EOP", "EQUAL", "ERROR", "ESI", "EVALUATE", "EXCEPTION", "EXCLUSIVE", "EXIT",
            "EXTEND", "EXTERNAL", "FD", "FILE", "FILE-CONTROL", "FILLER", "FINAL", "FIRST",
            "FOOTING", "FOR", "FROM", "FUNCTION", "GENERATE", "GIVING", "GLOBAL", "GO",
            "GOBACK", "GREATER", "GROUP", "HEADING", "HIGH-VALUE", "HIGH-VALUES", "I-O",
            "I-O-CONTROL", "IDENTIFICATION", "IF", "IN", "INDEX", "INDEXED", "INDICATE",
            "INITIAL", "INITIATE", "INPUT", "INPUT-OUTPUT", "INSPECT", "INSTALLATION",
            "INTO", "INVALID", "IS", "JUST", "JUSTIFIED", "KEY", "LABEL", "LAST", "LEADING",
            "LEFT", "LENGTH", "LESS", "LIMIT", "LIMITS", "LINAGE", "LINE", "LINE-COUNTER",
            "LINES", "LINKAGE", "LOCK", "LOW-VALUE", "LOW-VALUES", "MEMORY", "MERGE", "MESSAGE",
            "MODE", "MODULES", "MOVE", "MULTIPLE", "MULTIPLY", "NATIONAL", "NATIVE", "NEGATIVE",
            "NEXT", "NO", "NOT", "NULL", "NULLS", "NUMBER", "NUMERIC", "OBJECT-COMPUTER",
            "OCCURS", "OF", "OFF", "OFFSET", "OMITTED", "ON", "OPEN", "OPTIONAL", "OR",
            "ORDER", "ORGANIZATION", "OTHER", "OUTPUT", "OVERFLOW", "PACKED-DECIMAL", "PADDING",
            "PAGE", "PAGE-COUNTER", "PERFORM", "PF", "PH", "PIC", "PICTURE", "PLUS", "POINTER",
            "POSITION", "POSITIVE", "PRINTING", "PROCEDURE", "PROCEDURES", "PROCEED", "PROCESS",
            "PROGRAM", "PROGRAM-ID", "PROGRAM-STATUS", "PROHIBITED", "PROTECTED", "PURGE", "QUEUE",
            "QUOTE", "QUOTES", "RANDOM", "RD", "READ", "RECEIVE", "RECORD", "RECORDS",
            "REDEFINES", "REEL", "REFERENCE", "RELATIVE", "RELEASE", "RELOAD", "REMAINDER",
            "REMOVAL", "RENAMES", "REPLACE", "REPLACING", "REPORT", "REPORTING", "REPORTS",
            "REPOSITORY", "RERUN", "RESERVE", "RESET", "RETURN", "RETURNING", "REVERSED",
            "REWIND", "REWRITE", "RF", "RH", "RIGHT", "ROUNDED", "RUN", "SAME", "SD",
            "SEARCH", "SECTION", "SECURITY", "SEGMENT", "SEGMENT-LIMIT", "SELECT", "SEND",
            "SENTENCE", "SEPARATE", "SEQUENCE", "SEQUENTIAL", "SET", "SHARED", "SIGN",
            "SIZE", "SORT", "SORT-MERGE", "SOURCE", "SOURCE-COMPUTER", "SPACE", "SPACES",
            "SPECIAL-NAMES", "STANDARD", "STANDARD-1", "STANDARD-2", "START", "STATUS", "STOP",
            "STRING", "SUB-QUEUE-1", "SUB-QUEUE-2", "SUB-QUEUE-3", "SUBTRACT", "SUM", "SUPPRESS",
            "SYMBOLIC", "SYNC", "SYNCHRONIZED", "TABLE", "TALLYING", "TAPE", "TERMINAL",
            "TERMINATE", "TEST", "TEXT", "THAN", "THEN", "THROUGH", "THRU", "TIME", "TIMES",
            "TO", "TOP", "TRAILING", "TRUE", "TYPE", "UNIT", "UNSTRING", "UNTIL", "UP",
            "UPON", "USAGE", "USE", "USING", "VALUE", "VALUES", "VARYING", "WHEN", "WITH",
            "WORDS", "WORKING-STORAGE", "WRITE", "ZERO", "ZEROES", "ZEROS"
        ),
        LanguageMode.SMALLTALK to setOf(
            "self", "super", "nil", "true", "false", "thisContext", "ifTrue:", "ifFalse:",
            "ifTrue:ifFalse:", "whileTrue:", "whileFalse:", "to:do:", "and:", "or:", "new",
            "value", "do:", "collect:", "select:", "reject:", "detect:", "inject:into:", "at:",
            "at:put:", "class", "isNil", "notNil"
        ),
        LanguageMode.POSTSCRIPT to setOf(
            "def", "dup", "exch", "pop", "copy", "index", "roll", "clear", "count", "mark",
            "cleartomark", "counttomark", "add", "sub", "mul", "div", "idiv", "mod", "abs",
            "neg", "ceiling", "floor", "round", "truncate", "sqrt", "exp", "ln", "log", "sin",
            "cos", "atan", "rand", "srand", "if", "ifelse", "for", "repeat", "forall",
            "loop", "exit", "stop", "stopped", "newpath", "moveto", "lineto", "curveto",
            "arc", "closepath", "stroke", "fill", "showpage", "grestore", "gsave", "bind",
            "load", "store", "get", "put", "begin", "end"
        ),
        LanguageMode.D to setOf(
            "abstract", "alias", "align", "asm", "assert", "auto", "body", "bool", "break",
            "byte", "case", "cast", "catch", "cdouble", "cent", "cfloat", "char", "class",
            "const", "continue", "creal", "dchar", "debug", "default", "delegate", "delete",
            "deprecated", "do", "double", "else", "enum", "export", "extern", "false", "final",
            "finally", "float", "for", "foreach", "foreach_reverse", "function", "goto", "idouble",
            "if", "ifloat", "import", "in", "inout", "int", "interface", "invariant", "ireal",
            "is", "lazy", "long", "macro", "mixin", "module", "new", "nothrow", "null", "out",
            "override", "package", "pragma", "private", "protected", "public", "pure", "real",
            "ref", "return", "scope", "shared", "short", "static", "struct", "super", "switch",
            "synchronized", "template", "this", "throw", "true", "try", "typedef", "typeid",
            "typeof", "ubyte", "ucent", "uint", "ulong", "union", "unittest", "ushort", "version",
            "void", "volatile", "wchar", "while", "with", "__FILE__", "__LINE__"
        ),
        LanguageMode.NIM to setOf(
            "addr", "and", "as", "asm", "bind", "block", "break", "case", "cast", "concept",
            "const", "continue", "converter", "defer", "discard", "distinct", "div", "do",
            "elif", "else", "end", "enum", "except", "export", "finally", "for", "from", "func",
            "if", "import", "include", "interface", "is", "isnot", "iterator", "let", "macro",
            "method", "mixin", "mod", "nil", "not", "notin", "object", "of", "or", "out",
            "proc", "ptr", "raise", "ref", "return", "shl", "shr", "static", "template", "try",
            "tuple", "type", "using", "when", "while", "xor", "yield"
        ),
        LanguageMode.FORTH to setOf(
            ":", ";", "variable", "constant", "create", "does>", "if", "else", "then", "begin",
            "until", "again", "while", "repeat", "do", "loop", "+loop", "i", "j", "leave", "exit",
            "recurse", "dup", "drop", "swap", "over", "rot", "nip", "tuck", "emit",
            "cr", "space", "cells", "cell+", "@", "!", "+!", "and", "or", "xor", "invert",
            "=", "<", ">", "0=", "0<", "0>"
        ),
        LanguageMode.ACTIONSCRIPT to setOf(
            "break", "case", "catch", "class", "const", "continue", "default", "delete", "do",
            "else", "extends", "false", "finally", "for", "function", "if", "implements",
            "import", "in", "instanceof", "interface", "internal", "is", "namespace", "new",
            "null", "package", "private", "protected", "public", "return", "super", "switch",
            "this", "throw", "true", "try", "typeof", "use", "var", "void", "while", "with",
            "get", "set", "override", "final", "dynamic", "native", "static"
        ),
        LanguageMode.NFO to emptySet()
    )

    private val TYPES: Map<LanguageMode, Set<String>> = mapOf(
        LanguageMode.C to setOf(
            "char", "double", "float", "int", "long", "short", "signed", "unsigned",
            "void", "size_t", "FILE"
        ),
        LanguageMode.CPP to setOf(
            "char", "double", "float", "int", "long", "short", "signed", "unsigned",
            "void", "size_t", "FILE", "auto", "std", "string", "vector", "map", "set"
        ),
        LanguageMode.CSHARP to setOf("int", "uint", "long", "ulong", "short", "ushort", "bool", "char", "string", "object", "byte", "sbyte", "double", "float", "decimal"),
        LanguageMode.JAVA to setOf("int", "long", "short", "byte", "char", "boolean", "float", "double", "String", "Object", "Integer", "Long", "Void"),
        LanguageMode.KOTLIN to setOf("Int", "Long", "Short", "Byte", "Char", "Boolean", "Float", "Double", "String", "Unit", "Any", "Nothing", "List", "Map", "Set"),
        LanguageMode.PYTHON to setOf("int", "str", "float", "bool", "list", "dict", "set", "tuple", "bytes", "object"),
        LanguageMode.TYPESCRIPT to setOf("string", "number", "boolean", "never", "unknown", "any", "void", "object")
    )

    private data class LangRule(
        val lineComments: List<String>,
        val blockComment: Pair<String, String>?,
        val strings: List<Char>,
        val keywords: Set<String>,
        val types: Set<String>,
        val caseInsensitive: Boolean = false,
        val preprocessor: String? = null,
        val hashLineComment: Boolean = false
    )

    private fun rulesFor(mode: LanguageMode): LangRule {
        val keywords = KEYWORDS[mode] ?: emptySet()
        val types = TYPES[mode] ?: emptySet()
        return when (mode) {
            LanguageMode.C, LanguageMode.CPP ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '\''), keywords, types, preprocessor = "#")
            LanguageMode.CSHARP ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '\''), keywords, types, preprocessor = "#")
            LanguageMode.JAVA ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '\''), keywords, types)
            LanguageMode.KOTLIN ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '\''), keywords, types)
            LanguageMode.PYTHON ->
                LangRule(listOf("#"), "\"\"\"" to "\"\"\"", listOf('"', '\''), keywords, types, hashLineComment = true)
            LanguageMode.JAVASCRIPT, LanguageMode.TYPESCRIPT ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '\'', '`'), keywords, types)
            LanguageMode.SHELL ->
                LangRule(listOf("#"), null, listOf('"', '\''), keywords, emptySet(), hashLineComment = true)
            LanguageMode.RUBY ->
                LangRule(listOf("#"), "=begin" to "=end", listOf('"', '\''), keywords, emptySet(), hashLineComment = true)
            LanguageMode.BATCH ->
                LangRule(listOf("rem", "::"), null, listOf('"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.INI ->
                LangRule(listOf(";", "#"), null, emptyList(), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.PHP ->
                LangRule(listOf("//", "#"), "/*" to "*/", listOf('"', '\''), keywords, emptySet())
            LanguageMode.GO, LanguageMode.RUST, LanguageMode.SWIFT ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '`'), keywords, emptySet())
            LanguageMode.LUA ->
                LangRule(listOf("--"), "--[[" to "]]", listOf('"', '\''), keywords, emptySet())
            LanguageMode.MAKEFILE ->
                LangRule(listOf("#"), null, emptyList(), keywords, emptySet())
            LanguageMode.TOML ->
                LangRule(listOf("#"), null, listOf('"', '\''), keywords, emptySet())
            LanguageMode.POWERSHELL ->
                LangRule(listOf("#"), "<#" to "#>", listOf('"', '\'', '`'), keywords, emptySet())
            LanguageMode.PERL ->
                LangRule(listOf("#"), null, listOf('"', '\''), keywords, emptySet())
            LanguageMode.CMAKE ->
                LangRule(listOf("#"), null, listOf('"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.VHDL ->
                LangRule(listOf("--"), null, listOf('"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.VERILOG ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.ASSEMBLY ->
                LangRule(listOf(";"), "/*" to "*/", listOf('\'', '"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.AUTOIT ->
                LangRule(listOf(";"), "#cs" to "#ce", listOf('"', '\''), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.COFFEESCRIPT ->
                LangRule(listOf("#"), "###" to "###", listOf('"', '\'', '`'), keywords, emptySet())
            LanguageMode.LISP, LanguageMode.SCHEME ->
                LangRule(listOf(";"), "#|" to "|#", listOf('"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.PASCAL, LanguageMode.ADA ->
                LangRule(listOf("//", "--"), "{" to "}", listOf('\'', '"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.FORTRAN ->
                LangRule(listOf("!"), null, listOf('"', '\''), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.HASKELL ->
                LangRule(listOf("--"), "{-" to "-}", listOf('"', '\''), keywords, emptySet())
            LanguageMode.ERLANG ->
                LangRule(listOf("%"), null, listOf('"', '\''), keywords, emptySet())
            LanguageMode.R ->
                LangRule(listOf("#"), null, listOf('"', '\'', '`'), keywords, emptySet())
            LanguageMode.TCL ->
                LangRule(listOf("#"), null, listOf('"'), keywords, emptySet())
            LanguageMode.MATLAB ->
                LangRule(listOf("%"), "%{" to "%}", listOf('\''), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.OBJECTIVE_C ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '\''), keywords, emptySet())
            LanguageMode.VISUALBASIC ->
                LangRule(listOf("'", "REM"), null, listOf('"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.COBOL ->
                LangRule(emptyList(), null, listOf('\'', '"'), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.SMALLTALK ->
                LangRule(listOf("\""), null, listOf('\''), keywords, emptySet())
            LanguageMode.POSTSCRIPT ->
                LangRule(listOf("%"), null, listOf('"', '('), keywords, emptySet())
            LanguageMode.D ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '\'', '`'), keywords, emptySet())
            LanguageMode.NIM ->
                LangRule(listOf("#"), "#[" to "]#", listOf('"', '\'', '`'), keywords, emptySet())
            LanguageMode.FORTH ->
                LangRule(listOf("\\"), null, emptyList(), keywords, emptySet(), caseInsensitive = true)
            LanguageMode.ACTIONSCRIPT ->
                LangRule(listOf("//"), "/*" to "*/", listOf('"', '\''), keywords, emptySet())
            LanguageMode.SQL ->
                LangRule(listOf("--"), "/*" to "*/", listOf('\''), keywords, emptySet(), caseInsensitive = true)
            else -> LangRule(emptyList(), null, emptyList(), keywords, emptySet())
        }
    }

    fun tokenize(text: String, mode: LanguageMode): List<SyntaxToken> {
        return when (mode) {
            LanguageMode.MARKDOWN -> tokenizeMarkdown(text)
            LanguageMode.HTML, LanguageMode.XML -> tokenizeMarkup(text, mode == LanguageMode.XML)
            LanguageMode.JSON -> tokenizeJson(text)
            LanguageMode.CSS -> tokenizeCss(text)
            LanguageMode.YAML -> tokenizeYaml(text)
            LanguageMode.DIFF -> tokenizeDiff(text)
            LanguageMode.NORMAL_TEXT, LanguageMode.TEX, LanguageMode.NFO -> emptyList()
            else -> tokenizeGeneric(text, rulesFor(mode))
        }
    }

    /** Diff/patch: per-line markers (+, -, @@, diff/index/---/+++ headers). */
    private fun tokenizeDiff(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var lineStart = 0
        val n = text.length
        while (lineStart <= n) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) n else it }
            if (lineEnd > lineStart) {
                val c = text[lineStart]
                val kind = when {
                    text.startsWith("@@", lineStart) -> TokenKind.HEADING
                    text.startsWith("diff ", lineStart) || text.startsWith("index ", lineStart) ||
                        text.startsWith("--- ", lineStart) || text.startsWith("+++ ", lineStart) ||
                        text.startsWith("Index: ", lineStart) -> TokenKind.COMMENT
                    c == '+' -> TokenKind.STRING
                    c == '-' -> TokenKind.ATTRIBUTE
                    else -> null
                }
                if (kind != null) tokens.add(SyntaxToken(lineStart, lineEnd, kind))
            }
            if (lineEnd >= n) break
            lineStart = lineEnd + 1
        }
        return tokens
    }

    private fun tokenizeGeneric(text: String, rule: LangRule): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        val n = text.length
        val word = StringBuilder()
        val lineCommentStarts = rule.lineComments.sortedByDescending { it.length }

        fun flushWord(start: Int) {
            if (word.isEmpty()) return
            val w = word.toString()
            val cmp = if (rule.caseInsensitive) w.lowercase() else w
            val keywordHit = rule.keywords.contains(cmp) ||
                (rule.caseInsensitive && rule.keywords.any { it.equals(w, ignoreCase = true) })
            val kind = when {
                rule.types.contains(cmp) && !keywordHit -> TokenKind.TYPE
                keywordHit -> TokenKind.KEYWORD
                else -> null
            }
            if (kind != null) tokens.add(SyntaxToken(start, i, kind))
            word.setLength(0)
        }

        while (i < n) {
            val c = text[i]
            val rest = text.substring(i, minOf(n, i + 3))

            if (rule.preprocessor != null && c == rule.preprocessor[0] && (i == 0 || text[i - 1] == '\n')) {
                flushWord(i)
                var j = i + 1
                while (j < n && text[j] != '\n') j++
                if (j > i + 1) tokens.add(SyntaxToken(i, j, TokenKind.PREPROCESSOR))
                i = j
                continue
            }
            val lc = lineCommentStarts.firstOrNull { rest.startsWith(it) }
            if (lc != null && (lc != "rem" || i == 0 || text[i - 1] == '\n') && isWordBoundaryBefore(text, i)) {
                flushWord(i)
                var j = text.indexOf('\n', i)
                if (j < 0) j = n
                tokens.add(SyntaxToken(i, j, TokenKind.COMMENT))
                i = j
                continue
            }
            if (rule.blockComment != null && rest.startsWith(rule.blockComment.first)) {
                flushWord(i)
                val close = rule.blockComment.second
                var j = text.indexOf(close, i + rule.blockComment.first.length)
                j = if (j < 0) n else j + close.length
                tokens.add(SyntaxToken(i, j, TokenKind.COMMENT))
                i = j
                continue
            }
            if (rule.strings.contains(c) && c != '\n') {
                flushWord(i)
                var j = i + 1
                while (j < n) {
                    if (text[j] == '\\' && j + 1 < n) { j += 2; continue }
                    if (text[j] == c || text[j] == '\n') break
                    j++
                }
                if (j < n && text[j] == c) j++
                tokens.add(SyntaxToken(i, minOf(j, n), TokenKind.STRING))
                i = j
                continue
            }
            if (c.isDigit() && isWordBoundaryBefore(text, i)) {
                flushWord(i)
                var j = i
                while (j < n && (text[j].isDigit() || text[j] == '.' || text[j] == '_')) j++
                if (j < n && (text[j] == 'e' || text[j] == 'E')) {
                    j++
                    if (j < n && (text[j] == '+' || text[j] == '-')) j++
                    while (j < n && text[j].isDigit()) j++
                }
                if (j < n && (text[j] == 'x' || text[j] == 'X')) {
                    j++
                    while (j < n && (text[j].isDigit() || text[j] in 'a'..'f' || text[j] in 'A'..'F')) j++
                }
                tokens.add(SyntaxToken(i, j, TokenKind.NUMBER))
                i = j
                continue
            }
            if (c.isLetter() || c == '_') {
                word.append(c)
                i++
                while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) {
                    word.append(text[i])
                    i++
                }
                flushWord(i - word.length)
                continue
            }
            if (c in "+-*/%=<>!&|^~?:") {
                flushWord(i)
                tokens.add(SyntaxToken(i, i + 1, TokenKind.OPERATOR))
            } else {
                flushWord(i)
            }
            i++
        }
        flushWord(n)
        return tokens
    }

    private fun isWordBoundaryBefore(text: String, i: Int): Boolean {
        if (i == 0) return true
        val p = text[i - 1]
        return !p.isLetterOrDigit() && p != '_'
    }

    private fun tokenizeMarkup(text: String, xml: Boolean): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        val n = text.length
        while (i < n) {
            val lt = text.indexOf('<', i)
            if (lt < 0) break
            if (text.startsWith("<!--", lt)) {
                val end = text.indexOf("-->", lt + 4)
                val stop = if (end < 0) n else end + 3
                tokens.add(SyntaxToken(lt, stop, TokenKind.COMMENT))
                i = stop
                continue
            }
            val gt = text.indexOf('>', lt)
            if (gt < 0) break
            val isDecl = text.startsWith("<!", lt)
            val isPi = text.startsWith("<?", lt)
            val inner = text.substring(lt + 1, gt)
            if (!isDecl && !isPi && inner.isEmpty()) { i = gt + 1; continue }

            val tagEnd = inner.indexOfFirst { it == ' ' || it == '\t' || it == '\n' || it == '/' }
            val tagNameEndInTag = if (tagEnd < 0) inner.length else tagEnd
            val nameStart = lt + 1 + (if (isDecl || isPi) 1 else 0) + (if (inner.startsWith("/")) 1 else 0)
            val nameLen = tagNameEndInTag - (if (inner.startsWith("/")) 1 else 0) - (if (isDecl || (isPi && isPIEnd(inner, true))) 1 else 0)
            if (nameLen > 0) tokens.add(SyntaxToken(nameStart, nameStart + nameLen, TokenKind.TAG))

            // attributes
            var j = lt + 1 + tagNameEndInTag + (if (inner.startsWith("/")) 1 else 0)
            while (j < gt) {
                while (j < gt && (text[j] == ' ' || text[j] == '\t' || text[j] == '\n' || text[j] == '\r' || text[j] == '/')) j++
                if (j >= gt) break
                val attrStart = j
                while (j < gt && text[j] != '=' && text[j] != ' ' && text[j] != '\t' && text[j] != '\n' && text[j] != '/') j++
                if (j > attrStart) tokens.add(SyntaxToken(attrStart, j, TokenKind.ATTRIBUTE))
                if (j < gt && text[j] == '=') {
                    j++
                    val q = text.getOrNull(j)
                    if (q == '"' || q == '\'') {
                        val endQ = text.indexOf(q!!, j + 1).let { if (it < 0 || it >= gt + 1) gt else it }
                        if (endQ > j + 1) tokens.add(SyntaxToken(j + 1, endQ, TokenKind.VALUE))
                        j = minOf(endQ + 1, gt)
                    } else {
                        val vs = j
                        while (j < gt && text[j] != ' ' && text[j] != '\t' && text[j] != '\n') j++
                        if (j > vs) tokens.add(SyntaxToken(vs, j, TokenKind.VALUE))
                    }
                }
            }
            i = gt + 1
        }
        return tokens
    }

    private fun isPIEnd(inner: String, isPI: Boolean): Boolean = isPI && inner.endsWith("?")

    private fun tokenizeJson(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        val n = text.length
        var lastKeyEnd = -1
        while (i < n) {
            val c = text[i]
            when {
                c == '"' -> {
                    var j = i + 1
                    while (j < n && text[j] != '"') {
                        if (text[j] == '\\') j++
                        j++
                    }
                    j = minOf(j + 1, n)
                    val isKey = text.getOrNull(j) == ':'
                    if (isKey) tokens.add(SyntaxToken(i, j, TokenKind.ATTRIBUTE))
                    else tokens.add(SyntaxToken(i, j, TokenKind.STRING))
                    if (isKey) lastKeyEnd = j
                    i = j
                }
                c.isDigit() || (c == '-' && text.getOrNull(i + 1)?.isDigit() == true) -> {
                    var j = i + 1
                    while (j < n && (text[j].isDigit() || text[j] == '.' || text[j] == 'e' || text[j] == 'E' || text[j] == '+' || text[j] == '-')) j++
                    tokens.add(SyntaxToken(i, j, TokenKind.NUMBER))
                    i = j
                }
                c == 't' && text.startsWith("true", i) -> { tokens.add(SyntaxToken(i, i + 4, TokenKind.KEYWORD)); i += 4 }
                c == 'f' && text.startsWith("false", i) -> { tokens.add(SyntaxToken(i, i + 5, TokenKind.KEYWORD)); i += 5 }
                c == 'n' && text.startsWith("null", i) -> { tokens.add(SyntaxToken(i, i + 4, TokenKind.KEYWORD)); i += 4 }
                else -> i++
            }
        }
        return tokens
    }

    private fun tokenizeCss(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2)
                    val stop = if (end < 0) n else end + 2
                    tokens.add(SyntaxToken(i, stop, TokenKind.COMMENT))
                    i = stop
                }
                c == '\'' || c == '"' -> {
                    var j = i + 1
                    while (j < n && text[j] != c && text[j] != '\n') j++
                    j = minOf(j + 1, n)
                    tokens.add(SyntaxToken(i, j, TokenKind.STRING))
                    i = j
                }
                c == '#' && i + 1 < n && text[i + 1].isLetterOrDigit() -> {
                    var j = i + 1
                    while (j < n && (text[j].isLetterOrDigit())) j++
                    tokens.add(SyntaxToken(i, j, TokenKind.NUMBER))
                    i = j
                }
                c == '.' && i + 1 < n && text[i + 1].isLetter() -> {
                    var j = i + 1
                    while (j < n && (text[j].isLetterOrDigit() || text[j] == '-' || text[j] == '_')) j++
                    tokens.add(SyntaxToken(i, j, TokenKind.ATTRIBUTE))
                    i = j
                }
                c == '@' -> {
                    var j = i + 1
                    while (j < n && text[j].isLetterOrDigit()) j++
                    tokens.add(SyntaxToken(i, j, TokenKind.KEYWORD))
                    i = j
                }
                c == '-' || c.isDigit() -> {
                    var j = i + 1
                    while (j < n && (text[j].isDigit() || text[j] == '.')) j++
                    tokens.add(SyntaxToken(i, j, TokenKind.NUMBER))
                    i = j
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun tokenizeYaml(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '#' && (i == 0 || text[i - 1] == ' ' || text[i - 1] == '\n') -> {
                    var j = text.indexOf('\n', i)
                    if (j < 0) j = n
                    tokens.add(SyntaxToken(i, j, TokenKind.COMMENT))
                    i = j
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < n && text[j] != c && text[j] != '\n') j++
                    j = minOf(j + 1, n)
                    tokens.add(SyntaxToken(i, j, TokenKind.STRING))
                    i = j
                }
                c == ':' && (text.getOrNull(i + 1) == ' ' || text.getOrNull(i + 1) == '\n') -> {
                    val keyStart = i - 1
                    var s = keyStart
                    while (s > 0 && text[s - 1] != ' ' && text[s - 1] != '\n' && text[s - 1] != '-') s--
                    if (i > s) tokens.add(SyntaxToken(s, i, TokenKind.ATTRIBUTE))
                    tokens.add(SyntaxToken(i, i + 1, TokenKind.OPERATOR))
                    i++
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun tokenizeMarkdown(text: String): List<SyntaxToken> {
        val tokens = mutableListOf<SyntaxToken>()
        var i = 0
        val n = text.length
        var lineStart = 0
        while (i <= n) {
            if (i == n || text[i] == '\n') {
                val line = text.substring(lineStart, i)
                tokenizeMarkdownLine(tokens, text, lineStart, line)
                if (i == n) break
                lineStart = i + 1
            }
            i++
        }
        // inline: code spans and links
        var j = 0
        while (j < n) {
            val c = text[j]
            if (c == '`') {
                val end = text.indexOf('`', j + 1)
                if (end > j) {
                    tokens.add(SyntaxToken(j, end + 1, TokenKind.ESCAPE))
                    j = end + 1
                    continue
                }
            }
            if (c == '[') {
                val close = text.indexOf(']', j + 1)
                if (close > 0) {
                    tokens.add(SyntaxToken(j, close + 1, TokenKind.LINK))
                    j = close + 1
                    continue
                }
            }
            j++
        }
        return tokens
    }

    private fun tokenizeMarkdownLine(tokens: MutableList<SyntaxToken>, text: String, start: Int, line: String) {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        if (trimmed.startsWith("#")) {
            tokens.add(SyntaxToken(start + indent, start + line.length, TokenKind.HEADING))
        } else if (trimmed.startsWith(">")) {
            tokens.add(SyntaxToken(start + indent, start + line.length, TokenKind.COMMENT))
        } else if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            tokens.add(SyntaxToken(start + indent, start + line.length, TokenKind.ESCAPE))
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            tokens.add(SyntaxToken(start + indent, start + indent + 2, TokenKind.OPERATOR))
        } else if (Regex("^(\\d+)\\. ").containsMatchIn(trimmed)) {
            val m = Regex("^(\\d+)\\. ").find(trimmed)!!
            tokens.add(SyntaxToken(start + indent, start + indent + m.value.length, TokenKind.OPERATOR))
        }
    }
}
