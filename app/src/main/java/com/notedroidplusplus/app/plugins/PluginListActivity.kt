package com.notedroidplusplus.app.plugins

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.notedroidplusplus.app.R

/** Lists discovered plugins and lets the user invoke their commands. */
class PluginListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plugins)
        val list = findViewById<ListView>(R.id.plugin_list)
        val registry = PluginRegistry(this)
        val plugins = registry.discover()
        if (plugins.isEmpty()) {
            Toast.makeText(this, R.string.plugins_empty, Toast.LENGTH_SHORT).show()
        }
        val names = plugins.map { "${it.name} — ${it.description}" }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        list.setOnItemClickListener { _, _, position, _ ->
            plugins[position].commands.firstOrNull()?.let { cmd ->
                Toast.makeText(this, "Run: ${cmd.label}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
