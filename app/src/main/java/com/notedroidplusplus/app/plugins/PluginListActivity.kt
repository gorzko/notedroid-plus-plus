package com.notedroidplusplus.app.plugins

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.notedroidplusplus.app.R

/** Lists discovered plugins. Commands are invoked from the main Plugins menu. */
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
        val names = plugins.map { "${it.name} — ${it.description} (${it.commands.size})" }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
    }
}
