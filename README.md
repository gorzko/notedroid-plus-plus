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
- Find in Files: searches a SAF folder tree (OpenDocumentTree), results are
  file:line:column; tapping a result opens the file at that line
- Recent files menu with persisted history
- Per-tab close buttons (X) plus long-press Close / Close Others / Close All
- Pinch-to-zoom in the editor (persisted with the font-size setting)
- Go-to-line
- Undo/Redo (200 steps, per-tab stacks), cut/copy/paste/select all
- UPPERCASE / lowercase / Title Case / tOGGLE cASE, Proper Case (+blend), Sentence case
  (+blend), iNVERT cASE, ranDOm CasE; Blank Operations (trim leading/trailing/both,
  EOL to space, TAB↔space); Line Operations (duplicate, split/join lines, remove empty
  lines, move line up/down, reverse/randomize, sort asc/desc + numeric/case-insensitive,
  remove duplicates); Insert Date Time (short/long/custom pattern, persisted);
  Copy to Clipboard (filename/full path/directory); Increase/Decrease Line Indent
- File menu: Save All (untitled tabs go through Save As), Reload from disk (with
  dirty-buffer confirmation), Print (Android PrintManager), per-tab Read-Only toggle
  (input blocked, status-bar indicator)
- Encoding detection & conversion (UTF-8, UTF-16LE/BE, ASCII, ISO-8859-1, CP-1252);
  BOM detection is preserved on save
- EOL conversion: Windows (CRLF), Unix (LF), Mac (CR)
- State restoration across rotation (instance state) and hard process kill
  (session snapshot with restore prompt on cold start)
- Hardened XML processing (DOCTYPE/external entities disabled — XXE-safe)
- Sandboxed preview: JS off, file/content access off, network loads blocked
- Languages menu, per-tab language override, file-extension auto-detection
- Themes: Notepad++ default plus 14 named desktop themes (Bespoke, Black board,
  Choco, Deep Black, khaki, Mono Industrial, MossyLawn, Navajo, Plastic Code Wrap,
  Rybicki, Solarized, Twilight, Vibrant Black, Zenburn) applied to chrome + syntax;
  selection persists (Settings or View → Theme)
- App language selection (27 Notepad++ locales + system default) via per-app
  locales, persisted and applied without changing the system language
- Wrap lines, zoom in/out, functional settings screen

**Notepad++ parity batch (settings, tools, panels)**
- Recent Files History settings (max entries, name/full-path display, clear)
- Find in Files filters (glob includes, `!` file excludes, `!\dir` / `!+\dir`
  directory exclusions), In-all-sub-folders toggle, open-buffer priority
- Tools: MD5/SHA-1/SHA-256/SHA-512 hashing (text dialog, SAF files, selection
  to clipboard) and a Character Panel (ASCII grid + HTML entities + U+XXXX
  codepoint insert) reflecting the tab encoding
- View Current File In... (external apps via SAF grant) and Open containing
  folder from the tab menu
- Help (?) menu: About, Online User Manual, Project Page, copyable Debug Info
- File association intent filters for ~35 text/code extensions
- Window menu with open-document list and Sort By (name/type/length/modified)
- Macro menu: record/playback, run N times, save & replay persisted macros
- View > Monitoring (tail -f) per-tab live reload with dirty-buffer protection
- Vertical edge guides (multi-column, background mode) and editor padding
- Editing settings: caret width, current-line frame style, virtual space,
  scroll beyond last line, INS/OVR overwrite mode in the status bar
- Auto-completion: word/keyword popup and matched-pair auto-insert
- View > Summary dialog and Restore Default Zoom

**New built-in features**
- **In-tab preview** for Markdown and HTML: a document view mode toggled from
  the toolbar, rendered in a sandboxed WebView (JavaScript disabled, file/content
  access off, network loads blocked); the mode is chosen from the content, so a
  .md file previews even if the language selector says otherwise
- **Markdown toolbar** (bold, italic, strikethrough, code, headings, lists,
  links, code blocks, tables) with pure, unit-tested text transforms and full
  undo support
- **Prettify / Minify** for JSON and XML (validation included); JSON numbers
  keep full precision (integers beyond 2^53 survive round-trips)
- **Content-based language detection**: files without a known extension are
  sniffed (shebang, DOCTYPE, JSON/YAML/INI structure, Markdown markers, SQL)
  from a bounded 8 KB prefix; extension detection stays the default
- **Edit-based undo**: contiguous typing and backspace runs coalesce into
  single steps (per-tab, capped at 200) instead of whole-document snapshots

**Plugin support**
- v1 plugin API (`PluginHost`, `PluginCommand`, `PluginDescriptor`)
- `MainActivity` implements `PluginHost`, so built-in plugins run for real from
  the Plugins menu: MIME Base64 encode/decode, Lorem Ipsum, Word Count
- External plugin packs: import a `plugin.json` manifest (Plugins → Import);
  commands run in a declarative sandbox (upper/lower/trim/base64/insert) —
  no arbitrary code execution
- Save As picks the encoding first (UTF-8/UTF-16/…), matching the Notepad++
  "Convert to" flow; MIME derived from the file extension
- Tools → Validate XML reports well-formedness with error details

**Cloud storage (Google Drive / OneDrive)**
- NoteDroid++ uses the Android Storage Access Framework for all file access.
  On devices with the Google Drive or OneDrive app installed, their documents
  providers appear directly in the system picker: File → Open browses and
  opens files from Drive/OneDrive, Save/Save As writes back through the same
  grant. This is the deliberate least-privilege path — zero app permissions,
  no OAuth credentials stored, no SDK dependency; the OS brokers access.

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
