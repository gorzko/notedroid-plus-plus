package com.notedroidplusplus.app.tools

/**
 * JSON prettify/minify built on the self-contained [Json] parser so it behaves
 * identically on the JVM (unit tests) and on Android.
 */
object JsonTool {

    fun prettify(input: String, indent: Int = 4): Result<String> = runCatching {
        Json.stringify(Json.parse(input), indent)
    }

    fun minify(input: String): Result<String> = runCatching {
        Json.stringify(Json.parse(input), 0)
    }
}
