package com.notedroidplusplus.app

import com.notedroidplusplus.app.preview.PreviewView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Content-aware preview/toolbar detection (issue #25): Markdown marker
 * sniffing is shared with [com.notedroidplusplus.app.editing.ContentSniffer],
 * so preview mode and the formatting toolbar agree on what counts as a
 * Markdown document, regardless of the tab's language selector.
 */
class PreviewModeTest {

    @Test
    fun markdownContentPreviewsEvenWhenLanguageIsNotMarkdown() {
        val mode = PreviewView.modeFor("# Title\n\n- item\n", languageIsMarkdown = false, languageIsHtml = false)
        assertEquals(PreviewView.Mode.MARKDOWN, mode)
    }

    @Test
    fun linkOnlyContentCountsAsMarkdown() {
        val mode = PreviewView.modeFor("see [docs](https://x.y) here\n", false, false)
        assertEquals(PreviewView.Mode.MARKDOWN, mode)
    }

    @Test
    fun plainTextHasNoPreview() {
        assertNull(PreviewView.modeFor("just words\nnothing special\n", false, false))
    }

    @Test
    fun languageSelectorStillWinsForMarkdown() {
        assertEquals(
            PreviewView.Mode.MARKDOWN,
            PreviewView.modeFor("plain words\n", languageIsMarkdown = true, languageIsHtml = false)
        )
    }

    @Test
    fun htmlContentPreviewsAsHtml() {
        assertEquals(
            PreviewView.Mode.HTML,
            PreviewView.modeFor("<!DOCTYPE html><html><body>hi</body></html>", false, false)
        )
        assertEquals(
            PreviewView.Mode.HTML,
            PreviewView.modeFor("anything", languageIsMarkdown = false, languageIsHtml = true)
        )
    }

    @Test
    fun boundedContentDoesNotScanWholeFile() {
        val md = StringBuilder("# Heading\n\n")
        repeat(4) { md.append("- item\n\n[link](x)\n\n") }
        repeat(100_000) { md.append("z") }
        assertEquals(
            PreviewView.Mode.MARKDOWN,
            PreviewView.modeFor(md.toString(), languageIsMarkdown = false, languageIsHtml = false)
        )
    }
}
