package com.notedroidplusplus.app.editing

import java.io.File

/**
 * Loads and saves UDL definitions as JSON files in an app-private directory.
 * Names are sanitized before use as file names (no path characters), and
 * writes are bounded by a maximum definitions count.
 */
class UserDefinedLanguageStore(private val dir: File) {
    companion object {
        const val MAX_DEFINITIONS = 32
        const val FILE_SUFFIX = ".udl.json"
    }

    private val lock = Any()

    fun list(): List<UserDefinedLanguage> {
        synchronized(lock) {
            return dir.listFiles { f -> f.isFile && f.name.endsWith(FILE_SUFFIX) }
                ?.mapNotNull { file ->
                    runCatching { UserDefinedLanguage.fromJson(file.readText()) }.getOrNull()
                }
                .orEmpty()
                .sortedBy { it.name.lowercase() }
        }
    }

    fun save(udl: UserDefinedLanguage) {
        synchronized(lock) {
            if (!dir.exists()) dir.mkdirs()
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(FILE_SUFFIX) }
            val exists = files?.any { it.name == fileNameFor(udl.name) } ?: false
            if (!exists && (files?.size ?: 0) >= MAX_DEFINITIONS) {
                throw IllegalStateException("Too many user defined languages (max $MAX_DEFINITIONS)")
            }
            File(dir, fileNameFor(udl.name)).writeText(udl.toJson())
        }
    }

    fun delete(name: String): Boolean {
        synchronized(lock) {
            return File(dir, fileNameFor(name)).delete()
        }
    }

    /** Finds a UDL claiming [ext] (lowercased, dot-stripped). */
    fun forExtension(ext: String): UserDefinedLanguage? {
        if (ext.isEmpty()) return null
        return list().firstOrNull { ext in it.extensions }
    }

    fun findByName(name: String): UserDefinedLanguage? =
        list().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun fileNameFor(name: String): String {
        val safe = name.filter { it.isLetterOrDigit() || it in " -_" }.ifEmpty { "udl" }
        return safe + FILE_SUFFIX
    }
}
