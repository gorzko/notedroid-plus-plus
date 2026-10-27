package com.notedroidplusplus.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.tabs.TabLayout
import com.notedroidplusplus.app.core.Document
import com.notedroidplusplus.app.core.FileManager
import com.notedroidplusplus.app.core.TextUtils
import com.notedroidplusplus.app.databinding.ActivityMainBinding
import com.notedroidplusplus.app.editing.CodeEditText
import com.notedroidplusplus.app.plugins.PluginHost
import com.notedroidplusplus.app.plugins.PluginRegistry
import com.notedroidplusplus.app.preview.PreviewActivity
import com.notedroidplusplus.app.search.FindOptions
import com.notedroidplusplus.app.search.findNext
import com.notedroidplusplus.app.search.findPrev
import com.notedroidplusplus.app.search.replaceAll
import com.notedroidplusplus.app.settings.SettingsActivity
import com.notedroidplusplus.app.tools.JsonTool
import com.notedroidplusplus.app.tools.XmlTool
import java.nio.charset.Charset

class MainActivity : AppCompatActivity(), PluginHost {

    companion object {
        private const val MAX_UNDO = 200
        private const val KEY_TABS = "tabs_state"
        private const val KEY_ACTIVE = "active_tab"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var editor: CodeEditText
    private lateinit var files: FileManager
    private lateinit var prefs: SharedPreferences
    private lateinit var pluginRegistry: PluginRegistry

    data class TabSession(
        var document: Document,
        var tab: TabLayout.Tab?,
        val undoStack: ArrayDeque<String> = ArrayDeque(),
        val redoStack: ArrayDeque<String> = ArrayDeque(),
        var caret: Int = 0
    )

    private val tabs = mutableListOf<TabSession>()
    private var active = -1
    private var newCounter = 1
    private var findOptions = FindOptions()
    private var findPanelOpen = false
    private var pendingCloseIndex = -1

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openUri(it) }
        }

    private val saveAsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) {
                if (pendingCloseIndex >= 0) pendingCloseIndex = -1
                return@registerForActivityResult
            }
            val index = pendingCloseIndex
            val session = if (index >= 0) tabs.getOrNull(index) else activeSession()
            if (session != null) {
                val saved = saveSessionTo(session, uri)
                if (index >= 0) {
                    if (saved) removeTab(index) else pendingCloseIndex = -1
                }
            }
            pendingCloseIndex = -1
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

        applyPersistedSettings()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { activateTab(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) { syncActiveDocument() }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        binding.btnNew.setOnClickListener { addTab(null, null) }
        binding.btnOpen.setOnClickListener { openLauncher.launch(arrayOf("text/*", "application/json", "application/xml")) }
        binding.btnSave.setOnClickListener { saveActive() }
        binding.btnFind.setOnClickListener { toggleFindPanel(false) }
        binding.btnPreview.setOnClickListener { preview() }
        binding.btnPrettify.setOnClickListener { prettify() }
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
            addTab(null, null)
            val intentUri = intent?.data
            if (intentUri != null) openUri(intentUri)
        }
        updateStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { openUri(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        syncActiveDocument()
        activeSession()?.let { it.caret = editor.selectionStart }
        val states = tabs.map { arrayOf(it.document.uri, it.document.fileName, it.document.text, it.document.language.name, it.document.encoding, it.document.lineEnding, it.document.modified, it.caret) }
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
        }
        val act = state.getInt(KEY_ACTIVE, 0).coerceIn(0, tabs.size - 1)
        activateTab(act)
        activeSession()?.let { editor.setSelection(it.caret.coerceIn(0, it.document.text.length)) }
        refreshTabLabels()
    }

    private fun applyPersistedSettings() {
        val dark = prefs.getBoolean("dark_theme", false)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        editor.showLineNumbers = prefs.getBoolean("show_line_numbers", true)
        val fontSize = prefs.getInt("font_size", 14)
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat().coerceIn(8f, 40f))
    }

    override fun onResume() {
        super.onResume()
        editor.showLineNumbers = prefs.getBoolean("show_line_numbers", true)
        val fontSize = prefs.getInt("font_size", 14)
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat().coerceIn(8f, 40f))
        editor.applyTheme(resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES)
    }

    // ---- PluginHost ----

    override val context: android.content.Context get() = this

    override fun getText(): String = editor.text?.toString() ?: ""

    override fun setText(text: String) = setEditorText(text)

    override fun getSelection(): String =
        editor.text?.substring(editor.selectionStart.coerceAtMost(editor.selectionEnd), editor.selectionEnd.coerceAtLeast(editor.selectionStart)).orEmpty()

    override fun language(): LanguageMode = activeSession()?.document?.language ?: LanguageMode.NORMAL_TEXT

    override fun showStatus(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setEditorText(text: String) {
        val s = activeSession() ?: return
        pushUndo(s)
        editor.removeTextChangedListener(tw)
        editor.setText(text)
        editor.setSelection(text.length)
        editor.addTextChangedListener(tw)
        s.document = s.document.copy(text = text, modified = true)
        refreshTabLabels()
        updateStatus()
    }

    private fun pushUndo(s: TabSession) {
        val snapshot = editor.text?.toString() ?: return
        s.undoStack.addLast(snapshot)
        if (s.undoStack.size > MAX_UNDO) s.undoStack.removeFirst()
        s.redoStack.clear()
    }

    // ---- Text watcher (records per-tab undo) ----

    private val tw = object : android.text.TextWatcher {
        private var last: String = ""
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { last = s?.toString() ?: "" }
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            val sess = activeSession()
            if (sess != null && last != s?.toString()) {
                sess.undoStack.addLast(last)
                if (sess.undoStack.size > MAX_UNDO) sess.undoStack.removeFirst()
                sess.redoStack.clear()
            }
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
        val tab = binding.tabLayout.newTab().setText(doc.fileName)
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
            val session = addTab(uri, loaded.text)
            session.document = session.document.copy(
                encoding = loaded.charset.name(),
                lineEnding = loaded.lineEnding,
                hasBom = loaded.hadBom
            )
            refreshTabLabels()
            updateStatus()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_load_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun activateTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        activeSession()?.let { it.caret = editor.selectionStart }
        syncActiveDocument()
        active = index
        val s = tabs[index]
        editor.removeTextChangedListener(tw)
        editor.setText(s.document.text)
        editor.language = s.document.language
        editor.setSelection(s.caret.coerceIn(0, s.document.text.length))
        editor.addTextChangedListener(tw)
        refreshTabLabels()
        updateStatus()
    }

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
            s.tab?.text = if (s.document.modified) "● ${s.document.fileName}" else s.document.fileName
        }
    }

    private fun updateStatus() {
        val text = editor.text?.toString() ?: ""
        val pos = editor.selectionStart.coerceIn(0, text.length)
        val before = text.substring(0, pos)
        val line = before.count { it == '\n' } + 1
        val col = pos - before.lastIndexOf('\n')
        binding.statusPosition.text = getString(R.string.line_col, line, col)
        val selLen = editor.selectionEnd - editor.selectionStart
        binding.statusSelection.text = if (selLen > 0) getString(R.string.sel, selLen) else ""
        val s = activeSession()
        binding.statusEncoding.text = s?.document?.encoding ?: "UTF-8"
        binding.statusEol.text = when (s?.document?.lineEnding) {
            "\r\n" -> "Windows (CR LF)"
            "\r" -> "Mac (CR)"
            else -> "Unix (LF)"
        }
        binding.statusLanguage.text = s?.document?.language?.displayName ?: ""
    }

    // ---- Save / Close ----

    private fun saveActive() {
        val s = activeSession() ?: return
        val uri = s.document.uri
        if (uri != null) {
            saveSessionTo(s, Uri.parse(uri))
        } else {
            pendingCloseIndex = -1
            saveAsLauncher.launch(s.document.fileName)
        }
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

    private fun closeActive() {
        val s = activeSession() ?: return
        if (s.document.modified) {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_save_title)
                .setMessage(getString(R.string.confirm_save_msg, s.document.fileName))
                .setPositiveButton(R.string.yes) { _, _ ->
                    val uri = s.document.uri
                    if (uri != null) {
                        if (saveSessionTo(s, Uri.parse(uri))) removeTab(active) else pendingCloseIndex = -1
                    } else {
                        pendingCloseIndex = active
                        saveAsLauncher.launch(s.document.fileName)
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
        val s = activeSession() ?: return
        if (s.undoStack.isEmpty()) return
        val prev = s.undoStack.removeLast()
        s.redoStack.addLast(editor.text?.toString() ?: "")
        editor.removeTextChangedListener(tw)
        editor.setText(prev)
        editor.setSelection(prev.length)
        editor.addTextChangedListener(tw)
        s.document = s.document.copy(text = prev, modified = s.document.uri != null || prev.isNotEmpty())
        refreshTabLabels()
        updateStatus()
    }

    private fun performRedo() {
        val s = activeSession() ?: return
        if (s.redoStack.isEmpty()) return
        val next = s.redoStack.removeLast()
        s.undoStack.addLast(editor.text?.toString() ?: "")
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

    private fun preview() {
        val s = activeSession() ?: return
        when (s.document.language) {
            LanguageMode.MARKDOWN -> PreviewActivity.start(this, PreviewActivity.Mode.MARKDOWN, editor.text?.toString() ?: "")
            LanguageMode.HTML -> PreviewActivity.start(this, PreviewActivity.Mode.HTML, editor.text?.toString() ?: "")
            else -> Toast.makeText(this, R.string.tool_preview_only, Toast.LENGTH_SHORT).show()
        }
    }

    private fun transformCase(op: (String, Int, Int) -> String) {
        val text = editor.text?.toString() ?: return
        val s = editor.selectionStart.coerceAtMost(editor.selectionEnd)
        val e = editor.selectionEnd.coerceAtLeast(editor.selectionStart)
        val replaced = op(text, s, e)
        setEditorText(replaced)
        editor.setSelection(s, e)
    }

    private fun trimTrailing() {
        val t = TextUtils.trimTrailingWhitespace(editor.text?.toString() ?: "")
        setEditorText(t)
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
        item(getString(R.string.action_save)) { saveActive() }
        item(getString(R.string.action_save_as)) { activeSession()?.let { pendingCloseIndex = -1; saveAsLauncher.launch(it.document.fileName) } }
        item(getString(R.string.action_close)) { closeActive() }
        item(getString(R.string.action_exit)) { finish() }
    }

    private fun showEditMenu() = popup {
        item(getString(R.string.action_undo)) { performUndo() }
        item(getString(R.string.action_redo)) { performRedo() }
        item(getString(R.string.action_cut)) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), getSelection()))
            editor.text?.delete(editor.selectionStart, editor.selectionEnd)
        }
        item(getString(R.string.action_copy)) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), getSelection()))
        }
        item(getString(R.string.action_paste)) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            editor.text?.insert(editor.selectionStart.coerceAtMost(editor.selectionEnd), clip)
        }
        item(getString(R.string.action_select_all)) { editor.selectAll() }
        item(getString(R.string.action_uppercase)) { transformCase { t, s, e -> TextUtils.toUpper(t, s, e) } }
        item(getString(R.string.action_lowercase)) { transformCase { t, s, e -> TextUtils.toLower(t, s, e) } }
        item(getString(R.string.action_titlecase)) { transformCase { t, s, e -> TextUtils.titleCase(t, s, e) } }
        item(getString(R.string.action_toggle_case)) { transformCase { t, s, e -> TextUtils.toggleCase(t, s, e) } }
        item(getString(R.string.action_trim_trailing)) { trimTrailing() }
    }

    private fun showSearchMenu() = popup {
        item(getString(R.string.action_find)) { toggleFindPanel(false) }
        item(getString(R.string.action_replace)) { toggleFindPanel(true) }
        item(getString(R.string.action_goto)) { gotoLine() }
    }

    private fun showViewMenu() = popup {
        item(getString(R.string.theme_light)) { setDarkTheme(false) }
        item(getString(R.string.theme_dark)) { setDarkTheme(true) }
        item(getString(R.string.wrap_lines)) { editor.setHorizontallyScrolling(false) }
        item(getString(R.string.no_wrap_lines)) { editor.setHorizontallyScrolling(true) }
        item(getString(R.string.show_line_numbers)) { editor.showLineNumbers = !editor.showLineNumbers }
        item(getString(R.string.action_zoom_in)) { zoom(1.25f) }
        item(getString(R.string.action_zoom_out)) { zoom(0.8f) }
    }

    private fun setDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean("dark_theme", dark).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        editor.applyTheme(dark)
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
        item(getString(R.string.action_preview)) { preview() }
    }

    private fun showPluginsMenu() = popup {
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
