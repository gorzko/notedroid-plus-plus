package com.notedroidplusplus.app.preview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.notedroidplusplus.app.R

/** Renders Markdown (converted to HTML) or raw HTML in a sandboxed WebView (JS disabled). */
class PreviewActivity : AppCompatActivity() {

    enum class Mode { MARKDOWN, HTML }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra(EXTRA_MODE)?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.HTML
        val content = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        title = getString(if (mode == Mode.MARKDOWN) R.string.preview_markdown else R.string.preview_html)
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = false
        webView.settings.loadWithOverviewMode = true
        webView.webViewClient = WebViewClient()
        setContentView(webView)
        webView.loadDataWithBaseURL(null, render(content, mode), "text/html", "utf-8", null)
    }

    private fun render(content: String, mode: Mode): String = when (mode) {
        Mode.HTML -> content
        Mode.MARKDOWN -> htmlWrap(MarkdownRenderer.render(content))
    }

    private fun htmlWrap(body: String): String =
        "<html><head><meta charset='utf-8'><style>body{font-family:sans-serif;margin:16px;line-height:1.5}" +
            "pre{background:#f0f0f0;padding:8px;overflow:auto}code{background:#f0f0f0;padding:2px}</style>" +
            "</head><body>$body</body></html>"

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_CONTENT = "content"
        fun start(context: Context, mode: Mode, content: String) {
            context.startActivity(Intent(context, PreviewActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_CONTENT, content)
            })
        }
    }
}
