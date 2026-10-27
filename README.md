# NoteDroid++

A full-featured Android text/code editor inspired by **Notepad++**, keeping the classic
look and feel (tabbed editing, line-number gutter, syntax colors, bottom status bar)
while following Android UX best practices (SAF file access, material toolbar, adaptive
icon, dark theme, back/forward gesture support).

## Features

**Notepad++ core**
- Multi-tab editing with per-tab dirty markers (●)
- Syntax highlighting for 20+ languages (C, C++, C#, Java, Kotlin, Python, JS, TS,
  HTML, XML, CSS, JSON, YAML, SQL, Shell, Batch, INI, PHP, Go, Rust, Swift, Ruby,
  Lua, Makefile, Markdown, TeX) modeled on Notepad++ `stylers.model.xml` colors
- Line numbers, current-line highlight, bracket matching
- Find / Replace with match case, whole word, regular expressions, wrap-around
  (Find Next / Find Prev buttons, keyboard search action)
- Go-to-line
- Undo/Redo (200 steps, per-tab stacks), cut/copy/paste/select all
- UPPERCASE / lowercase / Title Case / tOGGLE cASE, trim trailing whitespace
- Encoding detection & conversion (UTF-8, UTF-16LE/BE, ASCII, ISO-8859-1, CP-1252);
  BOM detection is preserved on save
- EOL conversion: Windows (CRLF), Unix (LF), Mac (CR)
- State restoration across rotation/process death (tabs, texts, caret, undo)
- Hardened XML processing (DOCTYPE/external entities disabled — XXE-safe)
- Sandboxed preview: JS off, file/content access off, network loads blocked
- Languages menu, per-tab language override, file-extension auto-detection
- Light/Dark theme (persisted), wrap lines, zoom in/out, functional settings screen

**New built-in features**
- **Preview** for Markdown and HTML (sandboxed WebView, JavaScript disabled)
- **Prettify / Minify** for JSON and XML (validation included)

**Plugin support**
- v1 plugin API (`PluginHost`, `PluginCommand`, `PluginDescriptor`)
- `MainActivity` implements `PluginHost`, so built-in plugins run for real from
  the Plugins menu: MIME Base64 encode/decode, Lorem Ipsum, Word Count
- Plugin registry designed for external plugin packs

## Building

Requires JDK 17 and Android SDK 34. The CI workflow installs Gradle 8.7 and runs:

```bash
gradle wrapper --gradle-version 8.7 --distribution-type bin
./gradlew assembleDebug testDebugUnitTest
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.
CI on every push/PR: `.github/workflows/android-ci.yml` (builds, runs unit tests,
uploads the APK as an artifact).

## Architecture

```
app/src/main/java/com/notedroidplusplus/app/
├── MainActivity.kt          # tabs, menus, find/replace panel, status bar
├── LanguageMode.kt          # languages + syntax token model
├── editing/                 # CodeEditText (gutter, spans, brackets), highlighter
├── core/                    # Document model, FileManager (SAF), TextUtils
├── search/                  # Find/Replace engine, find-in-files
├── tools/                   # JsonTool, XmlTool (prettify/minify/validate)
├── preview/                 # MarkdownRenderer, PreviewActivity (WebView)
├── settings/                # Preferences screen
└── plugins/                 # Plugin API v1 + built-in plugins
```

All editor logic that can be pure Kotlin is unit-tested (highlighter, find engine,
markdown renderer, JSON/XML tools, text utilities) under `app/src/test`.

## Writing a plugin

Implement `NoteDroidPlugin` and expose commands:

```kotlin
class MyPlugin : NoteDroidPlugin {
    override val descriptor = PluginDescriptor(
        name = "My Plugin",
        description = "Does something useful",
        commands = listOf(
            PluginCommand("do", "Do it") { host ->
                host.setText(host.getText().uppercase())
                host.showStatus("Done")
            }
        )
    )
}
```

Register it in `PluginRegistry.builtIns()`. Commands receive a `PluginHost`
granting access to context, text, selection, language, and status toasts.
