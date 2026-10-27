package com.notedroidplusplus.app.plugins

/** Built-in plugin showing word/char/line counts, like the TextFX stats. */
class WordCountPlugin : NoteDroidPlugin {
    override val descriptor = PluginDescriptor(
        name = "Word Count",
        description = "Count words, characters and lines",
        commands = listOf(
            PluginCommand("count", "Count Words") { host ->
                val t = host.getText()
                val words = t.split(Regex("\\s+")).count { it.isNotEmpty() }
                val chars = t.length
                val lines = t.count { it == '\n' } + 1
                host.showStatus("Words: $words, Chars: $chars, Lines: $lines")
            }
        )
    )
}
