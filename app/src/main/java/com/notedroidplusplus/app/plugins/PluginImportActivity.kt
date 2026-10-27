package com.notedroidplusplus.app.plugins

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.notedroidplusplus.app.R
import java.io.File

/**
 * Imports an external plugin pack: a plugin.json file picked via SAF is copied
 * into filesDir/plugins/<name>/plugin.json. Commands declared there become
 * available in the Plugins menu (declarative ops only, see [PluginPacks]).
 */
class PluginImportActivity : AppCompatActivity() {

    private lateinit var list: ListView

    private val pickLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { import(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugins)
        title = getString(R.string.plugins_title)
        list = findViewById(R.id.plugin_list)
        render()
        pickLauncher.launch(arrayOf("application/json", "text/*"))
    }

    private fun render() {
        val packs = PluginPacks.list(this)
        val names = packs.map { "${it.name} — ${it.description} (${it.commands.size} commands)" }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        list.onItemClickListener = null
    }

    private fun import(uri: Uri) {
        val ok = runCatching {
            val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw IllegalStateException("Cannot read manifest")
            val packName = com.notedroidplusplus.app.tools.Json.parse(text).let { v ->
                (v as? com.notedroidplusplus.app.tools.Json.Value.Obj)
                    ?.entries?.firstOrNull { it.first == "name" }?.second
                    ?.let { sv -> (sv as? com.notedroidplusplus.app.tools.Json.Value.Str)?.s }
            } ?: "pack_${System.currentTimeMillis()}"
            val dir = File(PluginPacks.packsDir(this), packName)
            dir.mkdirs()
            File(dir, "plugin.json").writeText(text)
        }.isSuccess
        if (ok) {
            Toast.makeText(this, R.string.plugin_imported, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.plugin_import_failed, Toast.LENGTH_SHORT).show()
        }
        render()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(android.content.Intent(context, PluginImportActivity::class.java))
        }
    }
}
