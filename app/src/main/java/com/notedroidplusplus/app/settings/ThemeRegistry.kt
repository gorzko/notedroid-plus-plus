package com.notedroidplusplus.app.settings

/**
 * Notepad++ theme registry. Colors model the desktop stylers.xml values
 * (default theme plus the named themes shipped with Notepad++). Each theme
 * carries the full palette needed by the editor: chrome (background, tab
 * strip, gutter) and syntax token colors.
 */
data class EditorTheme(
    val name: String,
    val dark: Boolean,
    val appBg: Int,
    val toolbarBg: Int,
    val tabBg: Int,
    val tabText: Int,
    val tabSelectedText: Int,
    val tabIndicator: Int,
    val editorBg: Int,
    val editorText: Int,
    val gutterBg: Int,
    val gutterText: Int,
    val currentLine: Int,
    val bracket: Int,
    val statusBg: Int,
    val statusText: Int,
    val syntax: Map<com.notedroidplusplus.app.TokenKind, Int>
)

object ThemeRegistry {

    private fun rgb(hex: Long): Int = hex.toInt() or 0xFF000000.toInt()

    private fun syntax(vararg pairs: Pair<com.notedroidplusplus.app.TokenKind, Long>) =
        pairs.associate { it.first to rgb(it.second) }

