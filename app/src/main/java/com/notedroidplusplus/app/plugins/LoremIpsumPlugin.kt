package com.notedroidplusplus.app.plugins

/** Built-in plugin inserting lorem ipsum text. */
class LoremIpsumPlugin : NoteDroidPlugin {
    override val descriptor = PluginDescriptor(
        name = "Lorem Ipsum",
        description = "Insert placeholder text",
        commands = listOf(
            PluginCommand("insert", "Insert Lorem Ipsum") { host ->
                host.setText(
                    host.getText() + " Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                        "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
                )
                host.showStatus("Lorem ipsum inserted")
            }
        )
    )
}
