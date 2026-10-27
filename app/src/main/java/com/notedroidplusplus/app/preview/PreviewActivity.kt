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

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = PreviewRendererHolder.mode
            ?: intent.getStringExtra(EXTRA_MODE)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
            ?: Mode.HTML
        val content = if (!PreviewRendererHolder.consumed) PreviewRendererHolder.content
            else intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        PreviewRendererHolder.consumed = true
        title = getString(if (mode == Mode.MARKDOWN) R.string.preview_markdown else R.string.preview_html)
        val w = WebView(this)
        webView = w
        w.settings.javaScriptEnabled = false
        w.settings.allowFileAccess = false
        w.settings.allowContentAccess = false
        w.settings.blockNetworkLoads = true
        w.settings.loadsImagesAutomatically = true
        w.settings.loadWithOverviewMode = true
        w.webViewClient = WebViewClient()
        setContentView(w)
        w.loadDataWithBaseURL(null, render(content, mode), "text/html", "utf-8", null)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
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
            PreviewRendererHolder.put(content, mode)
            context.startActivity(Intent(context, PreviewActivity::class.java))
        }
    }
}
