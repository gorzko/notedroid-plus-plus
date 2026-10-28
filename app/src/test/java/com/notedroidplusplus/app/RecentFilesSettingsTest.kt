package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.RecentFiles
import com.notedroidplusplus.app.core.RecentFilesSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFilesSettingsTest {

    @Test
    fun parseMax_defaultsOnInvalidInput() {
        assertEquals(10, RecentFilesSettings.parseMax(null))
        assertEquals(10, RecentFilesSettings.parseMax("banana"))
        assertEquals(10, RecentFilesSettings.parseMax("  "))
    }

    @Test
    fun parseMax_clampsToRange() {
        assertEquals(1, RecentFilesSettings.parseMax("0"))
        assertEquals(1, RecentFilesSettings.parseMax("-5"))
        assertEquals(100, RecentFilesSettings.parseMax("500"))
        assertEquals(42, RecentFilesSettings.parseMax(" 42 "))
    }

    @Test
    fun trim_truncatesToLimit() {
        val entries = (1..30).map { RecentFiles.Entry("u$it", "n$it") }
        assertEquals(10, RecentFilesSettings.trim(entries, 10).size)
        assertEquals(30, RecentFilesSettings.trim(entries, 100).size)
        assertEquals(entries.take(3), RecentFilesSettings.trim(entries, 3))
    }

    @Test
    fun label_usesNameOrUriPerMode() {
        val e = RecentFiles.Entry("content://x/y/readme.md", "readme.md")
        assertEquals("readme.md", RecentFilesSettings.label(e, RecentFilesSettings.DisplayMode.NAME_ONLY))
        assertEquals("content://x/y/readme.md", RecentFilesSettings.label(e, RecentFilesSettings.DisplayMode.FULL_PATH))
    }

    @Test
    fun resolveMax_acceptsSeekBarInt() {
        assertEquals(25, RecentFilesSettings.resolveMax(25))
        assertEquals(1, RecentFilesSettings.resolveMax(0))
        assertEquals(100, RecentFilesSettings.resolveMax(500))
    }

    @Test
    fun resolveMax_acceptsLegacyString() {
        assertEquals(42, RecentFilesSettings.resolveMax("42"))
        assertEquals(10, RecentFilesSettings.resolveMax("banana"))
        assertEquals(1, RecentFilesSettings.resolveMax("-5"))
    }

    @Test
    fun resolveMax_defaultsOnMissingOrUnexpected() {
        assertEquals(10, RecentFilesSettings.resolveMax(null))
        assertEquals(10, RecentFilesSettings.resolveMax(true))
        assertEquals(10, RecentFilesSettings.resolveMax(3.5))
    }
}
