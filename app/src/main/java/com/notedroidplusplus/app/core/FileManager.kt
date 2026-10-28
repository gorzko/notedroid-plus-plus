package com.notedroidplusplus.app.core

import android.content.Context
import android.net.Uri
import java.nio.charset.Charset

/**
 * Storage adapter over Android Storage Access Framework.
 * All actual file IO happens through ContentResolver; this class keeps the logic
 * testable by accepting a [FileIo] abstraction.
 */
class FileManager(private val io: FileIo) {

    interface FileIo {
        fun readBytes(uri: Uri): ByteArray
        fun writeBytes(uri: Uri, bytes: ByteArray)
        fun displayName(uri: Uri): String
    }

    data class LoadedFile(
        val text: String,
        val charset: Charset,
        val lineEnding: String,
        val hadBom: Boolean
    )

    fun load(uri: Uri, forcedCharset: Charset? = null): LoadedFile {
        val bytes = io.readBytes(uri)
        val hadBom = hasBom(bytes)
        val charset = forcedCharset ?: run {
            val heuristic = TextUtils.detect(bytes)
            if (hadBom || heuristic != Charsets.UTF_8) heuristic
            else {
                // Notepad++ prolog-first detection: an XML/HTML declared encoding wins
                val decoded = String(stripBom(bytes), heuristic)
                TextUtils.detectFromProlog(decoded) ?: heuristic
            }
        }
        val text = String(stripBom(bytes), charset)
        return LoadedFile(text, charset, TextUtils.detectLineEnding(text), hadBom)
    }

    fun save(uri: Uri, text: String, charset: Charset, lineEnding: String, writeBom: Boolean = false) {
        val normalized = TextUtils.convertLineEndings(text, lineEnding)
        val body = normalized.toByteArray(charset)
        val bom = if (writeBom) bomFor(charset) else ByteArray(0)
        io.writeBytes(uri, bom + body)
    }

    private fun bomFor(charset: Charset): ByteArray = when (charset.name()) {
        "UTF-8" -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        "UTF-16LE", "UTF-16" -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        "UTF-16BE" -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        else -> ByteArray(0)
    }

    fun displayName(uri: Uri): String = io.displayName(uri)

    companion object {
        fun hasBom(bytes: ByteArray): Boolean = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> true
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> true
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> true
            else -> false
        }

        fun stripBom(bytes: ByteArray): ByteArray = when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                bytes.copyOfRange(3, bytes.size)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                bytes.copyOfRange(2, bytes.size)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                bytes.copyOfRange(2, bytes.size)
            else -> bytes
        }

        fun defaultIo(context: Context): FileIo = object : FileIo {
            override fun readBytes(uri: Uri): ByteArray =
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Cannot open $uri")

            override fun writeBytes(uri: Uri, bytes: ByteArray) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Cannot write $uri")
            }

            override fun displayName(uri: Uri): String {
                runCatching {
                    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) {
                            c.getString(idx)?.let { return it }
                        }
                    }
                }
                return uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
            }
        }
    }
}
