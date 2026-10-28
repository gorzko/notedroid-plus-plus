package com.notedroidplusplus.app.preview

/**
 * Fingerprint cache for the content-aware preview-mode sniff.
 * [PreviewView.modeFor] scans up to a 4096-char head on every call; the
 * status bar re-runs it per keystroke and per cursor move, so the result is
 * memoized against a cheap fingerprint (text length + head hash + language
 * flags). Only a changed head (or language selection) triggers a re-sniff.
 */
class PreviewModeMemo {

    private var lastFingerprint: Long = 0L
    private var lastResult: PreviewView.Mode? = null
    private var computed = false

    /** Number of times the underlying sniff actually ran (for tests/diagnostics). */
    var computations = 0
        private set

    fun modeFor(content: String, languageIsMarkdown: Boolean, languageIsHtml: Boolean): PreviewView.Mode? {
        val fp = fingerprint(content, languageIsMarkdown, languageIsHtml)
        if (computed && fp == lastFingerprint) return lastResult
        val result = PreviewView.modeFor(content, languageIsMarkdown, languageIsHtml)
        lastFingerprint = fp
        lastResult = result
        computed = true
        computations++
        return result
    }

    companion object {
        const val HEAD_LIMIT = 4096

        fun fingerprint(content: String, languageIsMarkdown: Boolean, languageIsHtml: Boolean): Long {
            val head = if (content.length > HEAD_LIMIT) content.substring(0, HEAD_LIMIT) else content
            var h = 1125899906842597L
            h = 31L * h + content.length
            h = 31L * h + head.hashCode()
            h = 31L * h + if (languageIsMarkdown) 1 else 0
            h = 31L * h + if (languageIsHtml) 1 else 0
            return h
        }
    }
}
