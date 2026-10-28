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
import com.google.android.material.tabs.TabLayout
import com.notedroidplusplus.app.core.Document
import com.notedroidplusplus.app.core.FileManager
import com.notedroidplusplus.app.core.RecentFiles
import com.notedroidplusplus.app.core.SessionStore
import com.notedroidplusplus.app.core.TextUtils
import com.notedroidplusplus.app.databinding.ActivityMainBinding
import com.notedroidplusplus.app.editing.CodeEditText
import com.notedroidplusplus.app.editing.ContentSniffer
import com.notedroidplusplus.app.editing.MarkdownActions
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
import com.notedroidplusplus.app.tools.JsonTool
import com.notedroidplusplus.app.tools.XmlTool
import java.nio.charset.Charset

class MainActivity : AppCompatActivity(), PluginHost {

    companion object {
        private const val KEY_TABS = "tabs_state"
        private const val KEY_ACTIVE = "active_tab"
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
        var readOnly: Boolean = false
    )

    private val tabs = mutableListOf<TabSession>()
    private var active = -1
    private var newCounter = 1
    private var findOptions = FindOptions()
    private var findPanelOpen = false
    private val saveAsQueue = mutableListOf<TabSession>()
    private val previewModeMemo = PreviewModeMemo()

    /** Per-flow Save As state: one entry per in-flight flow, never shared between flows. */
    private data class SaveAsFlow(
        val session: TabSession?,
        val encoding: String?,
        val closeIndex: Int = -1
    )

    private val saveAsFlows = mutableListOf<SaveAsFlow>()
    private var activeSaveAsFlow: SaveAsFlow? = null

    private fun beginSaveAsFlow(flow: SaveAsFlow) {
        activeSaveAsFlow?.let { saveAsFlows.add(it) }
        activeSaveAsFlow = flow
    }

    private fun endActiveSaveAsFlow() {
        activeSaveAsFlow = null
    }

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
            val savedUri = uri
            val flow = activeSaveAsFlow
            val encodingTarget = flow?.session
            if (savedUri != null && encodingTarget != null) {
                flow.encoding?.let { enc -> encodingTarget.document = encodingTarget.document.copy(encoding = enc) }
            }
            if (savedUri == null) {
                if (flow != null && flow.closeIndex >= 0) {
                    // pending close dropped: the tab stays open
                }
                endActiveSaveAsFlow()
                saveAsQueue.clear()
                return@registerForActivityResult
            }
            val index = flow?.closeIndex ?: -1
            val session = when {
                index >= 0 -> tabs.getOrNull(index)
                encodingTarget != null && tabs.contains(encodingTarget) -> encodingTarget
                else -> activeSession()
            }
            if (session != null) {
                val saved = saveSessionTo(session, savedUri)
                if (index >= 0) {
                    if (saved) removeTab(index)
                }
            }
            endActiveSaveAsFlow()
            launchNextQueuedSaveAs()
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
        binding.cbWrapAround.isChecked = true