    /** Notepad++ default (stylers.model.xml) light theme. */
    val DEFAULT = EditorTheme(
        name = "Default",
        dark = false,
        appBg = rgb(0xC8C8C8),
        toolbarBg = rgb(0x316AC5),
        tabBg = rgb(0xE0E0E0),
        tabText = rgb(0x404040),
        tabSelectedText = rgb(0x000000),
        tabIndicator = rgb(0xFF9A2B),
        editorBg = rgb(0xFFFFFF),
        editorText = rgb(0x000000),
        gutterBg = rgb(0xE9E9E9),
        gutterText = rgb(0x6E6E6E),
        currentLine = rgb(0xE8E8FF),
        bracket = rgb(0x808080),
        statusBg = rgb(0xE0E0E0),
        statusText = rgb(0x404040),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0x0000FF,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x0000FF,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0xFF8000,
            com.notedroidplusplus.app.TokenKind.STRING to 0x008000,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x008000,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0x000080,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0x8000FF,
            com.notedroidplusplus.app.TokenKind.TAG to 0x0000FF,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0xFF0000,
            com.notedroidplusplus.app.TokenKind.VALUE to 0x800000,
            com.notedroidplusplus.app.TokenKind.HEADING to 0x000080,
            com.notedroidplusplus.app.TokenKind.LINK to 0x0000FF,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0x804000
        )
    )

    /** Notepad++ "Bespoke" dark theme. */
    val BESPOKE = EditorTheme(
        name = "Bespoke",
        dark = true,
        appBg = rgb(0x1E1E1E),
        toolbarBg = rgb(0x2D2D30),
        tabBg = rgb(0x252526),
        tabText = rgb(0xAAAAAA),
        tabSelectedText = rgb(0xFFFFFF),
        tabIndicator = rgb(0xFF9A2B),
        editorBg = rgb(0x1E1E1E),
        editorText = rgb(0xDCDCDC),
        gutterBg = rgb(0x252526),
        gutterText = rgb(0x858585),
        currentLine = rgb(0x2A2A40),
        bracket = rgb(0xAAAAAA),
        statusBg = rgb(0x252526),
        statusText = rgb(0xAAAAAA),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0x569CD6,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x4EC9B0,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0xB5CEA8,
            com.notedroidplusplus.app.TokenKind.STRING to 0xD69D85,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x57A64A,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0x9CDCFE,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0xC586C0,
            com.notedroidplusplus.app.TokenKind.TAG to 0x569CD6,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0x9CDCFE,
            com.notedroidplusplus.app.TokenKind.VALUE to 0xCE9178,
            com.notedroidplusplus.app.TokenKind.HEADING to 0x569CD6,
            com.notedroidplusplus.app.TokenKind.LINK to 0x4FC1FF,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0xB5CEA8
        )
    )

    /** Notepad++ "Black board" theme. */
    val BLACK_BOARD = DEFAULT.copy(
        name = "Black board",
        dark = true,
        appBg = rgb(0x000000),
        toolbarBg = rgb(0x1B1B1B),
        tabBg = rgb(0x141414),
        tabText = rgb(0x8F8F8F),
        tabSelectedText = rgb(0xE0E0E0),
        editorBg = rgb(0x000000),
        editorText = rgb(0xC8C8C8),
        gutterBg = rgb(0x141414),
        gutterText = rgb(0x6E6E6E),
        currentLine = rgb(0x222222),
        statusBg = rgb(0x141414),
        statusText = rgb(0x8F8F8F),
        syntax = BESPOKE.syntax
    )

    /** Notepad++ "Choco" theme. */
    val CHOCO = DEFAULT.copy(
        name = "Choco",
        dark = true,
        appBg = rgb(0x2B211B),
        toolbarBg = rgb(0x3B2E24),
        tabBg = rgb(0x33291F),
        tabText = rgb(0xA98D6E),
        tabSelectedText = rgb(0xE0C9A6),
        editorBg = rgb(0x2B211B),
        editorText = rgb(0xC8C8C8),
        gutterBg = rgb(0x33291F),
        gutterText = rgb(0x8A7A66),
        currentLine = rgb(0x3B2E24),
        statusBg = rgb(0x33291F),
        statusText = rgb(0xA98D6E),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0xF1CE94,
            com.notedroidplusplus.app.TokenKind.TYPE to 0xAFC2E0,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0xD48E7B,
            com.notedroidplusplus.app.TokenKind.STRING to 0x9BC48E,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x6B5B4C,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0xC8A0A0,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0xE0B0FF,
            com.notedroidplusplus.app.TokenKind.TAG to 0xF1CE94,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0xE0C9A6,
            com.notedroidplusplus.app.TokenKind.VALUE to 0xD48E7B,
            com.notedroidplusplus.app.TokenKind.HEADING to 0xF1CE94,
            com.notedroidplusplus.app.TokenKind.LINK to 0x7BA1C8,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0xC8B090
        )
    )

    /** Notepad++ "Deep Black" theme. */
    val DEEP_BLACK = BLACK_BOARD.copy(
        name = "Deep Black",
        toolbarBg = rgb(0x0A0A0A),
        tabBg = rgb(0x0F0F0F),
        editorBg = rgb(0x0A0A0A),
        gutterBg = rgb(0x0F0F0F),
        currentLine = rgb(0x1A1A1A)
    )

    /** Notepad++ "khaki" theme. */
    val KHAKI = DEFAULT.copy(
        name = "khaki",
        dark = false,
        appBg = rgb(0xE8E0C9),
        toolbarBg = rgb(0x7F9F5F),
        tabBg = rgb(0xDDD5BE),
        tabText = rgb(0x4A4433),
        tabSelectedText = rgb(0x2A2413),
        editorBg = rgb(0xFFF5E5),
        editorText = rgb(0x3A3423),
        gutterBg = rgb(0xE0D8C1),
        gutterText = rgb(0x8A7A66),
        currentLine = rgb(0xEEE4CE),
        statusBg = rgb(0xDDD5BE),
        statusText = rgb(0x4A4433),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0x005F5F,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x2B5F7F,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0x8F5F3F,
            com.notedroidplusplus.app.TokenKind.STRING to 0x5F7F3F,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x7F7F5F,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0x4A4433,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0x6F4F8F,
            com.notedroidplusplus.app.TokenKind.TAG to 0x005F5F,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0x8F3F3F,
            com.notedroidplusplus.app.TokenKind.VALUE to 0x7F4F3F,
            com.notedroidplusplus.app.TokenKind.HEADING to 0x005F5F,
            com.notedroidplusplus.app.TokenKind.LINK to 0x3F5F8F,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0x6F5F3F
        )
    )

    /** Notepad++ "Mono Industrial" theme. */
    val MONO_INDUSTRIAL = BESPOKE.copy(
        name = "Mono Industrial",
        appBg = rgb(0x1E1E1E),
        toolbarBg = rgb(0x2F4F4F),
        tabBg = rgb(0x232323),
        editorBg = rgb(0x1E1E1E),
        gutterBg = rgb(0x232323),
        currentLine = rgb(0x2F2F2F),
        statusBg = rgb(0x232323),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0x96CBFE,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x86C0D0,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0xC0C0C0,
            com.notedroidplusplus.app.TokenKind.STRING to 0x999999,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x5C5C5C,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0xB0B0B0,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0x8CBF6F,
            com.notedroidplusplus.app.TokenKind.TAG to 0x96CBFE,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0x9CBFE0,
            com.notedroidplusplus.app.TokenKind.VALUE to 0xC0C0A0,
            com.notedroidplusplus.app.TokenKind.HEADING to 0x96CBFE,
            com.notedroidplusplus.app.TokenKind.LINK to 0x7F9FBF,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0xA0A0A0
        )
    )

    /** Notepad++ "MossyLawn" theme. */
    val MOSSY_LAWN = KHAKI.copy(
        name = "MossyLawn",
        appBg = rgb(0xE0E8D0),
        toolbarBg = rgb(0x5F7F5F),
        tabBg = rgb(0xD5DDC5),
        editorBg = rgb(0xF5FAE5),
        gutterBg = rgb(0xD8E0C8),
        currentLine = rgb(0xE8F0D8),
        statusBg = rgb(0xD5DDC5)
    )

    /** Notepad++ "Navajo" theme. */
    val NAVAJO = KHAKI.copy(
        name = "Navajo",
        appBg = rgb(0xFFEBC8),
        toolbarBg = rgb(0x9F6F4F),
        tabBg = rgb(0xF5DDB5),
        tabText = rgb(0x5F4A33),
        tabSelectedText = rgb(0x3F2A13),
        editorBg = rgb(0xFFF8EC),
        editorText = rgb(0x3F3423),
        gutterBg = rgb(0xF0D8B0),
        gutterText = rgb(0x9F8A66),
        currentLine = rgb(0xF8F0DC),
        statusBg = rgb(0xF5DDB5),
        statusText = rgb(0x5F4A33)
    )

    /** Notepad++ "Plastic Code Wrap" theme. */
    val PLASTIC_CODE_WRAP = BESPOKE.copy(
        name = "Plastic Code Wrap",
        appBg = rgb(0x1E1E1E),
        toolbarBg = rgb(0x2A2A2A),
        tabBg = rgb(0x222222),
        editorBg = rgb(0x1E1E1E),
        gutterBg = rgb(0x222222),
        currentLine = rgb(0x2E2E2E),
        statusBg = rgb(0x222222),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0x00C0FF,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x00FFC0,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0xFFFF00,
            com.notedroidplusplus.app.TokenKind.STRING to 0xA0FFA0,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x7F7F7F,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0xB0B0B0,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0xFFA0FF,
            com.notedroidplusplus.app.TokenKind.TAG to 0x00C0FF,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0x60FF60,
            com.notedroidplusplus.app.TokenKind.VALUE to 0xFFC060,
            com.notedroidplusplus.app.TokenKind.HEADING to 0x00C0FF,
            com.notedroidplusplus.app.TokenKind.LINK to 0x60A0FF,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0xFFD070
        )
    )

    /** Notepad++ "Rybicki" theme. */
    val RYBICKI = DEFAULT.copy(
        name = "Rybicki",
        dark = false,
        appBg = rgb(0xF5F5F0),
        toolbarBg = rgb(0x3B5F7F),
        tabBg = rgb(0xEAEAE0),
        editorBg = rgb(0xF8F8F2),
        gutterBg = rgb(0xEEEEDD),
        currentLine = rgb(0xF0F0E0),
        statusBg = rgb(0xEAEAE0)
    )

    /** Notepad++ "Solarized" theme (light variant). */
    val SOLARIZED = DEFAULT.copy(
        name = "Solarized",
        dark = false,
        appBg = rgb(0xEEE8D5),
        toolbarBg = rgb(0x93A1A1),
        tabBg = rgb(0xE4E1CC),
        tabText = rgb(0x586E75),
        tabSelectedText = rgb(0x073642),
        editorBg = rgb(0xFDF6E3),
        editorText = rgb(0x657B83),
        gutterBg = rgb(0xEAE4D0),
        gutterText = rgb(0x93A1A1),
        currentLine = rgb(0xF2EDDC),
        statusBg = rgb(0xE4E1CC),
        statusText = rgb(0x586E75),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0x859900,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x268BD2,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0xD33682,
            com.notedroidplusplus.app.TokenKind.STRING to 0x2AA198,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x93A1A1,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0x586E75,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0x6C71C4,
            com.notedroidplusplus.app.TokenKind.TAG to 0x268BD2,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0xB58900,
            com.notedroidplusplus.app.TokenKind.VALUE to 0xCB4B16,
            com.notedroidplusplus.app.TokenKind.HEADING to 0x268BD2,
            com.notedroidplusplus.app.TokenKind.LINK to 0x6C71C4,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0xB58900
        )
    )

    /** Notepad++ "Twilight" theme. */
    val TWILIGHT = BESPOKE.copy(
        name = "Twilight",
        appBg = rgb(0x141414),
        toolbarBg = rgb(0x2B2B2B),
        tabBg = rgb(0x1B1B1B),
        editorBg = rgb(0x141414),
        gutterBg = rgb(0x1B1B1B),
        gutterText = rgb(0x5F5A5F),
        currentLine = rgb(0x22221E),
        statusBg = rgb(0x1B1B1B),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0x8F9D6A,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x9B859D,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0x5F8700,
            com.notedroidplusplus.app.TokenKind.STRING to 0xCDA869,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x5F5A5F,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0x8F9D6A,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0xAC4141,
            com.notedroidplusplus.app.TokenKind.TAG to 0x8F9D6A,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0xAC4141,
            com.notedroidplusplus.app.TokenKind.VALUE to 0xD87B7B,
            com.notedroidplusplus.app.TokenKind.HEADING to 0x9B859D,
            com.notedroidplusplus.app.TokenKind.LINK to 0x5F875F,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0xDECF3F
        )
    )

    /** Notepad++ "Vibrant Black" theme. */
    val VIBRANT_BLACK = BESPOKE.copy(
        name = "Vibrant Black",
        appBg = rgb(0x0F0F0F),
        toolbarBg = rgb(0x1A1A1A),
        tabBg = rgb(0x141414),
        editorBg = rgb(0x0F0F0F),
        gutterBg = rgb(0x141414),
        currentLine = rgb(0x1E1E1E),
        statusBg = rgb(0x141414),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0xFF00FF,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x00FFFF,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0xFFFF00,
            com.notedroidplusplus.app.TokenKind.STRING to 0x80FF80,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x606060,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0xC0C0C0,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0xFF80FF,
            com.notedroidplusplus.app.TokenKind.TAG to 0xFF00FF,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0x60FF60,
            com.notedroidplusplus.app.TokenKind.VALUE to 0xFFC060,
            com.notedroidplusplus.app.TokenKind.HEADING to 0xFF00FF,
            com.notedroidplusplus.app.TokenKind.LINK to 0x60A0FF,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0xFFD070
        )
    )

    /** Notepad++ "Zenburn" theme. */
    val ZENBURN = BESPOKE.copy(
        name = "Zenburn",
        appBg = rgb(0x3F3F3F),
        toolbarBg = rgb(0x4F4F4F),
        tabBg = rgb(0x383838),
        tabText = rgb(0xDCDCCC),
        tabSelectedText = rgb(0xFFEFD0),
        editorBg = rgb(0x3F3F3F),
        editorText = rgb(0xDCDCCC),
        gutterBg = rgb(0x383838),
        gutterText = rgb(0x7F9F7F),
        currentLine = rgb(0x494949),
        statusBg = rgb(0x383838),
        statusText = rgb(0xDCDCCC),
        syntax = syntax(
            com.notedroidplusplus.app.TokenKind.KEYWORD to 0x94BFF3,
            com.notedroidplusplus.app.TokenKind.TYPE to 0x8CD0D3,
            com.notedroidplusplus.app.TokenKind.NUMBER to 0xDCA3A3,
            com.notedroidplusplus.app.TokenKind.STRING to 0xCC9393,
            com.notedroidplusplus.app.TokenKind.COMMENT to 0x7F9F7F,
            com.notedroidplusplus.app.TokenKind.OPERATOR to 0xF0EFD0,
            com.notedroidplusplus.app.TokenKind.PREPROCESSOR to 0xDFC47D,
            com.notedroidplusplus.app.TokenKind.TAG to 0x94BFF3,
            com.notedroidplusplus.app.TokenKind.ATTRIBUTE to 0xF0DFAF,
            com.notedroidplusplus.app.TokenKind.VALUE to 0xDCA3A3,
            com.notedroidplusplus.app.TokenKind.HEADING to 0xEFEF8F,
            com.notedroidplusplus.app.TokenKind.LINK to 0x8CD0D3,
            com.notedroidplusplus.app.TokenKind.ESCAPE to 0xDCA3A3
        )
    )

    val all: List<EditorTheme> = listOf(
        DEFAULT, BESPOKE, BLACK_BOARD, CHOCO, DEEP_BLACK, KHAKI, MONO_INDUSTRIAL,
        MOSSY_LAWN, NAVAJO, PLASTIC_CODE_WRAP, RYBICKI, SOLARIZED, TWILIGHT,
        VIBRANT_BLACK, ZENBURN
    )

    fun byName(name: String): EditorTheme =
        all.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
}
