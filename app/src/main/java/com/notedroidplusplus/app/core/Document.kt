package com.notedroidplusplus.app.core

/** Immutable snapshot of a document, like Notepad++'s per-tab buffer. */
data class Document(
    val uri: String?,
    val fileName: String,
    val text: String,
    val language: com.notedroidplusplus.app.LanguageMode,
    val encoding: String = "UTF-8",
    val lineEnding: String = "\\n",
    val modified: Boolean = false,
    val readOnly: Boolean = false
) {
    val isNew: Boolean get() = uri == null
    val lineCount: Int get() = text.count('\\n') + 1
}
