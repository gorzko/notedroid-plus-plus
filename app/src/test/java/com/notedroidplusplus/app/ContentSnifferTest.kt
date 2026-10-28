package com.notedroidplusplus.app

import com.notedroidplusplus.app.editing.ContentSniffer
import com.notedroidplusplus.app.LanguageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentSnifferTest {

    @Test
    fun shebangDetectsShell() {
        assertEquals(LanguageMode.SHELL, ContentSniffer.detect("#!/bin/bash\necho hi\n"))
        assertEquals(LanguageMode.SHELL, ContentSniffer.detect("#!/usr/bin/env sh\nls\n"))
    }

    @Test
    fun shebangDetectsPythonAndNode() {
        assertEquals(LanguageMode.PYTHON, ContentSniffer.detect("#!/usr/bin/env python3\nprint(1)\n"))
        assertEquals(LanguageMode.JAVASCRIPT, ContentSniffer.detect("#!/usr/bin/env node\nconsole.log(1)\n"))
    }

    @Test
    fun doctypeDetectsHtml() {
        assertEquals(LanguageMode.HTML, ContentSniffer.detect("<!DOCTYPE html>\n<html><body>x</body></html>\n"))
    }

    @Test
    fun xmlDeclarationDetectsXml() {
        assertEquals(LanguageMode.XML, ContentSniffer.detect("<?xml version=\"1.0\"?>\n<root><a/></root>\n"))
    }

    @Test
    fun includeDetectsC() {
        assertEquals(LanguageMode.C, ContentSniffer.detect("#include <stdio.h>\nint main(){return 0;}\n"))
    }

    @Test
    fun cppDirectivesDetectCpp() {
        assertEquals(
            LanguageMode.CPP,
            ContentSniffer.detect("#include <vector>\nnamespace x { template<class T> struct S { T::type v; }; }\n")
        )
    }

    @Test
    fun bracesWithKeysDetectJson() {
        assertEquals(LanguageMode.JSON, ContentSniffer.detect("{\"a\": 1, \"b\": [true, null]}"))
    }

    @Test
    fun yamlDocumentMarkerDetectsYaml() {
        assertEquals(LanguageMode.YAML, ContentSniffer.detect("---\nname: app\nversion: 2\n"))
    }

    @Test
    fun keyValueLinesDetectYaml() {
        assertEquals(LanguageMode.YAML, ContentSniffer.detect("name: notedroid\nplatform: android\n"))
    }

    @Test
    fun iniSectionsDetectIni() {
        assertEquals(LanguageMode.INI, ContentSniffer.detect("[core]\nrepositoryformat = 0\nfilemode = true\n"))
    }

    @Test
    fun markdownHeadingsAndListsDetectMarkdown() {
        val md = "# Title\n\nSome text with a [link](http://x).\n\n- item one\n- item two\n"
        assertEquals(LanguageMode.MARKDOWN, ContentSniffer.detect(md))
    }

    @Test
    fun sqlStatementsDetectSql() {
        assertEquals(LanguageMode.SQL, ContentSniffer.detect("SELECT id FROM users WHERE active = 1;\n"))
    }

    @Test
    fun plainTextStaysNormal() {
        assertEquals(LanguageMode.NORMAL_TEXT, ContentSniffer.detect("just some words\nnothing special\n"))
    }

    @Test
    fun boundedReadDoesNotScanWholeFile() {
        val md = StringBuilder("# Heading\n\n")
        repeat(5) { md.append("- item\n\n[link](x)\n\n") }
        repeat(100_000) { md.append("z") }
        assertEquals(LanguageMode.MARKDOWN, ContentSniffer.detect(md.toString()))
    }

    @Test
    fun extensionStillWinsWhenKnown() {
        assertEquals(LanguageMode.JSON, ContentSniffer.detectWithFileName("data.json", "#!/bin/sh\n"))
        assertEquals(LanguageMode.PYTHON, ContentSniffer.detectWithFileName("script", "#!/usr/bin/python\n"))
        assertEquals(LanguageMode.MAKEFILE, ContentSniffer.detectWithFileName("Makefile", "install:\n"))
        assertEquals(
            LanguageMode.NORMAL_TEXT,
            ContentSniffer.detectWithFileName("noext", "just words\n")
        )
    }

    @Test
    fun maxSniffBytesIsBounded() {
        assertTrue(ContentSniffer.MAX_SNIFF_BYTES <= 8192)
    }
}
