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

    data class LoadedFile(val text: String, val charset: Charset, val lineEnding: String)

    fun load(uri: Uri, forcedCharset: Charset? = null): LoadedFile {
        val bytes = io.readBytes(uri)
        val charset = forcedCharset ?: TextUtils.detect(bytes)
        val text = String(stripBom(bytes), charset)
        return LoadedFile(text, charset, TextUtils.detectLineEnding(text))
    }

    fun save(uri: Uri, text: String, charset: Charset, lineEnding: String) {
        val normalized = TextUtils.convertLineEndings(text, lineEnding)
        io.writeBytes(uri, normalized.toByteArray(charset))
    }

    fun displayName(uri: Uri): String = io.displayName(uri)

    companion object {
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

            override fun displayName(uri: Uri): String = uri.lastPathSegment ?: uri.toString()
        }
    }
}
