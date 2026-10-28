package com.notedroidplusplus.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.PrintDocumentAdapter.LayoutResultCallback
import android.print.PrintDocumentAdapter.WriteResultCallback
import android.os.CancellationSignal
import android.text.InputFilter
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.ScaleGestureDetector
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.tabs.TabLayout
import com.notedroidplusplus.app.core.Document
import com.notedroidplusplus.app.core.FileManager
import com.notedroidplusplus.app.core.RecentFiles
import com.notedroidplusplus.app.core.RecentFilesSettings
import com.notedroidplusplus.app.core.MacroRecorder
import com.notedroidplusplus.app.core.MacroStore
import com.notedroidplusplus.app.core.SaveAsCoordinator
import com.notedroidplusplus.app.core.WindowSort
import com.notedroidplusplus.app.core.SessionStore
import com.notedroidplusplus.app.core.TextUtils
import com.notedroidplusplus.app.databinding.ActivityMainBinding
import com.notedroidplusplus.app.editing.CodeEditText
import com.notedroidplusplus.app.editing.AutoComplete
import com.notedroidplusplus.app.editing.ContentSniffer
import com.notedroidplusplus.app.editing.EdgeColumns
import com.notedroidplusplus.app.editing.MatchedPairs
import com.notedroidplusplus.app.editing.ShowSymbolFlags
import com.notedroidplusplus.app.search.resolved
import com.notedroidplusplus.app.editing.MarkdownActions
import com.notedroidplusplus.app.editing.SyntaxHighlighter
import com.notedroidplusplus.app.files.FindInFilesActivity
import com.notedroidplusplus.app.plugins.PluginHost
import com.notedroidplusplus.app.plugins.PluginImportActivity
import com.notedroidplusplus.app.plugins.PluginRegistry
import com.notedroidplusplus.app.search.SearchHit
import com.notedroidplusplus.app.search.findAll
import com.notedroidplusplus.app.preview.PreviewModeMemo
import com.notedroidplusplus.app.preview.PreviewView
import com.notedroidplusplus.app.search.FindOptions
import com.notedroidplusplus.app.search.findNext
import com.notedroidplusplus.app.search.findPrev
import com.notedroidplusplus.app.search.replaceAll
import com.notedroidplusplus.app.settings.SettingsActivity
import com.notedroidplusplus.app.settings.ThemeRegistry
import com.notedroidplusplus.app.tools.CharacterTable
import com.notedroidplusplus.app.tools.HashTool
import com.notedroidplusplus.app.tools.JsonTool
import com.notedroidplusplus.app.tools.XmlTool
import kotlin.concurrent.thread
import java.nio.charset.Charset

class MainActivity : AppCompatActivity(), PluginHost {

