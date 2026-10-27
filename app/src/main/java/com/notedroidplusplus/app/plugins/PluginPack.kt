package com.notedroidplusplus.app.plugins

import android.content.Context
import com.notedroidplusplus.app.tools.Json
import java.io.File

/**
 * External plugin pack: a directory under filesDir/plugins/<name>/ containing
 * plugin.json ({"name":..., "description":..., "commands":[{"id","label","do":...}]}).
 *
 * Command execution model for third-party packs is deliberately restricted to a
 * small declarative operation set (no arbitrary code execution), keeping the
 * sandbox safe while enabling simple utility plugins:
 *  - "upper": uppercase the text or selection
 *  - "lower": lowercase the text or selection
 *  - "trim": trim trailing whitespace
 *  - "base64-encode" / "base64-decode"
 *  - "insert:<text>": insert text at the caret
 */
object PluginPacks {

    data class Pack(val name: String, val description: String, val commands: List<PackCommand>)
    data class PackCommand(val id: String, val label: String, val op: String, val arg: String?)

    fun packsDir(context: Context): File = File(context.filesDir, "plugins").apply { mkdirs() }

    fun list(context: Context): List<Pack> {
        return packsDir(context).listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir -> load(dir) }
            ?: emptyList()
    }

    fun load(dir: File): Pack? {
        val manifest = File(dir, "plugin.json")
        if (!manifest.exists()) return null
        return runCatching {
            val obj = Json.parse(manifest.readText()) as? Json.Value.Obj ?: return null
            fun str(v: Json.Value?) = (v as? Json.Value.Str)?.s
            val name = str(obj.entries.firstOrNull { it.first == "name" }?.second) ?: dir.name
            val desc = str(obj.entries.firstOrNull { it.first == "description" }?.second) ?: ""
            val cmds = (obj.entries.firstOrNull { it.first == "commands" }?.second as? Json.Value.Arr)?.items
                ?.mapNotNull { c ->
                    val co = c as? Json.Value.Obj ?: return@mapNotNull null
                    val id = str(co.entries.firstOrNull { it.first == "id" }?.second) ?: return@mapNotNull null
                    val label = str(co.entries.firstOrNull { it.first == "label" }?.second) ?: id
                    val op = str(co.entries.firstOrNull { it.first == "do" }?.second) ?: return@mapNotNull null
                    PackCommand(id, label, op, str(co.entries.firstOrNull { it.first == "arg" }?.second))
                } ?: emptyList()
            Pack(name, desc, cmds)
        }.getOrNull()
    }

    fun run(cmd: PackCommand, host: PluginHost) {
        val sel = host.getSelection()
        val text = host.getText()
        val target = if (sel.isNotEmpty()) sel else text
        when (cmd.op) {
            "upper" -> host.setText(if (sel.isNotEmpty()) text.replace(sel, target.uppercase()) else target.uppercase())
            "lower" -> host.setText(if (sel.isNotEmpty()) text.replace(sel, target.lowercase()) else target.lowercase())
            "trim" -> {
                val trimmed = target.split("\n").joinToString("\n") { it.trimEnd() }
                host.setText(if (sel.isNotEmpty()) text.replace(sel, trimmed) else trimmed)
            }
            "base64-encode" -> host.setText(android.util.Base64.encodeToString(target.toByteArray(), android.util.Base64.NO_WRAP))
            "base64-decode" -> runCatching {
                host.setText(String(android.util.Base64.decode(target, android.util.Base64.NO_WRAP)))
            }.onFailure { host.showStatus("Not valid Base64") }
            "insert" -> {
                val arg = cmd.arg ?: ""
                val caret = host.caret()
                host.setText(text.substring(0, caret) + arg + text.substring(caret))
            }
            else -> host.showStatus("Unknown plugin op: ${cmd.op}")
        }
    }
}
