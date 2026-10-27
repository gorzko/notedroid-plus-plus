package com.notedroidplusplus.app.tools

/**
 * JSON prettify/minify implemented on top of Android's built-in org.json so no extra
 * dependency is needed. Pure JVM-testable.
 */
object JsonTool {

    fun prettify(input: String, indent: Int = 4): Result<String> = runCatching {
        val v = if (input.trim().startsWith("[")) {
            org.json.JSONArray(input)
        } else {
            org.json.JSONObject(input)
        }
        if (indent <= 0) v.toString() else when (v) {
            is org.json.JSONObject -> v.toString(indent)
            is org.json.JSONArray -> v.toString(indent)
            else -> v.toString()
        }
    }

    fun minify(input: String): Result<String> = runCatching {
        val v = if (input.trim().startsWith("[")) {
            org.json.JSONArray(input)
        } else {
            org.json.JSONObject(input)
        }
        v.toString()
    }
}
