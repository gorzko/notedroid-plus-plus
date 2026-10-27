package com.notedroidplusplus.app.preview

/**
 * Holds preview content out-of-band so arbitrarily large documents never hit the
 * Binder transaction limit (Intent extras are capped at ~1 MB).
 */
object PreviewRendererHolder {
    @Volatile var content: String = ""
    @Volatile var mode: PreviewActivity.Mode? = null
    @Volatile var consumed: Boolean = true

    fun put(content: String, mode: PreviewActivity.Mode) {
        this.content = content
        this.mode = mode
        this.consumed = false
    }
}
