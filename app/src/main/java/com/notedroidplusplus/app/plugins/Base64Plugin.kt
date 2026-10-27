package com.notedroidplusplus.app.plugins

import android.util.Base64

/** Built-in plugin mimicking a classic Notepad++ MIME-tools plugin (Base64 subset). */
class Base64Plugin : NoteDroidPlugin {
    override val descriptor = PluginDescriptor(
        name = "MIME Base64",
        description = "Encode/decode selection as Base64",
        commands = listOf(
            PluginCommand("encode", "Base64 Encode") { host ->
                val sel = host.getSelection()
                val text = if (sel.isNotEmpty()) sel else host.getText()
                host.setText(Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))
                host.showStatus("Base64 encoded")
            },
            PluginCommand("decode", "Base64 Decode") { host ->
                val sel = host.getSelection()
                val text = if (sel.isNotEmpty()) sel else host.getText()
                runCatching {
                    host.setText(String(Base64.decode(text, Base64.NO_WRAP)))
                    host.showStatus("Base64 decoded")
                }.onFailure { host.showStatus("Not valid Base64") }
            }
        )
    )
}
