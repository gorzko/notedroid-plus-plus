package com.notedroidplusplus.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.notedroidplusplus.app.core.Document
import com.notedroidplusplus.app.core.FileManager
import com.notedroidplusplus.app.core.TextUtils
import com.notedroidplusplus.app.databinding.ActivityMainBinding
import com.notedroidplusplus.app.editing.CodeEditText
import com.notedroidplusplus.app.preview.PreviewActivity
import com.notedroidplusplus.app.search.FindOptions
import com.notedroidplusplus.app.search.findNext
import com.notedroidplusplus.app.search.replaceAll
import com.notedroidplusplus.app.settings.SettingsActivity
import com.notedroidplusplus.app.tools.JsonTool
import com.notedroidplusplus.app.tools.XmlTool
import java.nio.charset.Charset

class MainActivity : AppCompatActivity() {

    companion object { private const val MAX_UNDO = 200 }

    private lateinit var binding: ActivityMainBinding
    private lateinit var editor: CodeEditText
    private val tabs = mutableListOf<TabSession>()
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var active = -1
    private var newCounter = 1
    private var findOptions = FindOptions()
    private var replaceMode = false
    private lateinit var files: FileManager
    private var darkTheme = false
    private var wrapLines = false

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { openUri(it) }
        }
    private val saveAsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { activeSession()?.let { s -> saveSessionTo(s, uri, askCharset = false) } }
        }

    data class TabSession(
        var document: Document,
        var tab: TabLayout.Tab?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        editor = binding.editor
        files = FileManager(FileManager.defaultIo(this))

        if (tabs.isEmpty()) addTab(null, null)

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
        binding.btnFindNext.setOnClickListener { findNextMatch() }
        binding.btnFindClose.setOnClickListener { binding.findPanel.visibility = View.GONE }
        binding.btnReplace.setOnClickListener { replaceOne() }
        binding.btnReplaceAll.setOnClickListener { replaceEverything() }
        binding.cbMatchCase.setOnCheckedChangeListener { _, _ -> refreshFindOptions() }
        binding.cbWholeWord.setOnCheckedChangeListener { _, _ -> refreshFindOptions() }
        binding.cbRegex.setOnCheckedChangeListener { _, _ -> refreshFindOptions() }

        editor.onSelectionChange = { updateStatus() }

        val intentUri = intent?.data
        if (intentUri != null) openUri(intentUri)
        else updateStatus()
    }

    private fun activeSession(): TabSession? = tabs.getOrNull(active)

    private fun addTab(uri: Uri?, initialText: String?) {
        val name = uri?.let { files.displayName(it) } ?: getString(R.string.tab_new, newCounter++)
        val doc = Document(
            uri = uri?.toString(),
            fileName = name,
            text = initialText ?: "",
            language = uri?.let { LanguageMode.fromFileName(name) } ?: LanguageMode.NORMAL_TEXT
        )
        val tab = binding.tabLayout.newTab().setText(if (doc.modified) "● ${doc.fileName}" else doc.fileName)
        binding.tabLayout.addTab(tab)
        tabs.add(TabSession(doc, tab))
        activateTab(tabs.size - 1)
    }

    private fun openUri(uri: Uri) {
        try {
            val loaded = files.load(uri)
            addTab(uri, loaded.text)
            activeSession()?.let {
                it.document = it.document.copy(
                    encoding = loaded.charset.name(),
                    lineEnding = loaded.lineEnding
                )
            }
            refreshTabLabels()
            updateStatus()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_load_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun activateTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        syncActiveDocument()
        active = index
        val s = tabs[index]
        editor.removeTextChangedListener(textListener())
        editor.setText(s.document.text)
        editor.language = s.document.language
        editor.setSelection(s.document.text.length)
        editor.addTextChangedListener(tw)
        refreshTabLabels()
        updateStatus()
    }

    private val tw = object : android.text.TextWatcher {
        private var last: String = ""
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { last = s?.toString() ?: "" }
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            if (last != s?.toString() && ::editor.isInitialized) {
                if (last.length <= MAX_UNDO && s != null) {
                    undoStack.addLast(last)
                    if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
                    redoStack.clear()
                }
            }
            onTextChanged()
        }
    }

    private fun performUndo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeLast()
        redoStack.addLast(editor.text.toString())
        editor.removeTextChangedListener(tw)
        editor.setText(prev)
        editor.setSelection(prev.length)
        editor.addTextChangedListener(tw)
        onTextChanged()
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeLast()
        undoStack.addLast(editor.text.toString())
        editor.removeTextChangedListener(tw)
        editor.setText(next)
        editor.setSelection(next.length)
        editor.addTextChangedListener(tw)
        onTextChanged()
    }

    private fun syncActiveDocument() {
        val s = activeSession() ?: return
        val text = editor.text.toString()
        if (text != s.document.text) {
            s.document = s.document.copy(text = text, modified = s.document.uri != null || text.isNotEmpty())
        }
    }

    private fun onTextChanged() {
        val s = activeSession() ?: return
        val modified = s.document.uri != null || editor.text.isNotEmpty()
        if (modified != s.document.modified || editor.text.toString() != s.document.text) {
            s.document = s.document.copy(text = editor.text.toString(), modified = modified)
        }
        refreshTabLabels()
        updateStatus()
    }

    private fun refreshTabLabels() {
        tabs.forEachIndexed { i, s ->
            s.tab?.text = if (s.document.modified) "● ${s.document.fileName}" else s.document.fileName
        }
    }

    private fun updateStatus() {
        val text = editor.text.toString()
        val pos = editor.selectionStart.coerceIn(0, text.length)
        val before = text.substring(0, pos)
        val line = before.count { it == '\n' } + 1
        val col = pos - (before.lastIndexOf('\n').let { if (it < 0) -1 else it })
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

    private fun saveActive() {
        val s = activeSession() ?: return
        val uri = s.document.uri
        if (uri != null) {
            saveSessionTo(s, Uri.parse(uri), askCharset = false)
        } else {
            saveAsLauncher.launch(s.document.fileName)
        }
    }

    private fun saveSessionTo(s: TabSession, uri: Uri, askCharset: Boolean) {
        try {
            val charset = Charset.forName(s.document.encoding)
            files.save(uri, editor.text.toString(), charset, s.document.lineEnding)
            s.document = s.document.copy(
                uri = uri.toString(),
                fileName = files.displayName(uri),
                modified = false,
                language = LanguageMode.fromFileName(files.displayName(uri))
            )
            editor.language = s.document.language
            refreshTabLabels()
            updateStatus()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_save_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun closeActive() {
        val s = activeSession() ?: return
        if (s.document.modified) {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm_save_title)
                .setMessage(getString(R.string.confirm_save_msg, s.document.fileName))
                .setPositiveButton(R.string.yes) { _, _ -> saveActive(); removeTab(active) }
                .setNegativeButton(R.string.no) { _, _ -> removeTab(active) }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else removeTab(active)
    }

    private fun removeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        binding.tabLayout.removeTab(tabs[index].tab)
        tabs.removeAt(index)
        if (tabs.isEmpty()) { addTab(null, null); return }
        active = (active).coerceAtMost(tabs.size - 1)
        activateTab(active.coerceAtLeast(0))
    }

    private fun toggleFindPanel(replace: Boolean) {
        replaceMode = replace
        val visible = binding.findPanel.visibility != View.VISIBLE
        binding.findPanel.visibility = if (visible || replaceMode != replace) View.VISIBLE else View.GONE
        binding.replaceRow.visibility = if (replaceMode) View.VISIBLE else View.GONE
        if (visible) binding.findInput.requestFocus()
    }

    private fun refreshFindOptions() {
        findOptions = findOptions.copy(
            query = binding.findInput.text.toString(),
            replaceWith = binding.replaceInput.text.toString(),
            matchCase = binding.cbMatchCase.isChecked,
            wholeWord = binding.cbWholeWord.isChecked,
            regex = binding.cbRegex.isChecked
        )
    }

    private fun findNextMatch() {
        refreshFindOptions()
        val text = editor.text.toString()
        val start = editor.selectionEnd
        val idx = findNext(text, start, findOptions)
        if (idx >= 0) {
            editor.setSelection(idx, idx + findQueryLength(text, idx))
        } else {
            Toast.makeText(this, R.string.toast_no_match, Toast.LENGTH_SHORT).show()
        }
    }

    private fun findQueryLength(text: String, idx: Int): Int {
        return if (findOptions.regex) {
            try {
                Regex(findOptions.query).find(text.substring(idx))?.value?.length ?: 0
            } catch (_: Exception) { 0 }
        } else findOptions.query.length
    }

    private fun replaceOne() {
        refreshFindOptions()
        val text = editor.text.toString()
        val selStart = editor.selectionStart
        if (text.substring(selStart, editor.selectionEnd) == findOptions.query ||
            (findOptions.regex && Regex(findOptions.query).matches(text.substring(selStart, editor.selectionEnd)))
        ) {
            editor.text.replace(selStart, editor.selectionEnd, findOptions.replaceWith)
        }
        findNextMatch()
    }

    private fun replaceEverything() {
        refreshFindOptions()
        val (newText, count) = replaceAll(editor.text.toString(), findOptions)
        editor.removeTextChangedListener(tw)
        editor.setText(newText)
        editor.addTextChangedListener(tw)
        onTextChanged()
        Toast.makeText(this, getString(R.string.replaced_occurrences, count), Toast.LENGTH_SHORT).show()
    }

    private fun gotoLine() {
        val max = editor.text.toString().count { it == '\n' } + 1
        val input = EditText(this).apply { hint = getString(R.string.goto_line, max) }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_goto)
            .setView(input)
            .setPositiveButton(R.string.yes) { _, _ ->
                val line = input.text.toString().toIntOrNull() ?: return@setPositiveButton
                val offset = com.notedroidplusplus.app.core.TextUtils.lineIndexToOffset(editor.text.toString(), (line - 1).coerceIn(0, max - 1))
                editor.setSelection(offset)
                editor.requestFocus()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun prettify() {
        when (activeSession()?.document?.language) {
            LanguageMode.JSON -> formatTool(JsonTool::prettify, R.string.error_invalid_json)
            LanguageMode.XML -> formatTool({ XmlTool.prettify(it) }, R.string.error_invalid_xml)
            else -> Toast.makeText(this, "Prettify applies to JSON/XML", Toast.LENGTH_SHORT).show()
        }
    }

    private fun minify() {
        when (activeSession()?.document?.language) {
            LanguageMode.JSON -> formatTool(JsonTool::minify, R.string.error_invalid_json)
            LanguageMode.XML -> formatTool({ XmlTool.minify(it) }, R.string.error_invalid_xml)
            else -> Toast.makeText(this, "Minify applies to JSON/XML", Toast.LENGTH_SHORT).show()
        }
    }

    private inline fun formatTool(tool: (String) -> Result<String>, errorRes: Int) {
        tool(editor.text.toString())
            .onSuccess { result ->
                editor.removeTextChangedListener(tw)
                editor.setText(result)
                editor.addTextChangedListener(tw)
                onTextChanged()
            }
            .onFailure { Toast.makeText(this, errorRes, Toast.LENGTH_SHORT).show() }
    }

    private fun preview() {
        val s = activeSession() ?: return
        when (s.document.language) {
            LanguageMode.MARKDOWN -> PreviewActivity.start(this, PreviewActivity.Mode.MARKDOWN, editor.text.toString())
            LanguageMode.HTML -> PreviewActivity.start(this, PreviewActivity.Mode.HTML, editor.text.toString())
            else -> Toast.makeText(this, "Preview supports Markdown and HTML", Toast.LENGTH_SHORT).show()
        }
    }

    private fun transformCase(op: (String, Int, Int) -> String) {
        val text = editor.text
        val (s, e) = editor.selectionStart to editor.selectionEnd
        val (ss, ee) = (if (s <= e) s else e) to (if (s <= e) e else s)
        editor.removeTextChangedListener(tw)
        editor.setText(op(text.toString(), ss, ee))
        editor.setSelection(ss, ee)
        editor.addTextChangedListener(tw)
        onTextChanged()
    }

    private fun trimTrailing() {
        val t = TextUtils.trimTrailingWhitespace(editor.text.toString())
        editor.removeTextChangedListener(tw)
        editor.setText(t)
        editor.addTextChangedListener(tw)
        onTextChanged()
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
            8 -> startActivity(Intent(this, com.notedroidplusplus.app.plugins.PluginListActivity::class.java))
            9 -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        return true
    }

    private fun anchor(): View = binding.toolbar

    private fun popup(listener: PopupMenu.() -> Unit): PopupMenu {
        val pm = PopupMenu(this, anchor())
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
        item(getString(R.string.action_open_folder)) { openLauncher.launch(arrayOf("*/*")) }
        item(getString(R.string.action_save)) { saveActive() }
        item(getString(R.string.action_save_as)) { activeSession()?.let { saveAsLauncher.launch(it.document.fileName) } }
        item(getString(R.string.action_close)) { closeActive() }
        item(getString(R.string.action_exit)) { finish() }
    }

    private fun showEditMenu() = popup {
        item(getString(R.string.action_undo)) { performUndo() }
        item(getString(R.string.action_redo)) { performRedo() }
        item(getString(R.string.action_cut)) { editor.cut() }
        item(getString(R.string.action_copy)) { editor.copy() }
        item(getString(R.string.action_paste)) { editor.paste() }
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
        item(getString(R.string.theme_light)) { applyTheme(false) }
        item(getString(R.string.theme_dark)) { applyTheme(true) }
        item(getString(R.string.wrap_lines)) { wrapLines = !wrapLines; setWrap(wrapLines) }
        item(getString(R.string.show_line_numbers)) { editor.showLineNumbers = !editor.showLineNumbers }
        item(getString(R.string.action_zoom_in)) { zoom(1.25f) }
        item(getString(R.string.action_zoom_out)) { zoom(0.8f) }
    }

    private fun showEncodingMenu() = popup {
        TextUtils.names().forEach { name -> item(name) { setEncoding(name) } }
        menu.add(getString(R.string.eol_title))
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

    private fun applyTheme(dark: Boolean) {
        darkTheme = dark
        editor.applyTheme(dark)
    }

    private fun setWrap(wrap: Boolean) {
        editor.setHorizontallyScrolling(!wrap)
    }

    private fun zoom(factor: Float) {
        val size = editor.textSize / resources.displayMetrics.scaledDensity
        editor.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, (size * factor).coerceIn(8f, 40f))
        editor.rehighlight()
    }
}
