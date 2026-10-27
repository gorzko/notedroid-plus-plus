package com.notedroidplusplus.app.plugins

import android.content.Context

/**
 * Discovers plugins. v1 ships with built-in plugins registered programmatically;
 * the channel is designed so external plugin packs (via Intent/API) can be added
 * without changing call sites.
 */
class PluginRegistry(private val context: Context) {

    fun discover(): List<PluginDescriptor> =
        builtIns().map { it.descriptor } + packDescriptors()

    fun builtIns(): List<NoteDroidPlugin> = listOf(
        Base64Plugin(),
        LoremIpsumPlugin(),
        WordCountPlugin()
    )

    fun packs(): List<PluginPacks.Pack> = PluginPacks.list(context)

    fun packDescriptors(): List<PluginDescriptor> =
        packs().map { pack ->
            PluginDescriptor(
                name = pack.name,
                description = pack.description,
                commands = pack.commands.map { cmd ->
                    PluginCommand(cmd.id, cmd.label) { host -> PluginPacks.run(cmd, host) }
                }
            )
        }
}
