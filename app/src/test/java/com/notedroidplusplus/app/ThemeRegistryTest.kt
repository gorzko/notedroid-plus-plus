package com.notedroidplusplus.app

import com.notedroidplusplus.app.settings.ThemeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for review finding C1: every theme color must be opaque.
 * rgb() used to truncate 6-digit hex literals to alpha=0x00 (fully
 * transparent chrome and text).
 */
class ThemeRegistryTest {

    private fun alpha(color: Int): Int = (color ushr 24) and 0xFF
    private fun red(color: Int): Int = (color ushr 16) and 0xFF

    private fun assertOpaqueRecursively(theme: com.notedroidplusplus.app.settings.EditorTheme) {
        val colors = listOf(
            theme.appBg, theme.toolbarBg, theme.tabBg, theme.tabText,
            theme.tabSelectedText, theme.tabIndicator, theme.editorBg,
            theme.editorText, theme.gutterBg, theme.gutterText,
            theme.currentLine, theme.bracket, theme.statusBg, theme.statusText
        ) + theme.syntax.values.toList()
        colors.forEach { c ->
            assertEquals("alpha of 0x" + Integer.toHexString(c), 255, alpha(c))
        }
    }

    @Test
    fun allThemeColorsAreOpaque() {
        ThemeRegistry.all.forEach { assertOpaqueRecursively(it) }
    }

    @Test
    fun defaultThemeIsLight() {
        assertTrue(red(ThemeRegistry.DEFAULT.editorBg) > 200)
        assertTrue(red(ThemeRegistry.DEFAULT.editorText) < 64)
    }

    @Test
    fun darkThemesAreActuallyDark() {
        listOf(ThemeRegistry.BESPOKE, ThemeRegistry.BLACK_BOARD, ThemeRegistry.DEEP_BLACK, ThemeRegistry.VIBRANT_BLACK)
            .forEach { t ->
                assertTrue("${t.name} editorBg should be dark", red(t.editorBg) < 64)
                assertTrue("${t.name} should be flagged dark", t.dark)
            }
    }

    @Test
    fun byNameFallsBackToDefault() {
        assertEquals(ThemeRegistry.DEFAULT, ThemeRegistry.byName("nonexistent-theme"))
        assertEquals(ThemeRegistry.BESPOKE, ThemeRegistry.byName("bespoke"))
    }
}
