package com.notedroidplusplus.app.tools

import java.io.InputStream
import java.security.MessageDigest

/**
 * MD5 / SHA-1 / SHA-256 / SHA-512 hash generation (Notepad++ Tools parity).
 * Pure JVM; file hashing streams in bounded chunks.
 */
object HashTool {

    enum class Algorithm(val display: String) {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        SHA512("SHA-512")
    }

    private const val CHUNK = 64 * 1024

    fun hashBytes(algorithm: Algorithm, bytes: ByteArray): String =
        hex(MessageDigest.getInstance(algorithm.display.replace("-", "")).digest(bytes))

    fun hashText(algorithm: Algorithm, text: String): String =
        hashBytes(algorithm, text.toByteArray(Charsets.UTF_8))

    fun hashStream(algorithm: Algorithm, input: InputStream): String {
        val md = MessageDigest.getInstance(algorithm.display.replace("-", ""))
        val buf = ByteArray(CHUNK)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        return hex(md.digest())
    }

    fun hex(digest: ByteArray): String = digest.joinToString("") { "%02x".format(it) }
}
