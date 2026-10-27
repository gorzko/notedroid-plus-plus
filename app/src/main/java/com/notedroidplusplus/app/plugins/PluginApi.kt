package com.notedroidplusplus.app.plugins

import android.content.Context
import com.notedroidplusplus.app.LanguageMode

/**
 * Public plugin API (v1), inspired by Notepad++'s plugin interface.
 * A plugin manifests as a JSON descriptor (plugin.json) plus commands that
 * operate on editor text through [PluginHost].
 */
interface PluginHost {
    val context: Context
    fun getText(): String
    fun setText(text: String)
    fun getSelection(): String
    fun caret(): Int
    fun language(): LanguageMode
    fun showStatus(message: String)
}

data class PluginCommand(
    val id: String,
    val label: String,
    val run: (PluginHost) -> Unit
)

data class PluginDescriptor(
    val name: String,
    val description: String,
    val commands: List<PluginCommand>
)

interface NoteDroidPlugin {
    val descriptor: PluginDescriptor
}