        binding.findInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                findNextMatch(down = true)
                true
            } else false
        }

        editor.addTextChangedListener(tw)
        editor.onSelectionChange = { updateStatus() }

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
                .setNegativeButton(R.string.no) { _, _ -> { sessionStore.clear(); addTab(null, null) } }
                .setCancelable(false)
                .show()
        } else {
            addTab(null, null)
        }
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
        }
        activateTab(0)
        refreshTabLabels()
    }

    override fun onPause() {
        super.onPause()
        saveSessionSnapshot()
    }

    override fun onStop() {
        super.onStop()
        saveSessionSnapshot()
    }

    private fun saveSessionSnapshot() {
        if (tabs.isEmpty()) return
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
                s.caret
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
        val states = tabs.map { arrayOf(it.document.uri, it.document.fileName, it.document.text, it.document.language.name, it.document.encoding, it.document.lineEnding, it.document.modified, it.caret, it.readOnly) }
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
        }
        val act = state.getInt(KEY_ACTIVE, 0).coerceIn(0, tabs.size - 1)
        activateTab(act)
        activeSession()?.let { editor.setSelection(it.caret.coerceIn(0, it.document.text.length)) }
        refreshTabLabels()
    }

    private fun applyPersistedSettings() {
        applyTheme(ThemeRegistry.byName(prefs.getString("theme_name", "Default") ?: "Default"))
        editor.showLineNumbers = prefs.getBoolean("show_line_numbers", true)
        editor.setHorizontallyScrolling(!prefs.getBoolean("wrap_lines", false))
        val fontSize = prefs.getInt("font_size", 14)
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat().coerceIn(8f, 40f))
    }

    override fun onResume() {
        super.onResume()
        applyTheme(ThemeRegistry.byName(prefs.getString("theme_name", "Default") ?: "Default"))
        editor.showLineNumbers = prefs.getBoolean("show_line_numbers", true)
        editor.setHorizontallyScrolling(!prefs.getBoolean("wrap_lines", false))
        val fontSize = prefs.getInt("font_size", 14)
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat().coerceIn(8f, 40f))
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
                removed.isNotEmpty() && inserted.isEmpty() -> sess.history.pushDelete(start, removed)
                inserted.isNotEmpty() && removed.isEmpty() -> sess.history.pushInsert(start, inserted)
                removed.isNotEmpty() && inserted.isNotEmpty() -> sess.history.pushCompound(
                    buildList {
                        add(com.notedroidplusplus.app.core.UndoHistory.Edit.Delete(start, removed))
                        add(com.notedroidplusplus.app.core.UndoHistory.Edit.Insert(start, inserted))
                    }
                )
            }
            pendingRemoved = null
            pendingStart = -1
        }
        override fun afterTextChanged(s: android.text.Editable?) {
            onTextChanged()
        }
    }

    // ---- Tabs ----

    private fun activeSession(): TabSession? = tabs.getOrNull(active)

    private fun addTab(uri: Uri?, initialText: String?, name: String? = null): TabSession {
        val fileName = name ?: uri?.let { files.displayName(it) } ?: getString(R.string.tab_new, newCounter++)
        val doc = Document(
            uri = uri?.toString(),
            fileName = fileName,
            text = initialText ?: "",
            language = if (uri != null || name != null) LanguageMode.fromFileName(fileName) else LanguageMode.NORMAL_TEXT
        )
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
            recentFiles.add(uri.toString(), fileName)
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
        applyReadOnly(s)
        refreshTabLabels()
        updateStatus()
    }

    private fun applyReadOnly(s: TabSession) {
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
        val encodings = TextUtils.names()
        AlertDialog.Builder(this)
            .setTitle(R.string.encoding_title)
            .setItems(encodings.toTypedArray()) { _, which ->
                beginSaveAsFlow(SaveAsFlow(session = s, encoding = encodings[which]))
                saveAsLauncher.launch(mimeFor(s.document.fileName) to s.document.fileName)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                endActiveSaveAsFlow()
                saveAsQueue.clear()
                launchNextQueuedSaveAs()
            }
            .setOnCancelListener {
                endActiveSaveAsFlow()
                saveAsQueue.clear()
            }
            .show()
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
                    beginSaveAsFlow(SaveAsFlow(session = s, encoding = null, closeIndex = index))
                    saveAsLauncher.launch(mimeFor(s.document.fileName) to s.document.fileName)
                }
            }
            .setNegativeButton(R.string.no) { _, _ -> removeTab(index) }
            .setNeutralButton(R.string.cancel) { _, _ -> activateTab(prevActive) }
            .show()
    }

    private fun showTabCloseMenu() {
        popup {
            item(getString(R.string.action_close)) { closeTabAt(active) }
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
                        beginSaveAsFlow(SaveAsFlow(session = s, encoding = null, closeIndex = active))
                        saveAsLauncher.launch(mimeFor(s.document.fileName) to s.document.fileName)
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
        if (findPanelOpen) binding.findInput.requestFocus()
    }

    private fun refreshFindOptions() {
        findOptions = findOptions.copy(
            query = binding.findInput.text.toString(),
            replaceWith = binding.replaceInput.text.toString(),
            matchCase = binding.cbMatchCase.isChecked,
            wholeWord = binding.cbWholeWord.isChecked,
            regex = binding.cbRegex.isChecked,
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

    private fun transformCase(op: (String, Int, Int) -> String) {
        val text = editor.text?.toString() ?: return
        val s = editor.selectionStart.coerceAtMost(editor.selectionEnd)
        val e = editor.selectionEnd.coerceAtLeast(editor.selectionStart)
        val replaced = op(text, s, e)
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
        saveAsQueue.clear()
        saveAsQueue.addAll(tabs.filter { it.document.modified && it.document.uri == null })
        Toast.makeText(this, getString(R.string.save_all_result, saved), Toast.LENGTH_SHORT).show()
        launchNextQueuedSaveAs()
    }

    /** Starts the Save As flow for the next queued untitled tab (Save All), one picker at a time. */
    private fun launchNextQueuedSaveAs() {
        val next = saveAsQueue.removeFirstOrNull() ?: return
        if (!tabs.contains(next) || !next.document.modified) return launchNextQueuedSaveAs()
        saveAsWithEncoding(next)
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
        item(getString(R.string.action_uppercase)) { transformCase { t, s, e -> TextUtils.toUpper(t, s, e) } }
        item(getString(R.string.action_lowercase)) { transformCase { t, s, e -> TextUtils.toLower(t, s, e) } }
        item(getString(R.string.action_titlecase)) { transformCase { t, s, e -> TextUtils.titleCase(t, s, e) } }
        item(getString(R.string.op_proper)) { transformCase { t, s, e -> TextUtils.properCase(t, s, e, blend = false) } }
        item(getString(R.string.op_proper_blend)) { transformCase { t, s, e -> TextUtils.properCase(t, s, e, blend = true) } }
        item(getString(R.string.op_sentence)) { transformCase { t, s, e -> TextUtils.sentenceCase(t, s, e, blend = false) } }
        item(getString(R.string.op_sentence_blend)) { transformCase { t, s, e -> TextUtils.sentenceCase(t, s, e, blend = true) } }
        item(getString(R.string.action_toggle_case)) { transformCase { t, s, e -> TextUtils.toggleCase(t, s, e) } }
        item(getString(R.string.op_invert)) { transformCase { t, s, e -> TextUtils.invertCase(t, s, e) } }
        item(getString(R.string.op_random_case)) { transformCase { t, s, e -> TextUtils.randomCase(t, s, e, java.util.Random()) } }
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
        item(getString(R.string.action_goto)) { gotoLine() }
        item(getString(R.string.action_find_in_files)) {
            refreshFindOptions()
            fifLauncher.launch(
                Intent(this@MainActivity, FindInFilesActivity::class.java)
                    .putExtra(FindInFilesActivity.EXTRA_QUERY, binding.findInput.text.toString())
            )
        }
    }

    private fun showRecentMenu() {
        val recents = recentFiles.list()
        if (recents.isEmpty()) {
            Toast.makeText(this, R.string.no_recent_files, Toast.LENGTH_SHORT).show()
            return
        }
        popup {
            recents.forEach { entry ->
                item(entry.name) { openRecent(entry.uri) }
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
        menu.add(getString(R.string.eol_title)).isEnabled = false
        item(getString(R.string.windows_format)) { setEol("\r\n") }
        item(getString(R.string.unix_format)) { setEol("\n") }
        item(getString(R.string.mac_format)) { setEol("\r") }
    }

    private fun showLanguagesMenu() = popup {
        LanguageMode.entries.forEach { m -> item(m.displayName) { setLanguage(m) } }
    }

    private fun showToolsMenu() = popup {
        item(getString(R.string.action_prettify)) { prettify() }
        item(getString(R.string.action_minify)) { minify() }
        item(getString(R.string.action_validate_xml)) { validateXml() }
        item(getString(R.string.action_preview)) { preview() }
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