    companion object {
        private const val KEY_TABS = "tabs_state"
        private const val KEY_ACTIVE = "active_tab"
        private const val SAVE_ALL_OWNER = "save-all"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var editor: CodeEditText
    private lateinit var files: FileManager
    private lateinit var prefs: SharedPreferences
    private lateinit var pluginRegistry: PluginRegistry
    private lateinit var recentFiles: RecentFiles
    private lateinit var sessionStore: SessionStore
    private var scaleDetector: ScaleGestureDetector? = null

    data class TabSession(
        var document: Document,
        var tab: TabLayout.Tab?,
        val history: com.notedroidplusplus.app.core.UndoHistory = com.notedroidplusplus.app.core.UndoHistory(),
        var caret: Int = 0,
        var readOnly: Boolean = false,
        var lastModifiedAt: Long = 0L,
        var monitoring: Boolean = false,
        var bookmarks: Set<Int> = emptySet(),
        var textDirectionRtl: Boolean = false
    )

    private val tabs = mutableListOf<TabSession>()
    private var active = -1
    private var newCounter = 1
    private var findOptions = FindOptions()
    private var findPanelOpen = false
    private val previewModeMemo = PreviewModeMemo()

    /** Per-flow Save As payload, keyed by [SaveAsCoordinator.Flow.key]. */
    private data class SaveAsFlowData(
        val session: TabSession,
        var encoding: String? = null,
        val closeAfter: Boolean = false
    )

    private val saveAsCoordinator = SaveAsCoordinator()
    private val saveAsFlowData = mutableMapOf<Int, SaveAsFlowData>()

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openUri(it) }
        }

    private val saveAsLauncher =
        registerForActivityResult(object : ActivityResultContract<Pair<String, String>, Uri?>() {
            override fun createIntent(context: android.content.Context, input: Pair<String, String>): Intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = input.first
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_TITLE, input.second)
                }

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
                if (resultCode == RESULT_OK) intent?.data else null
        }) { uri ->
            val flow = saveAsCoordinator.active ?: return@registerForActivityResult
            val data = saveAsFlowData[flow.key] ?: return@registerForActivityResult
            if (uri == null) {
                cancelSaveAsFlow(flow.key)
                return@registerForActivityResult
            }
            data.encoding?.let { enc -> data.session.document = data.session.document.copy(encoding = enc) }
            val saved = saveSessionTo(data.session, uri)
            if (data.closeAfter && saved) {
                val idx = tabs.indexOf(data.session)
                if (idx >= 0) removeTab(idx)
            }
            saveAsFlowData.remove(flow.key)
            saveAsCoordinator.finish(flow.key)?.let { startSaveAsFlow(it.key) }
        }

    private val fifLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            val uri = data.getStringExtra(FindInFilesActivity.EXTRA_OPEN_URI) ?: return@registerForActivityResult
            val line = data.getIntExtra(FindInFilesActivity.EXTRA_OPEN_LINE, 1)
            openUriAtLine(Uri.parse(uri), line)
        }

    private fun openUriAtLine(uri: Uri, line: Int) {
        try {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val loaded = files.load(uri)
            val fileName = files.displayName(uri)
            val session = loadIntoTab(uri, loaded, fileName, ContentSniffer.detectWithFileName(fileName, loaded.text))
            val offset = TextUtils.lineIndexToOffset(session.document.text, (line - 1).coerceAtLeast(0))
            session.caret = offset
            editor.setSelection(offset.coerceAtMost(session.document.text.length))
            refreshTabLabels()
            updateStatus()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_load_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        editor = binding.editor
        files = FileManager(FileManager.defaultIo(this))
        prefs = getSharedPreferences("notedroid_settings", MODE_PRIVATE)
        pluginRegistry = PluginRegistry(this)
        recentFiles = RecentFiles(this)
        sessionStore = SessionStore(this)
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val size = editor.textSize / resources.displayMetrics.scaledDensity
                val newSize = (size * detector.scaleFactor).coerceIn(8f, 40f)
                if (kotlin.math.abs(newSize - size) >= 0.5f) {
                    editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, newSize)
                    prefs.edit().putInt("font_size", newSize.toInt()).apply()
                    editor.updateGutterPublic()
                    editor.rehighlight()
                }
                return true
            }
        })
        editor.setOnTouchListener { v, event ->
            scaleDetector?.onTouchEvent(event)
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP && editor.virtualSpace) {
                // compute the tapped column and let the editor pad to it after placement
                val gl = editor.layout
                if (gl != null) {
                    val x = event.x + editor.scrollX - editor.compoundPaddingLeft
                    val y = event.y + editor.scrollY - editor.paddingTop
                    val line = gl.getLineForVertical(y.toInt().coerceIn(0, (gl.lineCount - 1).coerceAtLeast(0)))
                    val col = ((x / editor.paint.measureText("8")).toInt()).coerceAtLeast(0)
                    editor.post { editor.applyVirtualSpace(col) }
                }
            }
            v.performClick()
            false
        }

        applyPersistedSettings()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { activateTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) { syncActiveDocument() }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.tabLayout.setOnLongClickListener {
            showTabCloseMenu()
            true
        }
        for (child in binding.tabLayout.touchables) {
            child.setOnLongClickListener { showTabCloseMenu(); true }
        }

        binding.btnNew.setOnClickListener { addTab(null, null) }
        binding.btnOpen.setOnClickListener { openLauncher.launch(arrayOf("text/*", "application/json", "application/xml")) }
        binding.btnSave.setOnClickListener { saveActive() }
        binding.btnFind.setOnClickListener { toggleFindPanel(false) }
        binding.btnPreview.setOnClickListener { preview() }
        binding.btnPrettify.setOnClickListener { prettify() }
        binding.mdBold.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.bold(t, s, e) } }
        binding.mdItalic.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.italic(t, s, e) } }
        binding.mdStrike.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.strike(t, s, e) } }
        binding.mdCode.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.inlineCode(t, s, e) } }
        binding.mdH1.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.heading(t, s, e, 1) } }
        binding.mdH2.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.heading(t, s, e, 2) } }
        binding.mdH3.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.heading(t, s, e, 3) } }
        binding.mdBulletList.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.list(t, s, e, ordered = false) } }
        binding.mdNumberedList.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.list(t, s, e, ordered = true) } }
        binding.mdLink.setOnClickListener { promptLink() }
        binding.mdCodeBlock.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.codeBlock(t, s, e) } }
        binding.mdTable.setOnClickListener { applyMarkdown { t, s, e -> MarkdownActions.table(t, s) } }
        binding.btnFindNext.setOnClickListener { findNextMatch(down = true) }
        binding.btnFindPrev.setOnClickListener { findNextMatch(down = false) }
        binding.btnFindClose.setOnClickListener { binding.findPanel.visibility = View.GONE; findPanelOpen = false }
        binding.btnReplace.setOnClickListener { replaceOne() }
        binding.btnReplaceAll.setOnClickListener { replaceEverything() }
        binding.cbMatchCase.setOnCheckedChangeListener { _, _ -> refreshFindOptions() }
        binding.cbWholeWord.setOnCheckedChangeListener { _, _ -> refreshFindOptions() }
        binding.cbRegex.setOnCheckedChangeListener { _, _ -> refreshFindOptions() }
        binding.cbExtended.setOnCheckedChangeListener { _, _ -> refreshFindOptions() }
        binding.cbDotAll.setOnCheckedChangeListener { _, _ -> refreshFindOptions() }
        binding.btnCount.setOnClickListener { countMatchesInDoc() }
        binding.btnFindAll.setOnClickListener { findAllInDocs(allTabs = false) }
        binding.btnMarkAll.setOnClickListener { markAll() }
        binding.cbWrapAround.isChecked = true

        binding.findInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                findNextMatch(down = true)
                true
            } else false
        }

        editor.addTextChangedListener(tw)
        editor.onSelectionChange = { updateStatus() }
        editor.onOverwriteToggled = { updateStatus() }
        binding.statusInsOvr.setOnClickListener {
            editor.overwriteMode = !editor.overwriteMode
        }
        attachCompletionPopup()

        if (savedInstanceState != null) {
            restoreState(savedInstanceState)
        } else {
            val intentUri = intent?.data
            if (intentUri != null) {
                addTab(null, null)
                openUri(intentUri)
            } else {
                offerRestoreSession()
            }
        }
        updateStatus()
    }

    private fun offerRestoreSession() {
        val saved = sessionStore.load()
        if (!saved.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.restore_session_title)
                .setMessage(getString(R.string.restore_session_msg, saved.size))
                .setPositiveButton(R.string.yes) { _, _ -> restoreSession(saved) }
                .setNegativeButton(R.string.no) { _, _ -> { sessionStore.clear(); offerBackupRestore() } }
                .setCancelable(false)
                .show()
        } else {
            offerBackupRestore()
        }
    }

    /** Crash-recovery fallback: reopen dirty buffers saved by the periodic backup. */
    private fun offerBackupRestore() {
        val backups = readPeriodicBackups()
        if (backups.isEmpty()) {
            addTab(null, null)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_session_title)
            .setMessage(getString(R.string.backup_restore_msg, backups.size))
            .setPositiveButton(R.string.yes) { _, _ ->
                backups.forEach { (name, text) -> addTab(null, text, name = name) }
                Toast.makeText(this, R.string.backup_restored, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.no) { _, _ -> addTab(null, null) }
            .setCancelable(false)
            .show()
    }

    private fun restoreSession(saved: List<SessionStore.TabState>) {
        saved.forEachIndexed { i, st ->
            val session = addTab(st.uri?.let { Uri.parse(it) }, st.text, name = st.name)
            session.document = session.document.copy(
                language = st.language,
                encoding = st.encoding,
                lineEnding = st.lineEnding,
                modified = st.modified
            )
            session.caret = st.caret
            session.bookmarks = st.bookmarks.toSet()
        }
        sessionStore.clear()
        activateTab(0)
        refreshTabLabels()
    }

    override fun onPause() {
        super.onPause()
        saveSessionSnapshot()
        if (prefs.getBoolean("periodic_backup", true)) writePeriodicBackup()
    }

    override fun onStop() {
        super.onStop()
        saveSessionSnapshot()
    }

    private fun saveSessionSnapshot() {
        if (tabs.isEmpty()) return
        if (!prefs.getBoolean("session_snapshot", true)) return
        syncActiveDocument()
        activeSession()?.let { it.caret = editor.selectionStart }
        val hasContent = tabs.any { it.document.text.isNotEmpty() }
        if (!hasContent) return
        sessionStore.save(tabs.map { s ->
            SessionStore.TabState(
                s.document.uri,
                s.document.fileName,
                s.document.text,
                s.document.language,
                s.document.encoding,
                s.document.lineEnding,
                s.document.modified,
                s.caret,
                s.bookmarks.toList()
            )
        })
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { openUri(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        syncActiveDocument()
        activeSession()?.let { it.caret = editor.selectionStart }
        val states = tabs.map { arrayOf(it.document.uri, it.document.fileName, it.document.text, it.document.language.name, it.document.encoding, it.document.lineEnding, it.document.modified, it.caret, it.readOnly, ArrayList(it.bookmarks), it.textDirectionRtl) }
        outState.putSerializable(KEY_TABS, ArrayList(states))
        outState.putInt(KEY_ACTIVE, active)
    }

    @Suppress("UNCHECKED_CAST")
    private fun restoreState(state: Bundle) {
        val saved = state.getSerializable(KEY_TABS) as? ArrayList<Array<Any?>> ?: return
        for (arr in saved) {
            val doc = Document(
                uri = arr[0] as String?,
                fileName = arr[1] as String,
                text = arr[2] as String,
                language = runCatching { LanguageMode.valueOf(arr[3] as String) }.getOrDefault(LanguageMode.NORMAL_TEXT),
                encoding = arr[4] as String,
                lineEnding = arr[5] as String,
                modified = arr[6] as Boolean
            )
            val session = addTab(doc.uri?.let { Uri.parse(it) }, doc.text, name = doc.fileName)
            session.document = doc
            session.caret = arr[7] as Int
            session.readOnly = arr[8] as? Boolean ?: false
            session.bookmarks = (arr[9] as? ArrayList<Int>)?.toSet() ?: emptySet()
            session.textDirectionRtl = arr[10] as? Boolean ?: false
        }
        if (tabs.isEmpty()) {
            addTab(null, null)
            return
        }
        val act = state.getInt(KEY_ACTIVE, 0).coerceIn(0, tabs.size - 1)
        activateTab(act)
        activeSession()?.let { editor.setSelection(it.caret.coerceIn(0, it.document.text.length)) }
        refreshTabLabels()
    }

    private val backupHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var backupRunnable: Runnable? = null

    private fun startPeriodicBackup() {
        backupRunnable?.let { backupHandler.removeCallbacks(it) }
        if (!prefs.getBoolean("periodic_backup", true)) return
        val minutes = prefs.getInt("periodic_backup_min", 3).coerceIn(1, 30)
        val runnable = object : Runnable {
            override fun run() {
                thread(name = "periodic-backup") { runCatching { writePeriodicBackup() } }
                backupHandler.postDelayed(this, minutes * 60_000L)
            }
        }
        backupRunnable = runnable
        backupHandler.postDelayed(runnable, minutes * 60_000L)
    }

    /** Writes dirty unsaved buffers to app-private storage for crash recovery. */
    private fun writePeriodicBackup() {
        val dirty = tabs.filter { it.document.modified && it.document.uri == null && it.document.text.isNotEmpty() }
        val dir = java.io.File(filesDir, "backups")
        if (dirty.isEmpty()) {
            // retention: nothing dirty anymore - drop backups that are no longer needed
            dir.listFiles()?.forEach { it.delete() }
            return
        }
        dir.mkdirs()
        val wanted = dirty.map { s ->
            s.document.fileName.map { c -> if (c.isLetterOrDigit() || c in "-_.") c else '_' }.joinToString("")
        }.toSet()
        // r4: drop stale backups for buffers that are no longer dirty
        dir.listFiles()?.forEach { f -> if (f.extension == "bak" && f.nameWithoutExtension !in wanted) f.delete() }
        dirty.forEach { s ->
            val safe = s.document.fileName.map { c -> if (c.isLetterOrDigit() || c in "-_.") c else '_' }.joinToString("")
            runCatching { java.io.File(dir, "$safe.bak").writeText(s.document.text) }
        }
    }

    /** Lists recoverable periodic-backup buffers (name -> text), empty when none. */
    private fun readPeriodicBackups(): List<Pair<String, String>> {
        val dir = java.io.File(filesDir, "backups")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.extension == "bak" }
            ?.mapNotNull { f -> runCatching { f.nameWithoutExtension to f.readText() }.getOrNull() }
            .orEmpty()
    }

    private fun applyPersistedSettings() {
        applyTheme(ThemeRegistry.byName(prefs.getString("theme_name", "Default") ?: "Default"))
        editor.showLineNumbers = prefs.getBoolean("show_line_numbers", true)
        editor.setHorizontallyScrolling(!prefs.getBoolean("wrap_lines", false))
        val fontSize = prefs.getInt("font_size", 14)
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat().coerceIn(8f, 40f))
        applyEditingSettings()
    }

    private fun applyEditingSettings() {
        editor.caretWidth = prefs.getInt("caret_width", 2).coerceIn(2, 3)
        editor.currentLineStyle = prefs.getString("current_line_style", "highlight") ?: "highlight"
        editor.virtualSpace = prefs.getBoolean("virtual_space", false)
        editor.scrollPastEnd = prefs.getBoolean("scroll_past_end", false)
        editor.leftPaddingDp = prefs.getInt("editor_padding_left", 4).coerceIn(0, 64)
        editor.rightPaddingDp = prefs.getInt("editor_padding_right", 4).coerceIn(0, 64)
        editor.showVerticalEdge = prefs.getBoolean("show_vertical_edge", true)
        applyShowSymbol()
        editor.tabSize = prefs.getInt("tab_size", 4).coerceIn(1, 16)
        editor.autoInsertPairs = prefs.getBoolean("auto_insert_pairs", false)
        editor.highlightThresholdBytes = prefs.getInt("highlight_threshold_mb", 5).coerceIn(1, 50) * 1024L * 1024L
        startPeriodicBackup()
        editor.edgeBackgroundMode = prefs.getBoolean("edge_background_mode", false)
        editor.edgeColumns = EdgeColumns.parse(prefs.getString("edge_columns", null))
    }

    override fun onResume() {
        super.onResume()
        applyPersistedSettings()
    }

    // ---- PluginHost ----

    override val context: android.content.Context get() = this

    override fun getText(): String = editor.text?.toString() ?: ""

    override fun setText(text: String) = setEditorText(text)

    override fun getSelection(): String =
        editor.text?.substring(editor.selectionStart.coerceAtMost(editor.selectionEnd), editor.selectionEnd.coerceAtLeast(editor.selectionStart)).orEmpty()

    override fun caret(): Int = editor.selectionStart.coerceAtLeast(0)

    override fun language(): LanguageMode = activeSession()?.document?.language ?: LanguageMode.NORMAL_TEXT

    override fun showStatus(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setEditorText(text: String) {
        val s = activeSession() ?: return
        if (s.readOnly) return
        val before = editor.text?.toString() ?: ""
        editor.removeTextChangedListener(tw)
        editor.setText(text)
        editor.setSelection(text.length)
        editor.addTextChangedListener(tw)
        if (before != text) {
            pushWholeTextChange(s, before, text)
            s.document = s.document.copy(text = text, modified = true)
        } else {
            s.document = s.document.copy(text = text)
        }
        refreshTabLabels()
        updateStatus()
    }

    /** Record a whole-buffer replacement as one undo step built from a minimal insert/delete pair. */
    private fun pushWholeTextChange(s: TabSession, before: String, after: String) {
        var prefix = 0
        val maxPrefix = minOf(before.length, after.length)
        while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(before.length, after.length) - prefix
        while (suffix < maxSuffix && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++
        val delStart = prefix
        val delEnd = before.length - suffix
        val insStart = prefix
        val insEnd = after.length - suffix
        val deleted = if (delEnd > delStart) before.substring(delStart, delEnd) else ""
        val inserted = if (insEnd > insStart) after.substring(insStart, insEnd) else ""
        val edits = buildList {
            if (deleted.isNotEmpty()) add(com.notedroidplusplus.app.core.UndoHistory.Edit.Delete(delStart, deleted))
            if (inserted.isNotEmpty()) add(com.notedroidplusplus.app.core.UndoHistory.Edit.Insert(insStart, inserted))
        }
        if (edits.isNotEmpty()) s.history.pushCompound(edits)
    }

    // ---- Text watcher (records per-tab edit-range undo) ----

    private val tw = object : android.text.TextWatcher {
        private var pendingStart = -1
        private var pendingRemoved: String? = null

        override fun beforeTextChanged(s: CharSequence?, start: Int, beforeCount: Int, afterCount: Int) {
            pendingRemoved = if (beforeCount > 0) {
                s?.subSequence(start, start + beforeCount)?.toString()
            } else null
            pendingStart = start
        }

        override fun onTextChanged(s: CharSequence?, start: Int, beforeCount: Int, count: Int) {
            val sess = activeSession() ?: return
            val removed = if (beforeCount > 0) pendingRemoved.takeIf { it != null } ?: "" else ""
            val inserted = if (count > 0) s?.subSequence(start, start + count)?.toString() ?: "" else ""
            when {
                removed.isNotEmpty() && inserted.isEmpty() -> {
                    sess.history.pushDelete(start, removed)
                    recordAction(MacroRecorder.Action.Delete(start, start + beforeCount))
                }
                inserted.isNotEmpty() && removed.isEmpty() -> {
                    sess.history.pushInsert(start, inserted)
                    recordAction(MacroRecorder.Action.Insert(start, inserted))
                }
                removed.isNotEmpty() && inserted.isNotEmpty() -> {
                    sess.history.pushCompound(
                        buildList {
                            add(com.notedroidplusplus.app.core.UndoHistory.Edit.Delete(start, removed))
                            add(com.notedroidplusplus.app.core.UndoHistory.Edit.Insert(start, inserted))
                        }
                    )
                    recordAction(MacroRecorder.Action.Delete(start, start + beforeCount))
                    recordAction(MacroRecorder.Action.Insert(start, inserted))
                }
            }
            pendingRemoved = null
            pendingStart = -1
        }
        override fun afterTextChanged(s: android.text.Editable?) {
            onTextChanged()
            maybeShowCompletion()
        }
    }

    // ---- Tabs ----

    private fun activeSession(): TabSession? = tabs.getOrNull(active)

    private fun addTab(uri: Uri?, initialText: String?, name: String? = null): TabSession {
        val fileName = name ?: uri?.let { files.displayName(it) } ?: getString(R.string.tab_new, newCounter++)
        val doc = if (uri == null && name == null) {
            // New Document defaults (#43)
            Document(
                uri = null,
                fileName = fileName,
                text = initialText ?: "",
                language = runCatching {
                    LanguageMode.valueOf(prefs.getString("new_doc_language", "NORMAL_TEXT") ?: "NORMAL_TEXT")
                }.getOrDefault(LanguageMode.NORMAL_TEXT),
                encoding = runCatching { Charset.forName(prefs.getString("new_doc_encoding", "UTF-8") ?: "UTF-8") }.getOrDefault(Charsets.UTF_8).name(),
                lineEnding = prefs.getString("new_doc_eol", "\n") ?: "\n"
            )
        } else {
            Document(
                uri = uri?.toString(),
                fileName = fileName,
                text = initialText ?: "",
                language = if (uri != null || name != null) LanguageMode.fromFileName(fileName) else LanguageMode.NORMAL_TEXT
            )
        }
        val tab = binding.tabLayout.newTab()
        tab.setCustomView(R.layout.tab_item)
        val label = tab.customView?.findViewById<android.widget.TextView>(R.id.tab_label)
        val closeBtn = tab.customView?.findViewById<android.widget.ImageView>(R.id.tab_close)
        label?.text = doc.fileName
        closeBtn?.setOnClickListener {
            val idx = tabs.indexOfFirst { it.tab === tab }
            if (idx >= 0) closeTabAt(idx)
        }
        binding.tabLayout.addTab(tab)
        val session = TabSession(doc, tab)
        tabs.add(session)
        activateTab(tabs.size - 1)
        return session
    }

    private fun openUri(uri: Uri) {
        try {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val loaded = files.load(uri)
            val fileName = files.displayName(uri)
            loadIntoTab(uri, loaded, fileName, ContentSniffer.detectWithFileName(fileName, loaded.text))
            recentFiles.add(uri.toString(), fileName, recentMax)
            refreshTabLabels()
            updateStatus()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_load_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Notepad++ behavior: opening a file while the active tab is an unmodified
     * empty document replaces that document in-place; otherwise a new tab is
     * created. The tab is always labeled with the file name.
     */
    private fun loadIntoTab(
        uri: Uri,
        loaded: FileManager.LoadedFile,
        fileName: String,
        language: LanguageMode
    ): TabSession {
        val current = activeSession()
        val reuse = current != null && current.document.uri == null &&
            !current.document.modified && current.document.text.isEmpty()
        if (reuse) {
            current!!.document = current.document.copy(
                uri = uri.toString(),
                fileName = fileName,
                text = loaded.text,
                language = language,
                encoding = loaded.charset.name(),
                lineEnding = loaded.lineEnding,
                hasBom = loaded.hadBom,
                modified = false
            )
            current.readOnly = false
            editor.filters = emptyArray()
            editor.removeTextChangedListener(tw)
            editor.setText(loaded.text)
            editor.language = language
            editor.setSelection(0)
            editor.addTextChangedListener(tw)
            refreshTabLabels()
            return current
        }
        val session = addTab(uri, loaded.text)
        session.document = session.document.copy(
            fileName = fileName,
            language = language,
            encoding = loaded.charset.name(),
            lineEnding = loaded.lineEnding,
            hasBom = loaded.hadBom
        )
        return session
    }

    private fun activateTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        if (previewMode != null) closePreview()
        activeSession()?.let { it.caret = editor.selectionStart }
        syncActiveDocument()
        active = index
        val s = tabs[index]
        editor.filters = emptyArray()
        editor.removeTextChangedListener(tw)
        editor.setText(s.document.text)
        editor.language = s.document.language
        editor.setSelection(s.caret.coerceIn(0, s.document.text.length))
        editor.addTextChangedListener(tw)
        editor.bookmarks = s.bookmarks
        editor.textDirection = if (s.textDirectionRtl) android.view.View.TEXT_DIRECTION_ANY_RTL
        else android.view.View.TEXT_DIRECTION_LTR
        editor.layoutDirection = if (s.textDirectionRtl) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
        applyReadOnly(s)
        refreshTabLabels()
        updateStatus()
    }

    private fun applyReadOnly(s: TabSession) {
        editor.readOnlyFlag = s.readOnly
        editor.filters = if (s.readOnly) {
            arrayOf(InputFilter { source, _, _, _, _, _ -> if (source.isNullOrEmpty()) null else "" })
        } else emptyArray()
    }

    private fun isEditorLocked(): Boolean = activeSession()?.readOnly == true

    private fun syncActiveDocument() {
        val s = activeSession() ?: return
        val text = editor.text?.toString() ?: return
        if (text != s.document.text || editor.text.isNullOrEmpty() != s.document.text.isEmpty()) {
            val modified = s.document.uri != null || text.isNotEmpty()
            s.document = s.document.copy(text = text, modified = modified)
        }
    }

    private fun onTextChanged() {
        val s = activeSession() ?: return
        val text = editor.text?.toString() ?: ""
        if (text != s.document.text) {
            val modified = s.document.uri != null || text.isNotEmpty()
            s.document = s.document.copy(text = text, modified = modified)
            s.lastModifiedAt = System.currentTimeMillis()
        }
        refreshTabLabels()
        updateStatus()
    }

    private fun refreshTabLabels() {
        tabs.forEach { s ->
            val label = s.tab?.customView?.findViewById<android.widget.TextView>(R.id.tab_label)
            label?.text = if (s.document.modified) "● ${s.document.fileName}" else s.document.fileName
        }
    }

    private fun updateStatus() {
        val text = editor.text?.toString() ?: ""
        val s0 = activeSession()
        s0?.let { it.bookmarks = editor.bookmarks }
        val pos = editor.selectionStart.coerceIn(0, text.length)
        val before = text.substring(0, pos)
        val line = before.count { it == '\n' } + 1
        val col = pos - before.lastIndexOf('\n')
        binding.statusPosition.text = getString(R.string.line_col, line, col)
        val s = activeSession()
        val selLen = editor.selectionEnd - editor.selectionStart
        binding.statusSelection.text = when {
            selLen > 0 -> getString(R.string.sel, selLen)
            s?.readOnly == true -> getString(R.string.readonly_status)
            else -> ""
        }
        binding.statusEncoding.text = s?.document?.encoding ?: "UTF-8"
        binding.statusEol.text = when (s?.document?.lineEnding) {
            "\r\n" -> "Windows (CR LF)"
            "\r" -> "Mac (CR)"
            else -> "Unix (LF)"
        }
        binding.statusLanguage.text = s?.document?.language?.displayName ?: ""
        val md = s != null && previewMode == null && previewModeMemo.modeFor(
            text,
            s.document.language == LanguageMode.MARKDOWN,
            s.document.language == LanguageMode.HTML
        ) == PreviewView.Mode.MARKDOWN
        binding.markdownToolbar.visibility = if (md) View.VISIBLE else View.GONE
        binding.statusInsOvr.text = if (editor.overwriteMode) {
            getString(R.string.status_ovr)
        } else {
            getString(R.string.status_ins)
        }
    }

    // ---- Save / Close ----

    private fun saveActive() {
        val s = activeSession() ?: return
        val uri = s.document.uri
        if (uri != null) {
            saveSessionTo(s, Uri.parse(uri))
        } else {
            saveAsWithEncoding(s)
        }
    }

    private fun mimeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return when {
            mime != null && mime.startsWith("text/") -> mime
            ext == "json" -> "application/json"
            ext == "xml" || ext == "svg" -> "application/xml"
            else -> "text/plain"
        }
    }

    private fun saveAsWithEncoding(s: TabSession) {
        beginSaveAsFlow(s, closeAfter = false, owner = null)
    }

    /** Registers a Save As flow; starts it immediately or queues it behind the active flow. */
    private fun beginSaveAsFlow(s: TabSession, closeAfter: Boolean, owner: String?) {
        val req = saveAsCoordinator.request(owner)
        saveAsFlowData[req.flow.key] = SaveAsFlowData(session = s, closeAfter = closeAfter)
        if (req.startNow) startSaveAsFlow(req.flow.key)
    }

    /** Opens the encoding dialog for the given flow and launches the picker on choice. */
    private fun startSaveAsFlow(key: Int) {
        val data = saveAsFlowData[key] ?: return
        val s = data.session
        if (!tabs.contains(s) ||
            (saveAsCoordinator.active?.owner == SAVE_ALL_OWNER && !s.document.modified)
        ) {
            cancelSaveAsFlow(key)
            return
        }
        val encodings = TextUtils.names()
        AlertDialog.Builder(this)
            .setTitle(R.string.encoding_title)
            .setItems(encodings.toTypedArray()) { _, which ->
                if (saveAsCoordinator.active?.key != key) return@setItems
                data.encoding = encodings[which]
                saveAsLauncher.launch(mimeFor(s.document.fileName) to s.document.fileName)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> cancelSaveAsFlow(key) }
            .setOnCancelListener { cancelSaveAsFlow(key) }
            .show()
    }

    /** Drops the flow (owner-scoped: same-owner queued flows go too) and starts the next one. */
    private fun cancelSaveAsFlow(key: Int) {
        saveAsFlowData.remove(key)
        saveAsCoordinator.cancel(key).next?.let { startSaveAsFlow(it.key) }
    }

    private fun saveSessionTo(s: TabSession, uri: Uri): Boolean {
        return try {
            val charset = Charset.forName(s.document.encoding)
            val text = s.document.text
            files.save(uri, text, charset, s.document.lineEnding, s.document.hasBom)
            s.document = s.document.copy(
                uri = uri.toString(),
                fileName = files.displayName(uri),
                modified = false,
                language = LanguageMode.fromFileName(files.displayName(uri)),
                hasBom = s.document.hasBom
            )
            editor.language = s.document.language
            refreshTabLabels()
            updateStatus()
            true
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_save_failed, e.message), Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun closeTabAt(index: Int) {
        val s = tabs.getOrNull(index) ?: return
        if (!s.document.modified) {
            removeTab(index)
            return
        }
        val prevActive = active
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_save_title)
            .setMessage(getString(R.string.confirm_save_msg, s.document.fileName))
            .setPositiveButton(R.string.yes) { _, _ ->
                if (s.document.uri != null) {
                    if (saveSessionTo(s, Uri.parse(s.document.uri))) removeTab(index)
                } else {
                    beginSaveAsFlow(s, closeAfter = true, owner = null)
                }
            }
            .setNegativeButton(R.string.no) { _, _ -> removeTab(index) }
            .setNeutralButton(R.string.cancel) { _, _ -> activateTab(prevActive) }
            .show()
    }

    private fun showTabCloseMenu() {
        popup {
            item(getString(R.string.action_close)) { closeTabAt(active) }
            activeSession()?.let { s -> item(getString(R.string.action_open_containing_folder)) { openContainingFolder(s) } }
            item(getString(R.string.action_close_others)) {
                val keep = active
                val dirty = tabs.count { it.document.modified && tabs.indexOf(it) != keep }
                tabs.indices.filterNot { it == keep }.reversed().forEach { idx ->
                    if (!tabs[idx].document.modified) removeTab(idx)
                }
                activateTab(0.coerceAtMost(tabs.size - 1))
                if (dirty > 0) Toast.makeText(this@MainActivity, getString(R.string.tabs_kept_dirty, dirty), Toast.LENGTH_SHORT).show()
            }
            item(getString(R.string.action_close_all)) {
                val dirty = tabs.count { it.document.modified }
                tabs.indices.reversed().forEach { idx ->
                    if (!tabs[idx].document.modified) removeTab(idx)
                }
                if (tabs.isEmpty()) addTab(null, null)
                if (dirty > 0) Toast.makeText(this@MainActivity, getString(R.string.tabs_kept_dirty, dirty), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun closeActive() {
        val s = activeSession() ?: return
        if (s.document.modified) {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_save_title)
                .setMessage(getString(R.string.confirm_save_msg, s.document.fileName))
                .setPositiveButton(R.string.yes) { _, _ ->
                    val uri = s.document.uri
                    if (uri != null) {
                        if (saveSessionTo(s, Uri.parse(uri))) removeTab(active)
                    } else {
                        beginSaveAsFlow(s, closeAfter = true, owner = null)
                    }
                }
                .setNegativeButton(R.string.no) { _, _ -> removeTab(active) }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else removeTab(active)
    }

    private fun removeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        tabs[index].tab?.let { binding.tabLayout.removeTab(it) }
        tabs.removeAt(index)
        if (tabs.isEmpty()) { addTab(null, null); return }
        val next = (index).coerceAtMost(tabs.size - 1)
        active = next
        activateTab(next)
    }

    // ---- Find / Replace ----

    private fun toggleFindPanel(replace: Boolean) {
        findPanelOpen = !findPanelOpen
        binding.findPanel.visibility = if (findPanelOpen) View.VISIBLE else View.GONE
        binding.replaceRow.visibility = if (findPanelOpen && replace) View.VISIBLE else View.GONE
        if (findPanelOpen) {
            if (binding.findInput.text.isNullOrBlank() && prefs.getBoolean("autofill_find", true)) {
                val sel = getSelection()
                if (sel.isNotEmpty()) {
                    val cap = prefs.getInt("find_max_chars", 256).coerceIn(16, 1024)
                    binding.findInput.setText(sel.take(cap))
                }
            }
            binding.findInput.requestFocus()
        }
    }

    private fun refreshFindOptions() {
        findOptions = findOptions.copy(
            query = binding.findInput.text.toString(),
            replaceWith = binding.replaceInput.text.toString(),
            matchCase = binding.cbMatchCase.isChecked,
            wholeWord = binding.cbWholeWord.isChecked,
            regex = binding.cbRegex.isChecked,
            extended = binding.cbExtended.isChecked,
            dotMatchesNewline = binding.cbDotAll.isChecked,
            wrapAround = binding.cbWrapAround.isChecked
        )
    }

    private fun findNextMatch(down: Boolean) {
        refreshFindOptions()
        val text = editor.text?.toString() ?: return
        val idx = if (down) {
            findNext(text, editor.selectionEnd, findOptions)
        } else {
            findPrev(text, editor.selectionStart, findOptions)
        }
        if (idx >= 0) {
            editor.setSelection(idx, idx + findQueryLength(text, idx))
        } else {
            Toast.makeText(this, R.string.toast_no_match, Toast.LENGTH_SHORT).show()
        }
    }

    private fun findQueryLength(text: String, idx: Int): Int {
        return if (findOptions.regex) {
            runCatching { Regex(findOptions.query).find(text.substring(idx))?.value?.length ?: 0 }.getOrDefault(0)
        } else findOptions.query.length
    }

    private fun replaceOne() {
        if (isEditorLocked()) return
        refreshFindOptions()
        val text = editor.text?.toString() ?: return
        val selStart = editor.selectionStart.coerceAtMost(editor.selectionEnd)
        val selEnd = editor.selectionEnd.coerceAtLeast(editor.selectionStart)
        val selected = text.substring(selStart, selEnd)
        val matched = if (findOptions.regex) {
            runCatching {
                val re = if (findOptions.matchCase) Regex(findOptions.query) else Regex(findOptions.query, RegexOption.IGNORE_CASE)
                re.matchAt(text, selStart)?.value == selected
            }.getOrDefault(false)
        } else {
            selected.equals(findOptions.query, ignoreCase = !findOptions.matchCase)
        }
        if (matched && selEnd > selStart) {
            editor.text?.replace(selStart, selEnd, findOptions.replaceWith)
        }
        findNextMatch(down = true)
    }

    private fun replaceEverything() {
        refreshFindOptions()
        val (newText, count) = replaceAll(editor.text?.toString() ?: "", findOptions)
        recordAction(
            MacroRecorder.Action.ReplaceAll(
                findOptions.query, findOptions.replaceWith, findOptions.regex, findOptions.matchCase
            )
        )
        setEditorText(newText)
        Toast.makeText(this, getString(R.string.replaced_occurrences, count), Toast.LENGTH_SHORT).show()
    }

    private fun gotoLine() {
        val text = editor.text?.toString() ?: return
        val max = text.count { it == '\n' } + 1
        val input = EditText(this).apply { hint = getString(R.string.goto_line, max) }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_goto)
            .setView(input)
            .setPositiveButton(R.string.yes) { _, _ ->
                val line = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val offset = TextUtils.lineIndexToOffset(text, (line - 1).coerceIn(0, max - 1))
                recordAction(MacroRecorder.Action.GoToLine(line))
                editor.setSelection(offset)
                editor.requestFocus()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Undo / Redo ----

    private fun performUndo() {
        if (isEditorLocked()) return
        val s = activeSession() ?: return
        val current = editor.text?.toString() ?: return
        val prev = s.history.undo(current) ?: return
        editor.removeTextChangedListener(tw)
        editor.setText(prev)
        editor.setSelection(prev.length)
        editor.addTextChangedListener(tw)
        s.document = s.document.copy(text = prev, modified = s.document.uri != null || prev.isNotEmpty())
        refreshTabLabels()
        updateStatus()
    }

    private fun performRedo() {
        if (isEditorLocked()) return
        val s = activeSession() ?: return
        val current = editor.text?.toString() ?: return
        val next = s.history.redo(current) ?: return
        editor.removeTextChangedListener(tw)
        editor.setText(next)
        editor.setSelection(next.length)
        editor.addTextChangedListener(tw)
        s.document = s.document.copy(text = next, modified = s.document.uri != null || next.isNotEmpty())
        refreshTabLabels()
        updateStatus()
    }

    // ---- Tools ----

    private fun prettify() {
        when (activeSession()?.document?.language) {
            LanguageMode.JSON -> formatTool({ JsonTool.prettify(it) }, R.string.error_invalid_json)
            LanguageMode.XML -> formatTool({ XmlTool.prettify(it) }, R.string.error_invalid_xml)
            else -> Toast.makeText(this, R.string.tool_json_xml_only, Toast.LENGTH_SHORT).show()
        }
    }

    private fun minify() {
        when (activeSession()?.document?.language) {
            LanguageMode.JSON -> formatTool({ JsonTool.minify(it) }, R.string.error_invalid_json)
            LanguageMode.XML -> formatTool({ XmlTool.minify(it) }, R.string.error_invalid_xml)
            else -> Toast.makeText(this, R.string.tool_json_xml_only, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTool(tool: (String) -> Result<String>, errorRes: Int) {
        tool(editor.text?.toString() ?: "")
            .onSuccess { result -> setEditorText(result) }
            .onFailure { Toast.makeText(this, errorRes, Toast.LENGTH_SHORT).show() }
    }

    private var previewMode: PreviewView.Mode? = null
    private var previewView: PreviewView? = null

    private fun preview() {
        if (previewMode != null) {
            closePreview()
            return
        }
        val s = activeSession() ?: return
        syncActiveDocument()
        val content = s.document.text
        val mode = PreviewView.modeFor(
            content,
            s.document.language == LanguageMode.MARKDOWN,
            s.document.language == LanguageMode.HTML
        )
        if (mode == null) {
            Toast.makeText(this, R.string.tool_preview_only, Toast.LENGTH_SHORT).show()
            return
        }
        openPreview(mode, content)
    }

    private fun openPreview(mode: PreviewView.Mode, content: String) {
        val container = binding.previewContainer
        val v = previewView ?: PreviewView(this).also { previewView = it }
        if (v.parent == null) container.addView(
            v,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        v.render(content, mode)
        previewMode = mode
        container.visibility = View.VISIBLE
        binding.editor.visibility = View.GONE
    }

    private fun closePreview() {
        previewView?.close()
        binding.previewContainer.visibility = View.GONE
        binding.editor.visibility = View.VISIBLE
        previewMode = null
    }

    private fun applyMarkdown(op: (String, Int, Int) -> MarkdownActions.Result) {
        if (previewMode != null) return
        if (isEditorLocked()) return
        val text = editor.text?.toString() ?: return
        val s = editor.selectionStart.coerceAtLeast(0)
        val e = editor.selectionEnd.coerceAtLeast(s)
        val r = op(text, s, e)
        if (r.text == text) return
        editor.text?.replace(0, text.length, r.text)
        editor.setSelection(r.newStart.coerceIn(0, r.text.length), r.newEnd.coerceIn(0, r.text.length))
    }

    private fun promptLink() {
        if (previewMode != null) return
        val input = EditText(this).apply {
            hint = getString(R.string.md_link_url_hint)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.md_link_prompt)
            .setView(input)
            .setPositiveButton(R.string.md_link_ok) { _, _ ->
                applyMarkdown { t, s, e -> MarkdownActions.link(t, s, e, input.text.toString()) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun transformCase(kind: String?, op: (String, Int, Int) -> String) {
        val text = editor.text?.toString() ?: return
        val s = editor.selectionStart.coerceAtMost(editor.selectionEnd)
        val e = editor.selectionEnd.coerceAtLeast(editor.selectionStart)
        val replaced = op(text, s, e)
        if (replaced != text && kind != null) {
            recordAction(MacroRecorder.Action.TransformCase(kind, s, e))
        }
        setEditorText(replaced)
        editor.setSelection(s, e)
    }

    /** Applies a line-span transform (selection span or whole document), single undo step via setEditorText. */
    private fun applyLineOp(eol: String, op: (String, Int, Int) -> String) {
        if (isEditorLocked()) return
        val text = editor.text?.toString() ?: return
        val caret = editor.selectionStart.coerceIn(0, text.length)
        val (first, last) = TextUtils.lineSpanForSelection(text, editor.selectionStart, editor.selectionEnd)
        val replaced = op(text, first, last)
        setEditorText(replaced)
        if (replaced != text) {
            editor.setSelection(caret.coerceIn(0, replaced.length))
        }
    }

    private fun applySelectionOp(op: (String, Int, Int) -> String) {
        if (isEditorLocked()) return
        val text = editor.text?.toString() ?: return
        val s = editor.selectionStart.coerceAtMost(editor.selectionEnd)
        val e = editor.selectionEnd.coerceAtLeast(editor.selectionStart)
        val replaced = op(text, s, e)
        if (replaced != text) {
            setEditorText(replaced)
            editor.setSelection(s, e)
        }
    }

    private fun moveActiveLine(eol: String, up: Boolean) {
        if (isEditorLocked()) return
        val text = editor.text?.toString() ?: return
        val caret = editor.selectionStart.coerceIn(0, text.length)
        val (line, _) = TextUtils.lineSpanForSelection(text, editor.selectionStart, editor.selectionEnd)
        val replaced = if (up) TextUtils.moveLineUp(text, line, eol) else TextUtils.moveLineDown(text, line, eol)
        setEditorText(replaced)
        if (replaced != text) {
            val col = caret - TextUtils.lineIndexToOffset(text, line)
            val newLine = if (up) line - 1 else line + 1
            val newStart = TextUtils.lineIndexToOffset(replaced, newLine)
            editor.setSelection((newStart + col).coerceIn(0, replaced.length))
        }
    }

    private fun insertAtCaret(value: String) {
        if (isEditorLocked() || value.isEmpty()) return
        val pos = editor.selectionStart.coerceAtMost(editor.selectionEnd)
        editor.text?.insert(pos, value)
    }

    private fun promptCustomDate(now: Long) {
        val input = EditText(this).apply {
            hint = getString(R.string.date_format_hint)
            setText(prefs.getString("date_format_custom", "yyyy-MM-dd HH:mm:ss") ?: "yyyy-MM-dd HH:mm:ss")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.date_format_prompt)
            .setView(input)
            .setPositiveButton(R.string.md_link_ok) { _, _ ->
                val pattern = input.text.toString()
                prefs.edit().putString("date_format_custom", pattern).apply()
                val formatted = TextUtils.formatDateTime(pattern, now)
                if (formatted.isEmpty()) {
                    Toast.makeText(this, R.string.date_format_invalid, Toast.LENGTH_SHORT).show()
                } else insertAtCaret(formatted)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun copyToClipboard(value: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), value))
        Toast.makeText(this, R.string.copy_done, Toast.LENGTH_SHORT).show()
    }

    private fun saveAll() {
        var saved = 0
        val targets = tabs.toList()
        for ((index, s) in targets.withIndex()) {
            if (!s.document.modified) continue
            val uri = s.document.uri
            if (uri != null) {
                if (index == active) syncActiveDocument()
                if (saveSessionTo(s, Uri.parse(uri))) saved++
            }
        }
        Toast.makeText(this, getString(R.string.save_all_result, saved), Toast.LENGTH_SHORT).show()
        tabs.filter { it.document.modified && it.document.uri == null }
            .forEach { beginSaveAsFlow(it, closeAfter = false, owner = SAVE_ALL_OWNER) }
    }

    private fun reloadActive() {
        val s = activeSession() ?: return
        val uriString = s.document.uri ?: run {
            Toast.makeText(this, getString(R.string.error_load_failed, "no file"), Toast.LENGTH_SHORT).show()
            return
        }
        val doReload = {
            try {
                syncActiveDocument()
                val uri = Uri.parse(uriString)
                val loaded = files.load(uri)
                s.document = s.document.copy(
                    text = loaded.text,
                    encoding = loaded.charset.name(),
                    lineEnding = loaded.lineEnding,
                    hasBom = loaded.hadBom,
                    modified = false
                )
                s.history.clear()
                editor.filters = emptyArray()
                editor.removeTextChangedListener(tw)
                editor.setText(loaded.text)
                editor.language = ContentSniffer.detectWithFileName(s.document.fileName, loaded.text)
                editor.setSelection(0)
                editor.addTextChangedListener(tw)
                applyReadOnly(s)
                refreshTabLabels()
                updateStatus()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.reload_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
        if (s.document.modified) {
            AlertDialog.Builder(this)
                .setTitle(R.string.reload_confirm_title)
                .setMessage(R.string.reload_confirm_msg)
                .setPositiveButton(R.string.yes) { _, _ -> doReload() }
                .setNegativeButton(R.string.no, null)
                .show()
        } else doReload()
    }

    private fun toggleReadOnly() {
        val s = activeSession() ?: return
        s.readOnly = !s.readOnly
        applyReadOnly(s)
        updateStatus()
    }

    private fun printActive() {
        val s = activeSession() ?: return
        syncActiveDocument()
        val text = s.document.text.ifEmpty { " " }
        val defaultMargin = 36f
        var layoutWidth = 0
        var layoutHeight = 0
        var marginH = defaultMargin
        var marginV = defaultMargin
        var printWidth = 595
        var printHeight = 842
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
                                  cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: android.os.Bundle?) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                // PdfDocument pages are in points (1/72"); media/margins come in mils (1/1000").
                val media = newAttributes.mediaSize
                printWidth = if (media != null) media.widthMils * 72 / 1000 else 595
                printHeight = if (media != null) media.heightMils * 72 / 1000 else 842
                val margins = newAttributes.minMargins
                marginH = margins?.leftMils?.times(72f)?.div(1000f) ?: defaultMargin
                marginV = margins?.topMils?.times(72f)?.div(1000f) ?: defaultMargin
                if (printWidth <= 2 * marginH || printHeight <= 2 * marginV) {
                    marginH = defaultMargin
                    marginV = defaultMargin
                }
                layoutWidth = (printWidth - 2 * marginH).toInt().coerceAtLeast(1)
                layoutHeight = (printHeight - 2 * marginV).toInt().coerceAtLeast(1)
                callback.onLayoutFinished(PrintDocumentInfo.Builder(s.document.fileName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build(), true)
            }

            override fun onWrite(pages: Array<out android.print.PageRange>?, destination: android.os.ParcelFileDescriptor,
                                 cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
                Thread {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return@Thread
                    }
                    val paint = TextPaint().apply {
                        textSize = 11f
                        isAntiAlias = true
                    }
                    val w = if (layoutWidth > 0) layoutWidth else (printWidth - 2 * defaultMargin).toInt().coerceAtLeast(1)
                    val h = if (layoutHeight > 0) layoutHeight else (printHeight - 2 * defaultMargin).toInt().coerceAtLeast(1)
                    val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(2f, 1.1f)
                        .build()
                    val lineHeight = if (layout.lineCount > 0) layout.getLineBottom(0).coerceAtLeast(1) else 1
                    val linesPerPage = (h / lineHeight).coerceAtLeast(1)
                    val pageCount = (layout.lineCount + linesPerPage - 1) / linesPerPage
                    val doc = PdfDocument()
                    try {
                        for (pageNo in 1..pageCount) {
                            val firstLine = (pageNo - 1) * linesPerPage
                            val lastLine = (firstLine + linesPerPage).coerceAtMost(layout.lineCount) - 1
                            val start = layout.getLineStart(firstLine)
                            val end = layout.getLineEnd(lastLine)
                            val pageLayout = StaticLayout.Builder.obtain(text, start, end, paint, w)
                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                .setLineSpacing(2f, 1.1f)
                                .build()
                            val info = PdfDocument.PageInfo.Builder(printWidth, printHeight, pageNo).create()
                            val page = doc.startPage(info)
                            page.canvas.save()
                            page.canvas.translate(marginH, marginV)
                            pageLayout.draw(page.canvas)
                            page.canvas.restore()
                            doc.finishPage(page)
                        }
                        java.io.FileOutputStream(destination.fileDescriptor).use { out ->
                            doc.writeTo(out)
                        }
                        callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback.onWriteFailed(e.message)
                    } finally {
                        doc.close()
                    }
                }.start()
            }
        }
        (getSystemService(PRINT_SERVICE) as PrintManager).print(
            getString(R.string.print_job_name, s.document.fileName), adapter, PrintAttributes.Builder().build()
        )
    }


    private fun setEncoding(name: String) {
        activeSession()?.let {
            it.document = it.document.copy(encoding = name)
            updateStatus()
        }
    }

    /** Notepad++ Convert-to: switch the save encoding after verifying the text fits. */
    private fun convertToEncoding(name: String) {
        val s = activeSession() ?: return
        val target = runCatching { Charset.forName(name) }.getOrNull() ?: return
        syncActiveDocument()
        if (!TextUtils.canRepresent(s.document.text, target)) {
            Toast.makeText(this, getString(R.string.convert_unrepresentable, name), Toast.LENGTH_LONG).show()
            return
        }
        s.document = s.document.copy(encoding = name, modified = true)
        refreshTabLabels()
        updateStatus()
        Toast.makeText(this, getString(R.string.convert_done, name), Toast.LENGTH_SHORT).show()
    }

    private fun setEol(eol: String) {
        activeSession()?.let {
            it.document = it.document.copy(lineEnding = eol)
            updateStatus()
        }
    }

    private fun setLanguage(mode: LanguageMode) {
        activeSession()?.let {
            it.document = it.document.copy(language = mode)
            editor.language = mode
            updateStatus()
        }
    }

    private fun runPluginCommand(pluginIdx: Int, cmdIdx: Int) {
        val plugins = pluginRegistry.discover()
        val cmd = plugins.getOrNull(pluginIdx)?.commands?.getOrNull(cmdIdx) ?: return
        try {
            cmd.run(this)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.plugin_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun showHelpMenu() = popup {
        item(getString(R.string.help_about)) { showAbout() }
        item(getString(R.string.help_manual)) { openLink("https://npp-user-manual.org/") }
        item(getString(R.string.help_project_page)) { openLink("https://github.com/gorzko/notedroid-plus-plus") }
        item(getString(R.string.help_debug_info)) { showDebugInfo() }
    }

    private fun showAbout() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        AlertDialog.Builder(this)
            .setTitle(R.string.help_about)
            .setMessage(getString(R.string.about_message, version))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun showDebugInfo() {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        val s = activeSession()
        syncActiveDocument()
        val info = buildString {
            appendLine("App: NoteDroid++ $version")
            appendLine("Package: $packageName")
            appendLine("Android SDK: ${android.os.Build.VERSION.SDK_INT} (${android.os.Build.VERSION.RELEASE})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Theme: ${prefs.getString("theme_name", "Default")}")
            appendLine("Open tabs: ${tabs.size}")
            appendLine("Active file: ${s?.document?.fileName ?: "-"}")
            appendLine("Language: ${s?.document?.language?.displayName ?: "-"}")
            appendLine("Encoding: ${s?.document?.encoding ?: "-"}")
            appendLine("EOL: ${s?.document?.lineEnding?.replace("\n", "LF")?.replace("\r", "CR") ?: "-"}")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.help_debug_info)
            .setMessage(info)
            .setPositiveButton(R.string.help_debug_copy) { _, _ -> copyToClipboard(info) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Window menu (#53) ----

    private fun showWindowMenu() {
        popup {
            tabs.forEachIndexed { i, s ->
                val marker = when {
                    i == active -> "✓ "
                    s.document.modified -> "● "
                    else -> "    "
                }
                item(marker + s.document.fileName) { activateTab(i) }
            }
            menu.add(getString(R.string.window_sort_by)).isEnabled = false
            item(getString(R.string.sort_name_asc)) { sortTabs(WindowSort.Field.NAME, false) }
            item(getString(R.string.sort_name_desc)) { sortTabs(WindowSort.Field.NAME, true) }
            item(getString(R.string.sort_type_asc)) { sortTabs(WindowSort.Field.TYPE, false) }
            item(getString(R.string.sort_type_desc)) { sortTabs(WindowSort.Field.TYPE, true) }
            item(getString(R.string.sort_size_asc)) { sortTabs(WindowSort.Field.LENGTH, false) }
            item(getString(R.string.sort_size_desc)) { sortTabs(WindowSort.Field.LENGTH, true) }
            item(getString(R.string.sort_modified_asc)) { sortTabs(WindowSort.Field.MODIFIED, false) }
            item(getString(R.string.sort_modified_desc)) { sortTabs(WindowSort.Field.MODIFIED, true) }
        }
    }

    private fun sortTabs(field: WindowSort.Field, descending: Boolean) {
        if (tabs.size < 2) return
        val activeDoc = activeSession()
        val ordered = WindowSort.sorted(tabs, { s ->
            WindowSort.DocInfo(
                name = s.document.fileName,
                typeOrdinal = s.document.language.ordinal,
                length = s.document.text.length,
                modifiedAt = s.lastModifiedAt
            )
        }, field, descending)
        tabs.clear()
        tabs.addAll(ordered)
        rebuildTabLayout()
        val newActive = ordered.indexOf(activeDoc).coerceAtLeast(0)
        activateTab(newActive)
        refreshTabLabels()
    }

    /** Rebuilds the TabLayout from the sessions list, preserving per-tab views. */
    private fun rebuildTabLayout() {
        val layout = binding.tabLayout
        for (child in layout.touchables) child.setOnLongClickListener(null)
        layout.removeAllTabs()
        tabs.forEach { s ->
            val tab = layout.newTab()
            tab.setCustomView(R.layout.tab_item)
            s.tab = tab
            val label = tab.customView?.findViewById<android.widget.TextView>(R.id.tab_label)
            val closeBtn = tab.customView?.findViewById<android.widget.ImageView>(R.id.tab_close)
            label?.text = s.document.fileName
            closeBtn?.setOnClickListener {
                val idx = tabs.indexOfFirst { it.tab === tab }
                if (idx >= 0) closeTabAt(idx)
            }
            layout.addTab(tab)
        }
        for (child in layout.touchables) {
            child.setOnLongClickListener { showTabCloseMenu(); true }
        }
    }

    // ---- Macro menu (#45) ----

    private val macroRecorderActions = mutableListOf<MacroRecorder.Action>()
    private var macroRecording = false
    private val macroStore by lazy { MacroStore(this) }

    private fun recordAction(a: MacroRecorder.Action) {
        if (macroRecording) macroRecorderActions.add(a)
    }

    private fun showMacroMenu() {
        popup {
            if (macroRecording) {
                item(getString(R.string.macro_stop)) { stopMacroRecording(save = false) }
            } else {
                item(getString(R.string.macro_start)) {
                    macroRecorderActions.clear()
                    macroRecording = true
                    Toast.makeText(this@MainActivity, R.string.macro_start, Toast.LENGTH_SHORT).show()
                }
            }
            item(getString(R.string.macro_playback)) { playbackMacro(macroRecorderActions.toList()) }
            item(getString(R.string.macro_run_multiple)) { promptMacroRepeat() }
            item(getString(R.string.macro_save)) { stopMacroRecording(save = true) }
            val saved = macroStore.list()
            if (saved.isNotEmpty()) {
                menu.add(getString(R.string.menu_separator)).isEnabled = false
                saved.forEach { m ->
                    item("▶ ${m.name}") { playbackMacro(m.actions) }
                }
                saved.forEach { m ->
                    item(getString(R.string.macro_delete) + ": ${m.name}") {
                        macroStore.delete(m.name)
                        Toast.makeText(this@MainActivity, R.string.macro_delete, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun stopMacroRecording(save: Boolean) {
        macroRecording = false
        if (!save) return
        if (macroRecorderActions.isEmpty()) {
            Toast.makeText(this, R.string.macro_none, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply {
            hint = getString(R.string.macro_name_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.macro_save)
            .setView(input)
            .setPositiveButton(R.string.md_link_ok) { _, _ ->
                val name = input.text.toString().ifBlank { "macro ${System.currentTimeMillis()}" }
                macroStore.save(MacroStore.Macro(name, macroRecorderActions.toList()))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptMacroRepeat() {
        val input = EditText(this).apply {
            hint = getString(R.string.macro_repeat_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.macro_run_multiple)
            .setView(input)
            .setPositiveButton(R.string.md_link_ok) { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n == null || n < 1 || n > 1000) {
                    Toast.makeText(this, R.string.macro_invalid_count, Toast.LENGTH_SHORT).show()
                } else {
                    var applied = 0
                    repeat(n) { applied += playbackMacroInternal(macroRecorderActions.toList()) }
                    Toast.makeText(this, getString(R.string.macro_playing, applied), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun playbackMacro(actions: List<MacroRecorder.Action>) {
        if (actions.isEmpty()) {
            Toast.makeText(this, R.string.macro_none, Toast.LENGTH_SHORT).show()
            return
        }
        val applied = playbackMacroInternal(actions)
        Toast.makeText(this, getString(R.string.macro_playing, applied), Toast.LENGTH_SHORT).show()
    }

    /** Replays against the active tab's buffer as one undoable whole-text change. */
    private fun playbackMacroInternal(actions: List<MacroRecorder.Action>): Int {
        val s = activeSession() ?: return 0
        if (s.readOnly) return 0
        val buf = MacroRecorder.StringBuffer(s.document.text)
        val applied = MacroRecorder.replay(buf, actions)
        if (buf.text != s.document.text) setEditorText(buf.text)
        return applied
    }

    // ---- Monitoring (tail -f) (#48) ----

    private val monitorHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null

    /** The session whose monitoring toggle started the poll loop (not necessarily active). */
    private var monitoredSession: TabSession? = null

    private fun toggleMonitoring() {
        val s = activeSession() ?: return
        if (s.document.uri == null) {
            Toast.makeText(this, R.string.need_save_first, Toast.LENGTH_SHORT).show()
            return
        }
        s.monitoring = !s.monitoring
        if (s.monitoring) {
            Toast.makeText(this, R.string.monitoring_on, Toast.LENGTH_LONG).show()
            startMonitoring(s)
        } else {
            stopMonitoring()
        }
    }

    private fun startMonitoring(session: TabSession) {
        stopMonitoring()
        monitoredSession = session
        val intervalSec = prefs.getInt("monitoring_interval", 2).coerceIn(1, 60)
        val runnable = object : Runnable {
            override fun run() {
                val s = monitoredSession
                if (s != null && s.monitoring && s.document.uri != null && tabs.contains(s)) {
                    thread(name = "monitor-check") {
                        val uri = Uri.parse(s.document.uri!!)
                        val changed = runCatching {
                            files.load(uri).text != s.document.text
                        }.getOrDefault(false)
                        runOnUiThread {
                            if (isFinishing || isDestroyed) {
                                stopMonitoring()
                            } else {
                                if (changed && s.monitoring) {
                                    if (s.document.modified) {
                                        Toast.makeText(this@MainActivity, R.string.monitoring_changed, Toast.LENGTH_LONG).show()
                                    } else if (s === activeSession()) {
                                        reloadActive()
                                        Toast.makeText(this@MainActivity, R.string.monitoring_reloaded, Toast.LENGTH_SHORT).show()
                                    } else {
                                        // monitored but inactive: refresh the buffer in place
                                        runCatching {
                                            val loaded = files.load(uri)
                                            s.document = s.document.copy(
                                                text = loaded.text,
                                                encoding = loaded.charset.name(),
                                                lineEnding = loaded.lineEnding,
                                                hasBom = loaded.hadBom,
                                                modified = false
                                            )
                                            refreshTabLabels()
                                        }
                                    }
                                }
                                monitorRunnable?.let { monitorHandler.postDelayed(it, intervalSec * 1000L) }
                            }
                        }
                    }
                } else {
                    monitorHandler.postDelayed(this, intervalSec * 1000L)
                }
            }
        }
        monitorRunnable = runnable
        monitorHandler.postDelayed(runnable, intervalSec * 1000L)
    }

    private fun stopMonitoring() {
        monitorRunnable?.let { monitorHandler.removeCallbacks(it) }
        monitorRunnable = null
        monitoredSession = null
    }

    override fun onDestroy() {
        stopMonitoring()
        backupRunnable?.let { backupHandler.removeCallbacks(it) }
        backupRunnable = null
        super.onDestroy()
    }

    // ---- Summary (#41) ----

    private fun showSummary() {
        val s = activeSession() ?: return
        syncActiveDocument()
        val text = s.document.text
        val lines = text.count { it == '\n' } + if (text.isEmpty()) 0 else 1
        val words = text.split(Regex("\\s+")).count { it.isNotEmpty() }
        val msg = buildString {
            appendLine(s.document.fileName)
            if (s.document.uri != null) appendLine(getString(R.string.summary_path, s.document.uri!!))
            appendLine(getString(R.string.summary_bytes, text.toByteArray(Charsets.UTF_8).size))
            appendLine(getString(R.string.summary_chars, text.length))
            appendLine(getString(R.string.summary_words, words))
            append(getString(R.string.summary_line, lines))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.summary_title)
            .setMessage(msg)
            .setPositiveButton(R.string.hash_copy) { _, _ -> copyToClipboard(msg) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Auto-completion + matched pairs (#46) ----

    private var completionPopup: android.widget.PopupWindow? = null
    private val autoInsertPairs: Boolean get() = prefs.getBoolean("auto_insert_pairs", false)
    private val wordCompletionOn: Boolean get() = prefs.getBoolean("word_completion", false)
    private val functionCompletionOn: Boolean get() = prefs.getBoolean("function_completion", false)
    private val minCompletionChars: Int get() = prefs.getInt("word_min_chars", 3).coerceIn(1, 10)

    private fun attachCompletionPopup() {
        editor.setOnKeyListener { v, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_TAB -> {
                        if (completionPopup?.isShowing == true) {
                            acceptCompletion()
                            return@setOnKeyListener true
                        }
                    }
                    android.view.KeyEvent.KEYCODE_BACK -> {
                        if (completionPopup?.isShowing == true) {
                            dismissCompletion()
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            v.onKeyPreIme(keyCode, event)
        }
    }

    private fun dismissCompletion() {
        completionPopup?.dismiss()
        completionPopup = null
    }

    private fun acceptCompletion() {
        val list = (completionPopup?.contentView as? android.widget.ListView)?.adapter
            as? android.widget.ArrayAdapter<*> ?: return dismissCompletion()
        // selected item or first
        val lv = completionPopup?.contentView as? android.widget.ListView ?: return dismissCompletion()
        val picked = lv.selectedItemId.toInt()
        val item = (if (picked >= 0) lv.getItemAtPosition(picked) else lv.getItemAtPosition(0)) ?: return dismissCompletion()
        insertCompletion(item.toString())
    }

    private fun insertCompletion(word: String) {
        dismissCompletion()
        val text = editor.text?.toString() ?: return
        val pos = editor.selectionStart
        val prefix = AutoComplete.prefixEndingAt(text, pos)
        if (prefix.isEmpty()) return
        val rest = word.substring(prefix.length)
        if (rest.isNotEmpty()) editor.text?.insert(pos, rest)
    }

    /** Shows the completion popup anchored near the caret when a long-enough prefix matches. */
    private fun maybeShowCompletion() {
        if (!wordCompletionOn && !functionCompletionOn) return
        val text = editor.text?.toString() ?: return
        val pos = editor.selectionStart
        val prefix = AutoComplete.prefixEndingAt(text, pos)
        if (prefix.length < minCompletionChars) {
            dismissCompletion()
            return
        }
        val words = if (wordCompletionOn) AutoComplete.documentWords(text) else emptyList()
        val keywords = if (functionCompletionOn) SyntaxHighlighter.keywordsFor(language()) else emptyList()
        val cands = AutoComplete.candidates(prefix, words, keywords, minCompletionChars)
        if (cands.isEmpty()) {
            dismissCompletion()
            return
        }
        val lv = android.widget.ListView(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, cands)
            setOnItemClickListener { _, _, position, _ ->
                insertCompletion(cands[position])
            }
        }
        val pw = android.widget.PopupWindow(lv, editor.width / 2, 400, true)
        pw.isOutsideTouchable = true
        dismissCompletion()
        completionPopup = pw
        val gl = editor.layout ?: return
        val line = gl.getLineForOffset(pos)
        val y = (gl.getLineBottom(line) - editor.scrollY).coerceAtLeast(0)
        pw.showAtLocation(editor, android.view.Gravity.NO_GRAVITY, editor.left, editor.top + y)
    }

    // ---- Search extensions (#37) ----

    private fun countMatchesInDoc() {
        refreshFindOptions()
        val text = editor.text?.toString() ?: return
        val n = com.notedroidplusplus.app.search.countMatches(text, findOptions)
        Toast.makeText(this, getString(R.string.search_count_result, n), Toast.LENGTH_SHORT).show()
    }

    /** Find All in current or all opened docs; results dialog jumps to the hit. */
    private fun findAllInDocs(allTabs: Boolean) {
        refreshFindOptions()
        syncActiveDocument()
        val targets = if (allTabs) tabs.toList() else listOfNotNull(activeSession())
        val hits = mutableListOf<com.notedroidplusplus.app.search.SearchHit>()
        targets.forEach { s ->
            hits.addAll(com.notedroidplusplus.app.search.findAll(s.document.text, findOptions, s.document.fileName, s.document.uri))
        }
        if (hits.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_match, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = hits.map { "${it.file}:${it.line}:${it.column}  ${it.lineText}" }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.search_results_title, hits.size))
            .setItems(labels.toTypedArray()) { _, which ->
                val hit = hits[which]
                val idx = tabs.indexOfFirst { it.document.uri != null && it.document.uri == hit.uri }
                    .takeIf { it >= 0 } ?: tabs.indexOfFirst { it.document.fileName == hit.file }
                if (idx >= 0) {
                    activateTab(idx)
                    val offset = TextUtils.lineIndexToOffset(tabs[idx].document.text, (hit.line - 1).coerceAtLeast(0))
                    editor.setSelection((offset + hit.column - 1).coerceIn(0, tabs[idx].document.text.length))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Mark All: background-span every match in the active document. */
    private fun markAll() {
        refreshFindOptions()
        val text = editor.text?.toString() ?: return
        val hits = com.notedroidplusplus.app.search.findAll(text, findOptions)
        if (hits.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_match, Toast.LENGTH_SHORT).show()
            return
        }
        editor.clearMarks()
        hits.forEach { h ->
            val start = h.start.coerceIn(0, text.length)
            editor.addMark(start, (start + effectiveQueryLength(text, start)).coerceAtMost(text.length))
        }
        Toast.makeText(this, getString(R.string.search_marked, hits.size), Toast.LENGTH_SHORT).show()
    }

    private fun clearMarks() {
        editor.clearMarks()
        Toast.makeText(this, R.string.search_marks_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun effectiveQueryLength(text: String, start: Int): Int {
        val resolved = findOptions.resolved()
        return if (resolved.regex) {
            val re = if (resolved.matchCase) Regex(resolved.query)
            else Regex(resolved.query, RegexOption.IGNORE_CASE)
            runCatching { re.find(text, start)?.value?.length ?: 1 }.getOrDefault(1)
        } else resolved.query.length.coerceAtLeast(1)
    }

    // ---- Bookmarks (#38) ----

    private fun showBookmarksMenu() = popup {
        item(getString(R.string.bm_toggle)) { toggleBookmark() }
        item(getString(R.string.bm_next)) { gotoBookmark(forward = true) }
        item(getString(R.string.bm_prev)) { gotoBookmark(forward = false) }
        item(getString(R.string.bm_clear)) {
            activeSession()?.let { it.bookmarks = emptySet() }
            editor.bookmarks = emptySet()
        }
        item(getString(R.string.bm_invert)) {
            val s = activeSession() ?: return@item
            val lines = s.document.text.count { it == '\n' } + 1
            s.bookmarks = com.notedroidplusplus.app.core.Bookmarks.invert(s.bookmarks, lines)
            editor.bookmarks = s.bookmarks
        }
        item(getString(R.string.bm_copy)) { copyBookmarkedLines(cut = false) }
        item(getString(R.string.bm_cut)) { copyBookmarkedLines(cut = true) }
        item(getString(R.string.bm_paste)) { pasteToBookmarkedLines() }
    }

    private fun currentLineIndex(): Int {
        val text = editor.text?.toString() ?: ""
        return text.substring(0, editor.selectionStart.coerceIn(0, text.length)).count { it == '\n' }
    }

    private fun toggleBookmark() {
        val s = activeSession() ?: return
        val line = currentLineIndex()
        s.bookmarks = com.notedroidplusplus.app.core.Bookmarks.toggle(s.bookmarks, line)
        editor.bookmarks = s.bookmarks
    }

    private fun gotoBookmark(forward: Boolean) {
        val s = activeSession() ?: return
        val line = currentLineIndex()
        val target = if (forward) com.notedroidplusplus.app.core.Bookmarks.next(s.bookmarks, line)
        else com.notedroidplusplus.app.core.Bookmarks.prev(s.bookmarks, line)
        if (target < 0) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }
        val text = s.document.text
        val offset = TextUtils.lineIndexToOffset(text, target)
        editor.setSelection(offset.coerceIn(0, text.length))
    }

    private fun copyBookmarkedLines(cut: Boolean) {
        val s = activeSession() ?: return
        if (s.bookmarks.isEmpty()) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }
        if (isEditorLocked()) return
        syncActiveDocument()
        val eol = s.document.lineEnding
        val block = com.notedroidplusplus.app.core.Bookmarks.collectLines(s.document.text, eol, s.bookmarks)
        copyToClipboard(block)
        if (cut) {
            setEditorText(com.notedroidplusplus.app.core.Bookmarks.removeLines(s.document.text, eol, s.bookmarks))
            s.bookmarks = emptySet()
            editor.bookmarks = emptySet()
        }
    }

    private fun pasteToBookmarkedLines() {
        val s = activeSession() ?: return
        if (s.bookmarks.isEmpty()) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }
        if (isEditorLocked()) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()
        if (clip.isNullOrEmpty()) {
            Toast.makeText(this, R.string.no_bookmarks, Toast.LENGTH_SHORT).show()
            return
        }
        syncActiveDocument()
        val eol = s.document.lineEnding
        var text = s.document.text
        // replace each bookmarked line's content with the clip, in reverse to keep offsets stable
        s.bookmarks.sortedDescending().forEach { line ->
            val start = TextUtils.lineIndexToOffset(text, line)
            val lineText = com.notedroidplusplus.app.core.TextUtils.lineAt(text, line)
            val end = (start + lineText.length).coerceAtMost(text.length)
            text = text.substring(0, start) + clip + text.substring(end)
        }
        setEditorText(text)
    }

    // ---- Menus ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, R.string.menu_file)
        menu.add(0, 2, 0, R.string.menu_edit)
        menu.add(0, 3, 0, R.string.menu_search)
        menu.add(0, 4, 0, R.string.menu_view)
        menu.add(0, 5, 0, R.string.menu_encoding)
        menu.add(0, 6, 0, R.string.menu_languages)
        menu.add(0, 7, 0, R.string.menu_tools)
        menu.add(0, 8, 0, R.string.menu_plugins)
        menu.add(0, 9, 0, R.string.menu_settings)
        menu.add(0, 10, 0, R.string.menu_macro)
        menu.add(0, 11, 0, R.string.menu_window)
        menu.add(0, 12, 0, R.string.menu_help)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> showFileMenu()
            2 -> showEditMenu()
            3 -> showSearchMenu()
            4 -> showViewMenu()
            5 -> showEncodingMenu()
            6 -> showLanguagesMenu()
            7 -> showToolsMenu()
            8 -> showPluginsMenu()
            9 -> startActivity(Intent(this, SettingsActivity::class.java))
            10 -> showMacroMenu()
            11 -> showWindowMenu()
            12 -> showHelpMenu()
        }
        return true
    }

    private fun popup(listener: PopupMenu.() -> Unit): PopupMenu {
        val pm = PopupMenu(this, binding.toolbar)
        pm.listener()
        pm.show()
        return pm
    }

    private fun PopupMenu.item(label: String, action: () -> Unit) {
        menu.add(label).setOnMenuItemClickListener { action(); true }
    }

    private fun showFileMenu() = popup {
        item(getString(R.string.action_new)) { addTab(null, null) }
        item(getString(R.string.action_open)) { openLauncher.launch(arrayOf("text/*")) }
        item(getString(R.string.open_recent)) { showRecentMenu() }
        item(getString(R.string.action_save)) { saveActive() }
        item(getString(R.string.action_save_as)) { activeSession()?.let { saveAsWithEncoding(it) } }
        item(getString(R.string.action_save_all)) { saveAll() }
        item(getString(R.string.action_reload)) { reloadActive() }
        item(getString(R.string.action_print)) { printActive() }
        item(getString(R.string.action_readonly)) { toggleReadOnly() }
        item(getString(R.string.action_close)) { closeActive() }
        item(getString(R.string.action_exit)) { finish() }
    }

    private fun showEditMenu() = popup {
        item(getString(R.string.action_undo)) { performUndo() }
        item(getString(R.string.action_redo)) { performRedo() }
        item(getString(R.string.action_cut)) {
            if (isEditorLocked()) return@item
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), getSelection()))
            editor.text?.delete(editor.selectionStart, editor.selectionEnd)
        }
        item(getString(R.string.action_copy)) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), getSelection()))
        }
        item(getString(R.string.action_paste)) {
            if (isEditorLocked()) return@item
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            editor.text?.insert(editor.selectionStart.coerceAtMost(editor.selectionEnd), clip)
        }
        item(getString(R.string.action_select_all)) { editor.selectAll() }
        item(getString(R.string.menu_line_ops)) { showLineOpsMenu() }
        item(getString(R.string.menu_convert_case)) { showConvertCaseMenu() }
        item(getString(R.string.menu_blank_ops)) { showBlankOpsMenu() }
        item(getString(R.string.menu_insert)) { showInsertMenu() }
        item(getString(R.string.menu_copy_clipboard)) { showCopyClipboardMenu() }
        item(getString(R.string.menu_indent)) { showIndentMenu() }
    }

    private fun showLineOpsMenu() = popup {
        val eol = activeSession()?.document?.lineEnding ?: "\n"
        item(getString(R.string.op_duplicate_line)) { applyLineOp(eol) { t, f, l -> TextUtils.duplicateLines(t, f, l, eol) } }
        item(getString(R.string.op_split_lines)) { applySelectionOp { t, s, e -> TextUtils.splitLines(t, s, e, eol) } }
        item(getString(R.string.op_join_lines)) {
            val s = editor.selectionStart.coerceAtMost(editor.selectionEnd)
            val e = editor.selectionEnd.coerceAtLeast(editor.selectionStart)
            if (s == e) {
                Toast.makeText(this@MainActivity, R.string.join_needs_multiline, Toast.LENGTH_SHORT).show()
            } else applySelectionOp { t, ss, ee -> TextUtils.joinLines(t, ss, ee) }
        }
        item(getString(R.string.op_remove_empty)) { applyLineOp(eol) { t, f, l -> TextUtils.removeEmptyLines(t, f, l, eol, includingBlank = false) } }
        item(getString(R.string.op_remove_blank)) { applyLineOp(eol) { t, f, l -> TextUtils.removeEmptyLines(t, f, l, eol, includingBlank = true) } }
        item(getString(R.string.op_move_up)) { moveActiveLine(eol, up = true) }
        item(getString(R.string.op_move_down)) { moveActiveLine(eol, up = false) }
        item(getString(R.string.op_reverse)) { applyLineOp(eol) { t, f, l -> TextUtils.reverseLines(t, f, l, eol) } }
        item(getString(R.string.op_randomize)) { applyLineOp(eol) { t, f, l -> TextUtils.randomizeLines(t, f, l, eol, java.util.Random()) } }
        item(getString(R.string.op_sort_asc)) { applyLineOp(eol) { t, f, l -> TextUtils.sortLines(t, f, l, eol, false, numeric = false, ignoreCase = true) } }
        item(getString(R.string.op_sort_desc)) { applyLineOp(eol) { t, f, l -> TextUtils.sortLines(t, f, l, eol, true, numeric = false, ignoreCase = true) } }
        item(getString(R.string.op_sort_asc_lexicographic)) { applyLineOp(eol) { t, f, l -> TextUtils.sortLines(t, f, l, eol, false, numeric = false, ignoreCase = false) } }
        item(getString(R.string.op_sort_desc_lexicographic)) { applyLineOp(eol) { t, f, l -> TextUtils.sortLines(t, f, l, eol, true, numeric = false, ignoreCase = false) } }
        item(getString(R.string.op_sort_asc_numeric)) { applyLineOp(eol) { t, f, l -> TextUtils.sortLines(t, f, l, eol, false, numeric = true, ignoreCase = false) } }
        item(getString(R.string.op_sort_desc_numeric)) { applyLineOp(eol) { t, f, l -> TextUtils.sortLines(t, f, l, eol, true, numeric = true, ignoreCase = false) } }
        item(getString(R.string.op_sort_asc_ci)) { applyLineOp(eol) { t, f, l -> TextUtils.sortLines(t, f, l, eol, false, numeric = false, ignoreCase = true) } }
        item(getString(R.string.op_sort_desc_ci)) { applyLineOp(eol) { t, f, l -> TextUtils.sortLines(t, f, l, eol, true, numeric = false, ignoreCase = true) } }
        item(getString(R.string.op_remove_dup)) { applyLineOp(eol) { t, f, l -> TextUtils.removeDuplicateLines(t, f, l, eol, consecutiveOnly = false) } }
        item(getString(R.string.op_remove_consecutive_dup)) { applyLineOp(eol) { t, f, l -> TextUtils.removeDuplicateLines(t, f, l, eol, consecutiveOnly = true) } }
    }

    private fun showConvertCaseMenu() = popup {
        item(getString(R.string.action_uppercase)) { transformCase("upper") { t, s, e -> TextUtils.toUpper(t, s, e) } }
        item(getString(R.string.action_lowercase)) { transformCase("lower") { t, s, e -> TextUtils.toLower(t, s, e) } }
        item(getString(R.string.action_titlecase)) { transformCase("title") { t, s, e -> TextUtils.titleCase(t, s, e) } }
        item(getString(R.string.op_proper)) { transformCase("proper") { t, s, e -> TextUtils.properCase(t, s, e, blend = false) } }
        item(getString(R.string.op_proper_blend)) { transformCase("proper-blend") { t, s, e -> TextUtils.properCase(t, s, e, blend = true) } }
        item(getString(R.string.op_sentence)) { transformCase("sentence") { t, s, e -> TextUtils.sentenceCase(t, s, e, blend = false) } }
        item(getString(R.string.op_sentence_blend)) { transformCase("sentence-blend") { t, s, e -> TextUtils.sentenceCase(t, s, e, blend = true) } }
        item(getString(R.string.action_toggle_case)) { transformCase("toggle") { t, s, e -> TextUtils.toggleCase(t, s, e) } }
        item(getString(R.string.op_invert)) { transformCase("toggle") { t, s, e -> TextUtils.invertCase(t, s, e) } }
        item(getString(R.string.op_random_case)) { transformCase(null) { t, s, e -> TextUtils.randomCase(t, s, e, java.util.Random()) } }
    }

    private fun showBlankOpsMenu() = popup {
        val eol = activeSession()?.document?.lineEnding ?: "\n"
        item(getString(R.string.action_trim_trailing)) { setEditorText(TextUtils.trimTrailingWhitespace(editor.text?.toString() ?: "")) }
        item(getString(R.string.op_trim_leading)) { setEditorText(TextUtils.trimLeadingWhitespace(editor.text?.toString() ?: "", eol)) }
        item(getString(R.string.op_trim_leading_trailing)) { setEditorText(TextUtils.trimLeadingAndTrailing(editor.text?.toString() ?: "", eol)) }
        item(getString(R.string.op_eol_space)) { setEditorText(TextUtils.eolToSpace(editor.text?.toString() ?: "")) }
        item(getString(R.string.op_tab_space)) { setEditorText(TextUtils.tabToSpace(editor.text?.toString() ?: "", eol, 4)) }
        item(getString(R.string.op_space_tab)) { setEditorText(TextUtils.spaceToTab(editor.text?.toString() ?: "", eol, 4, all = true)) }
        item(getString(R.string.op_space_tab_leading)) { setEditorText(TextUtils.spaceToTab(editor.text?.toString() ?: "", eol, 4, all = false)) }
    }

    private fun showInsertMenu() = popup {
        val now = System.currentTimeMillis()
        item(getString(R.string.op_date_short)) { insertAtCaret(TextUtils.formatDateTime(TextUtils.DATETIME_SHORT, now)) }
        item(getString(R.string.op_date_long)) { insertAtCaret(TextUtils.formatDateTime(TextUtils.DATETIME_LONG, now)) }
        item(getString(R.string.op_date_custom)) { promptCustomDate(now) }
    }

    private fun showCopyClipboardMenu() = popup {
        val s = activeSession()
        item(getString(R.string.copy_path_filename)) { copyToClipboard(s?.document?.fileName ?: "") }
        item(getString(R.string.copy_path_full)) { copyToClipboard(s?.document?.uri ?: "") }
        item(getString(R.string.copy_path_dir)) {
            val dir = s?.document?.uri
                ?.let { Uri.parse(it).lastPathSegment?.substringBeforeLast('/') }
            copyToClipboard(dir ?: "")
        }
    }

    private fun showIndentMenu() = popup {
        val eol = activeSession()?.document?.lineEnding ?: "\n"
        item(getString(R.string.indent_increase)) { applyLineOp(eol) { t, f, l -> TextUtils.indentIncrease(t, f, l, eol, "\t") } }
        item(getString(R.string.indent_decrease)) { applyLineOp(eol) { t, f, l -> TextUtils.indentDecrease(t, f, l, eol, "\t") } }
    }


    private fun showSearchMenu() = popup {
        item(getString(R.string.action_find)) { toggleFindPanel(false) }
        item(getString(R.string.action_replace)) { toggleFindPanel(true) }
        item(getString(R.string.search_count)) { countMatchesInDoc() }
        item(getString(R.string.search_find_all_current)) { findAllInDocs(allTabs = false) }
        item(getString(R.string.search_find_all_opened)) { findAllInDocs(allTabs = true) }
        item(getString(R.string.search_mark_all)) { markAll() }
        item(getString(R.string.search_clear_marks)) { clearMarks() }
        item(getString(R.string.menu_bookmarks)) { showBookmarksMenu() }
        item(getString(R.string.action_goto)) { gotoLine() }
        item(getString(R.string.action_find_in_files)) {
            refreshFindOptions()
            syncActiveDocument()
            val buffers = arrayListOf<String>()
            tabs.filter { it.document.uri != null }.forEach {
                buffers.add(it.document.uri!!)
                buffers.add(it.document.text)
            }
            fifLauncher.launch(
                Intent(this@MainActivity, FindInFilesActivity::class.java)
                    .putExtra(FindInFilesActivity.EXTRA_QUERY, binding.findInput.text.toString())
                    .putStringArrayListExtra(FindInFilesActivity.EXTRA_OPEN_BUFFERS, buffers)
            )
        }
    }

    private val recentMax: Int get() = RecentFilesSettings.parseMax(prefs.getString("recent_max", null))
    private val recentMode: RecentFilesSettings.DisplayMode
        get() = if (prefs.getString("recent_display", "name") == "path") RecentFilesSettings.DisplayMode.FULL_PATH
        else RecentFilesSettings.DisplayMode.NAME_ONLY

    private fun showRecentMenu() {
        val recents = RecentFilesSettings.trim(recentFiles.list(), recentMax)
        if (recents.isEmpty()) {
            Toast.makeText(this, R.string.no_recent_files, Toast.LENGTH_SHORT).show()
            return
        }
        popup {
            recents.forEach { entry ->
                item(RecentFilesSettings.label(entry, recentMode)) { openRecent(entry.uri) }
            }
            item(getString(R.string.recent_clear)) {
                recentFiles.clear()
                Toast.makeText(this@MainActivity, R.string.recent_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openRecent(uriString: String) {
        val uri = Uri.parse(uriString)
        try {
            val loaded = files.load(uri)
            val fileName = files.displayName(uri)
            loadIntoTab(uri, loaded, fileName, ContentSniffer.detectWithFileName(fileName, loaded.text))
            refreshTabLabels()
            updateStatus()
        } catch (e: Exception) {
            recentFiles.remove(uriString)
            Toast.makeText(this, getString(R.string.recent_file_gone, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun showViewMenu() = popup {
        item(getString(R.string.theme_picker)) { showThemeMenu() }
        item(getString(R.string.wrap_lines)) { setWrap(true) }
        item(getString(R.string.no_wrap_lines)) { setWrap(false) }
        item(getString(R.string.show_line_numbers)) { editor.showLineNumbers = !editor.showLineNumbers }
        item(getString(R.string.action_zoom_in)) { zoom(1.25f) }
        item(getString(R.string.action_zoom_out)) { zoom(0.8f) }
        item(getString(R.string.action_restore_zoom)) { restoreDefaultZoom() }
        item(getString(R.string.action_view_external)) { viewCurrentFileExternally() }
        item(getString(R.string.action_monitoring)) { toggleMonitoring() }
        item(getString(R.string.action_summary)) { showSummary() }
        item(getString(R.string.menu_show_symbol)) { showSymbolMenu() }
        item(getString(R.string.action_fullscreen)) { toggleFullScreen(distractionFree = false) }
        item(getString(R.string.action_distraction_free)) { toggleFullScreen(distractionFree = true) }
        item(getString(R.string.action_rtl)) { setTextDirection(android.view.View.LAYOUT_DIRECTION_RTL) }
        item(getString(R.string.action_ltr)) { setTextDirection(android.view.View.LAYOUT_DIRECTION_LTR) }
    }

    private fun showSymbolMenu() {
        popup {
            fun toggled(key: String): Boolean = prefs.getBoolean(key, false)
            fun flip(key: String, default: Boolean = false) {
                prefs.edit().putBoolean(key, !toggled(key)).apply()
                applyShowSymbol()
            }
            item(check(getString(R.string.symbol_space_tab), toggled("symbol_space_tab"))) { flip("symbol_space_tab") }
            item(check(getString(R.string.symbol_eol), toggled("symbol_eol"))) { flip("symbol_eol") }
            item(check(getString(R.string.symbol_non_printing), toggled("symbol_non_printing"))) { flip("symbol_non_printing") }
            item(check(getString(R.string.symbol_control), toggled("symbol_control"))) { flip("symbol_control") }
            item(check(getString(R.string.symbol_indent_guide), toggled("symbol_indent_guide"))) { flip("symbol_indent_guide") }
            item(check(getString(R.string.symbol_wrap), toggled("symbol_wrap"))) { flip("symbol_wrap") }
            item(check(getString(R.string.symbol_all), toggled("symbol_all"))) { flip("symbol_all") }
        }
    }

    private fun check(label: String, on: Boolean) = if (on) "✓ $label" else "    $label"

    private fun applyShowSymbol() {
        editor.showSymbolFlags = ShowSymbolFlags.fromPrefs(
            prefs.getBoolean("symbol_space_tab", false),
            prefs.getBoolean("symbol_eol", false),
            prefs.getBoolean("symbol_non_printing", false),
            prefs.getBoolean("symbol_control", false),
            prefs.getBoolean("symbol_indent_guide", false),
            prefs.getBoolean("symbol_wrap", false),
            prefs.getBoolean("symbol_all", false)
        )
    }

    // ---- Full-screen / RTL (#41) ----

    private var fullScreen = false

    private fun toggleFullScreen(distractionFree: Boolean) {
        fullScreen = !fullScreen
        val window = window
        val decor = window.decorView
        if (fullScreen) {
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            binding.appBar.visibility = View.GONE
            if (distractionFree) {
                binding.statusBar.visibility = View.GONE
                binding.tabLayout.visibility = View.GONE
            }
            val exit = android.widget.Button(this).apply {
                text = getString(R.string.action_exit_fs)
                setOnClickListener { toggleFullScreen(distractionFree) }
            }
            (binding.root as? android.view.ViewGroup)?.addView(exit, androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL })
            exit.tag = "fs_exit"
        } else {
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            binding.appBar.visibility = View.VISIBLE
            binding.statusBar.visibility = View.VISIBLE
            binding.tabLayout.visibility = View.VISIBLE
            (binding.root as? android.view.ViewGroup)?.findViewWithTag<android.widget.Button>("fs_exit")?.let {
                (binding.root as android.view.ViewGroup).removeView(it)
            }
        }
    }

    private fun setTextDirection(direction: Int) {
        val s = activeSession() ?: return
        s.textDirectionRtl = direction == android.view.View.LAYOUT_DIRECTION_RTL
        editor.textDirection = if (s.textDirectionRtl) android.view.View.TEXT_DIRECTION_ANY_RTL
        else android.view.View.TEXT_DIRECTION_LTR
        editor.layoutDirection = direction
    }

    private fun restoreDefaultZoom() {
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        prefs.edit().putInt("font_size", 14).apply()
        editor.updateGutterPublic()
        editor.rehighlight()
    }

    // ---- External app view + containing folder (#49) ----

    private fun viewCurrentFileExternally() {
        val s = activeSession()
        val uriString = s?.document?.uri
        if (uriString == null) {
            Toast.makeText(this, R.string.need_save_first, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                setDataAndType(Uri.parse(uriString), mimeFor(s.document.fileName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_external_app, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.error_load_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun openContainingFolder(s: TabSession) {
        val uriString = s.document.uri
        if (uriString == null) {
            Toast.makeText(this, R.string.need_save_first, Toast.LENGTH_SHORT).show()
            return
        }
        val docUri = Uri.parse(uriString)
        val parent = DocumentFile.fromSingleUri(this, docUri)?.parentFile
        if (parent == null) {
            // Derive the tree/document parent URI for providers exposing a hierarchy.
            val treeUri = runCatching {
                Uri.parse(docUri.toString().substringBeforeLast('/'))
            }.getOrNull()
            if (treeUri == null) {
                Toast.makeText(this, R.string.error_no_folder_view, Toast.LENGTH_LONG).show()
                return
            }
            try {
                startActivity(Intent(Intent.ACTION_VIEW, treeUri).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (e: android.content.ActivityNotFoundException) {
                Toast.makeText(this, R.string.error_no_folder_view, Toast.LENGTH_LONG).show()
            }
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, parent.uri).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_folder_view, Toast.LENGTH_LONG).show()
        }
    }

    private fun showThemeMenu() {
        popup {
            ThemeRegistry.all.forEach { theme ->
                item(theme.name) {
                    applyThemeName(theme.name)
                }
            }
        }
    }

    private fun applyThemeName(name: String) {
        prefs.edit().putString("theme_name", name).apply()
        applyTheme(ThemeRegistry.byName(name))
    }

    private fun setWrap(wrap: Boolean) {
        prefs.edit().putBoolean("wrap_lines", wrap).apply()
        editor.setHorizontallyScrolling(!wrap)
    }

    private fun applyTheme(theme: com.notedroidplusplus.app.settings.EditorTheme) {
        binding.root.setBackgroundColor(theme.appBg)
        binding.toolbar.setBackgroundColor(theme.toolbarBg)
        binding.toolbar.setTitleTextColor(0xFFFFFFFF.toInt())
        binding.tabLayout.setBackgroundColor(theme.tabBg)
        binding.tabLayout.setTabTextColors(theme.tabText, theme.tabSelectedText)
        binding.tabLayout.setSelectedTabIndicatorColor(theme.tabIndicator)
        binding.statusBar.setBackgroundColor(theme.statusBg)
        val statusViews = listOf(binding.statusPosition, binding.statusSelection, binding.statusEncoding, binding.statusEol, binding.statusLanguage)
        statusViews.forEach { it.setTextColor(theme.statusText) }
        editor.applyEditorTheme(theme)
    }

    private fun showEncodingMenu() = popup {
        TextUtils.names().forEach { name -> item(name) { setEncoding(name) } }
        item(getString(R.string.convert_to_title)) { showConvertToMenu() }
        item(getString(R.string.charsets_title)) { showCharsetsMenu() }
        menu.add(getString(R.string.eol_title)).isEnabled = false
        item(getString(R.string.windows_format)) { setEol("\r\n") }
        item(getString(R.string.unix_format)) { setEol("\n") }
        item(getString(R.string.mac_format)) { setEol("\r") }
    }

    private fun showConvertToMenu() = popup {
        val all = TextUtils.allCharsets()
        all.forEach { cs -> item(cs.name()) { convertToEncoding(cs.name()) } }
    }

    private fun showCharsetsMenu() {
        popup {
            TextUtils.CHARSET_FAMILIES.forEach { (family, _) ->
                item(family) { showCharsetFamilyMenu(family) }
            }
        }
    }

    private fun showCharsetFamilyMenu(family: String) {
        val names = TextUtils.CHARSET_FAMILIES.firstOrNull { it.first == family }?.second ?: return
        popup {
            names.forEach { name -> item(name) { setEncoding(name) } }
        }
    }

    private fun showLanguagesMenu() = popup {
        LanguageMode.entries.forEach { m -> item(m.displayName) { setLanguage(m) } }
    }

    private fun showToolsMenu() = popup {
        item(getString(R.string.action_prettify)) { prettify() }
        item(getString(R.string.action_minify)) { minify() }
        item(getString(R.string.action_validate_xml)) { validateXml() }
        item(getString(R.string.action_preview)) { preview() }
        item(getString(R.string.action_character_panel)) { showCharacterPanel() }
        menu.add(getString(R.string.menu_hashes)).isEnabled = false
        HashTool.Algorithm.entries.forEach { alg ->
            item(alg.display) { showHashMenu(alg) }
        }
    }

    private fun showHashMenu(alg: HashTool.Algorithm) {
        popup {
            item(getString(R.string.hash_generate)) { promptHashText(alg) }
            item(getString(R.string.hash_from_files)) {
            pendingHashAlgorithm = alg
            hashFilesLauncher.launch(arrayOf("*/*"))
        }
            item(getString(R.string.hash_from_selection)) {
                val sel = getSelection()
                if (sel.isEmpty()) {
                    Toast.makeText(this@MainActivity, R.string.hash_no_selection, Toast.LENGTH_SHORT).show()
                } else {
                    copyToClipboard(HashTool.hashText(alg, sel))
                    Toast.makeText(this@MainActivity, R.string.hash_copied, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun promptHashText(alg: HashTool.Algorithm) {
        val input = EditText(this).apply {
            hint = getString(R.string.hash_input_hint)
            minLines = 2
            gravity = android.view.Gravity.TOP
        }
        AlertDialog.Builder(this)
            .setTitle(alg.display)
            .setView(input)
            .setPositiveButton(R.string.hash_copy) { _, _ ->
                val digest = HashTool.hashText(alg, input.text.toString())
                copyToClipboard(digest)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private val hashFilesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            val alg = pendingHashAlgorithm ?: return@registerForActivityResult
            thread(name = "hash-files") {
                val lines = uris.map { uri ->
                    val name = runCatching { files.displayName(uri) }.getOrDefault(uri.lastPathSegment ?: "?")
                    val digest = runCatching {
                        contentResolver.openInputStream(uri)?.use { HashTool.hashStream(alg, it) }
                    }.getOrNull()
                    if (digest == null) "$name: ${getString(R.string.error_load_failed, "?")}"
                    else "$name: $digest"
                }
                runOnUiThread {
                    pendingHashAlgorithm = null
                    AlertDialog.Builder(this)
                        .setTitle(alg.display)
                        .setMessage(lines.joinToString("\n"))
                        .setPositiveButton(R.string.hash_copy) { _, _ ->
                            copyToClipboard(lines.joinToString("\n"))
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }

    private var pendingHashAlgorithm: HashTool.Algorithm? = null

    // ---- Character Panel (#57) ----

    private fun showCharacterPanel() {
        val s = activeSession()
        val charset = runCatching { Charset.forName(s?.document?.encoding ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        val rows = CharacterTable.rows(charset)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        val unicodeInput = EditText(this).apply {
            hint = getString(R.string.char_unicode_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }
        layout.addView(android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            addView(unicodeInput, android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(android.widget.Button(this@MainActivity).apply {
                text = getString(R.string.char_unicode_insert)
                setOnClickListener {
                    val cp = CharacterTable.parseCodepoint(unicodeInput.text.toString())
                    if (cp == null) {
                        Toast.makeText(this@MainActivity, R.string.char_unicode_invalid, Toast.LENGTH_SHORT).show()
                    } else {
                        insertAtCaret(CharacterTable.codepointToChar(cp))
                        Toast.makeText(this@MainActivity, getString(R.string.char_inserted, CharacterTable.codepointToChar(cp)), Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val grid = android.widget.GridView(this).apply {
            numColumns = 4
            adapter = object : android.widget.BaseAdapter() {
                override fun getCount() = rows.size * 3
                override fun getItem(position: Int) = position
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val tv = (convertView as? android.widget.TextView) ?: android.widget.TextView(this@MainActivity).apply {
                        gravity = android.view.Gravity.CENTER
                        setPadding(8, 8, 8, 8)
                        textSize = 13f
                    }
                    val row = rows[position / 3]
                    when (position % 3) {
                        0 -> tv.text = "${row.value} ${row.hex} ${row.char}"
                        1 -> tv.text = row.htmlName ?: row.htmlDecimal
                        2 -> tv.text = row.htmlHex
                    }
                    return tv
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                val row = rows[position / 3]
                val insert = when (position % 3) {
                    0 -> row.char
                    1 -> row.htmlName ?: row.htmlDecimal
                    else -> row.htmlHex
                }
                insertAtCaret(insert)
                Toast.makeText(this@MainActivity, getString(R.string.char_inserted, insert), Toast.LENGTH_SHORT).show()
            }
        }
        layout.addView(grid, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.char_panel_title, charset.name()))
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun validateXml() {
        val s = activeSession() ?: return
        if (s.document.language != LanguageMode.XML) {
            Toast.makeText(this, R.string.tool_json_xml_only, Toast.LENGTH_SHORT).show()
            return
        }
        XmlTool.validate(editor.text?.toString() ?: "")
            .onSuccess { Toast.makeText(this, R.string.xml_valid, Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, getString(R.string.xml_invalid, it.message), Toast.LENGTH_LONG).show() }
    }

    private fun showPluginsMenu() = popup {
        item(getString(R.string.plugin_import)) { PluginImportActivity.start(this@MainActivity) }
        menu.add(getString(R.string.plugin_builtins)).isEnabled = false
        pluginRegistry.discover().forEachIndexed { pi, plugin ->
            menu.add(plugin.name).isEnabled = false
            plugin.commands.forEachIndexed { ci, cmd ->
                item("${plugin.name}: ${cmd.label}") { runPluginCommand(pi, ci) }
            }
        }
    }

    private fun zoom(factor: Float) {
        val size = editor.textSize / resources.displayMetrics.scaledDensity
        val newSize = (size * factor).coerceIn(8f, 40f)
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, newSize)
        prefs.edit().putInt("font_size", newSize.toInt()).apply()
        editor.updateGutterPublic()
        editor.rehighlight()
    }
}
