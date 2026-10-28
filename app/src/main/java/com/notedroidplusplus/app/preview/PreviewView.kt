package com.notedroidplusplus.app.preview

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.notedroidplusplus.app.R

/**
 * In-tab preview surface: renders Markdown (converted to HTML) or raw HTML in
 * a sandboxed WebView. JS disabled, no file/content access, network loads
 * blocked; the WebView is destroyed when the preview is closed (and in
 * [onDetachedFromWindow]) per the repo's security standards.
 */
class PreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class Mode { MARKDOWN, HTML }

    private var webView: WebView? = null

    fun render(content: String, mode: Mode) {
        destroyWebView()
        val w = WebView(context)
        webView = w
        w.settings.javaScriptEnabled = false
        w.settings.allowFileAccess = false
        w.settings.allowContentAccess = false
        w.settings.blockNetworkLoads = true
        w.settings.loadsImagesAutomatically = true
        w.settings.loadWithOverviewMode = true
        w.webViewClient = WebViewClient()
        addView(w, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        w.loadDataWithBaseURL(null, wrap(content, mode), "text/html", "utf-8", null)
    }

    fun close() {
        destroyWebView()
        removeAllViews()
    }

    private fun destroyWebView() {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }

    override fun onDetachedFromWindow() {
        destroyWebView()
        super.onDetachedFromWindow()
    }

    private fun wrap(content: String, mode: Mode): String = when (mode) {
        Mode.HTML -> content
        Mode.MARKDOWN -> htmlWrap(MarkdownRenderer.render(content))
    }

    private fun htmlWrap(body: String): String =
        "<html><head><meta charset='utf-8'><style>body{font-family:sans-serif;margin:16px;line-height:1.5}" +
            "pre{background:#f0f0f0;padding:8px;overflow:auto}code{background:#f0f0f0;padding:2px}" +
            "</style></head><body>$body</body></html>"

    companion object {
        /**
         * Decide the preview mode from the document content rather than the
         * tab's language selector: Markdown files preview as Markdown even if
         * the language was never set (or was left as Normal text).
         */
        fun modeFor(content: String, languageIsMarkdown: Boolean, languageIsHtml: Boolean): Mode? {
            if (languageIsMarkdown) return Mode.MARKDOWN
            if (languageIsHtml) return Mode.HTML
            val head = if (content.length > 4096) content.substring(0, 4096) else content
            val trimmed = head.trim()
            if (trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true)
            ) return Mode.HTML
            var mdMarkers = 0
            for (line in head.lines()) {
                val t = line.trimEnd()
                if (Regex("^#{1,6}\\s+\\S").containsMatchIn(t)) mdMarkers++
                if (t.startsWith("```") || t.startsWith("~~~")) mdMarkers++
                if (Regex("\\[[^]]+]\\([^)]+\\)").containsMatchIn(t)) mdMarkers++
                if (Regex("^\\s*([-*+]|\\d+\\.)\\s+\\S").containsMatchIn(t)) mdMarkers++
            }
            if (mdMarkers >= 1) return Mode.MARKDOWN
            return null
        }
    }
}
