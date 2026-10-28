# AGENTS.md — NoteDroid++

Guidance for any AI agent working in this repository.

## Project

Android port of Notepad++ ("NoteDroid++"): Kotlin, Material 3, multi-tab code editor with syntax highlighting for 26 languages, find/replace, find-in-files, Markdown/HTML preview, JSON/XML prettify/minify/validate, and a plugin system.

## Build & verification — read this first

- **There is no local JDK/Android SDK in the development sandbox.** All build and test verification goes through GitHub Actions.
- CI workflow: `.github/workflows/android-ci.yml` — installs Gradle 8.7, runs `./gradlew assembleDebug testDebugUnitTest`, uploads `app-debug.apk` as an artifact.
- No Gradle wrapper is committed; CI generates it with `gradle wrapper --gradle-version 8.7 --distribution-type bin`.
- Monitor: `gh run list --branch <branch> --limit 3 --json databaseId,status,conclusion,headSha` (match `headSha` to your HEAD — old runs answer for old commits), poll `gh run view <id> --json status,conclusion`, diagnose with `gh run view <id> --log-failed`.
- Always diagnose the **first** failure in the log; later failures are usually cascade.

## Architecture rules

- Pure-Kotlin, JVM-testable core stays separated from Android UI: `editing/SyntaxHighlighter`, `search/FindEngine` + `search/FindInFiles`, `tools/Json` (self-written parser — `org.json` is an Android stub that throws in local unit tests), `tools/XmlTool`, `preview/MarkdownRenderer`, `core/TextUtils`.
- Every fixed logic bug gets a regression test under `app/src/test`.
- Dependencies go through the version catalog (`gradle/libs.versions.toml`) — no hardcoded coordinates.
- User-facing strings live in `res/values/strings.xml`, never inline in Kotlin.

## Security standards (non-negotiable)

- All user-supplied data (opened files, plugin manifests, regex, HTML/Markdown) is untrusted.
- XML: DOCTYPE declarations must stay banned (`disallow-doctype-decl`, required), external entities/DTD/XInclude disabled; tolerate unsupported optional hardening features but never the critical ones — fail closed.
- WebView preview: JS disabled, `allowFileAccess=false`, `allowContentAccess=false`, `blockNetworkLoads=true`, `destroy()` in `onDestroy`.
- Identifiers from external input (e.g. plugin pack names) must be sanitized (no `/`, `..`, path characters).
- Third-party plugin commands run in the declarative op sandbox only (`upper/lower/trim/base64-encode/base64-decode/insert`) — never arbitrary code execution.
- Least-privilege: no permission without a call site (the app intentionally declares none today).

## Design standards

- Features must be complete and reachable: if code exists, a menu/button must invoke it.
- Errors surface to the user (toast/dialog with detail), never swallowed.
- Long operations run off the main thread and offer cancel; unbounded work is capped.
- Settings persist (`notedroid_settings` SharedPreferences) and are applied on launch/resume.
- State survives rotation (`onSaveInstanceState`) and hard process kill (session snapshot with restore prompt).
- Editor performance: highlighting debounced, no per-keystroke relayout when nothing changed.

## Known bug patterns in this codebase

- Kotlin escapes: `'\n'` in char literals written as literal backslash-n — invalid escape; watch for it in codegen/patches.
- Inside `popup { ... }` helpers (`PopupMenu.() -> Unit`), `this` is the **PopupMenu** — qualify `this@MainActivity` for Toasts/Intents.
- JVM vs Android: `DocumentBuilderFactory`/`TransformerFactory` feature support differs; wrap optional features, require security-critical ones.
- Nullable receivers: `editor.text` is `Editable?`, `tab.customView` is nullable.

## Workflow expectations

- Commit focused units; push only to the task branch (`vibe/notedroid-plus-plus-android`); CI must be green on the final commit before claiming work is done.
- Deferred work becomes a GitHub issue (1 thing = 1 issue; group only strictly related items) with Summary/Details/Acceptance sections; check for duplicates across **all** issue states first.
- Keep README and PR descriptions matching shipped reality — never overclaim features or test counts.
- After addressing reviews, post a comment on the PR summarizing fixed vs deferred (with issue links).

## Open backlog (as of last session)

- #8: On-device verification of gutter/bracket geometry
- #23: Full Notepad++ localization port + language-file gap analysis
