package com.notedroidplusplus.app

import com.notedroidplusplus.app.search.FindFilters.acceptsDirectory
import com.notedroidplusplus.app.search.FindFilters.acceptsFile
import com.notedroidplusplus.app.search.FindFilters.globMatches
import com.notedroidplusplus.app.search.FindFilters.parse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindFiltersTest {

    @Test
    fun glob_basics() {
        assertTrue(globMatches("*.kt", "Main.KT"))
        assertTrue(globMatches("a?c", "abc"))
        assertFalse(globMatches("a?c", "abbc"))
        assertTrue(globMatches("*.*", "readme.md"))
        assertTrue(globMatches("*.md", "notes.md"))
        assertFalse(globMatches("*.md", "notes.txt"))
        assertTrue(globMatches("test*", "testdata"))
        assertTrue(globMatches("*log*", "mylogfile.txt"))
    }

    @Test
    fun parse_include_only() {
        val f = parse("*.kt")
        assertTrue(f.includes.contains("*.kt"))
        assertTrue(acceptsFile(f, "a.kt"))
        assertFalse(acceptsFile(f, "a.py"))
    }

    @Test
    fun parse_exclude_file() {
        val f = parse("!*bin *.*")
        assertTrue(f.includes.contains("*.*"))
        assertTrue(f.fileExcludes.contains("*bin"))
        assertTrue(acceptsFile(f, "data.txt"))
        assertFalse(acceptsFile(f, "data.bin"))
    }

    @Test
    fun parse_top_level_dir_exclusion() {
        val f = parse("!\\tests")
        assertTrue(f.dirExcludesTop.contains("tests"))
        assertFalse(acceptsDirectory(f, "Tests", depth = 0))
        assertTrue(acceptsDirectory(f, "tests", depth = 1))
    }

    @Test
    fun parse_recursive_dir_exclusion() {
        val f = parse("!+\\log*")
        assertTrue(f.dirExcludesRecursive.contains("log*") || f.dirExcludesRecursive.isNotEmpty())
        assertFalse(acceptsDirectory(f, "logs", depth = 0))
        assertFalse(acceptsDirectory(f, "logging", depth = 3))
    }

    @Test
    fun hidden_directories_are_skipped() {
        val f = parse("")
        assertFalse(acceptsDirectory(f, ".git", depth = 0))
        assertTrue(f.isEmpty)
        assertTrue(acceptsFile(f, "anything"))
    }
}
