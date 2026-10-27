package com.notedroidplusplus.app.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.notedroidplusplus.app.R
import com.notedroidplusplus.app.search.FindOptions
import com.notedroidplusplus.app.search.SearchHit
import com.notedroidplusplus.app.search.findAll
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/** Notepad++-style Find in Files: searches a SAF folder tree and lists per-file/line hits. */
class FindInFilesActivity : AppCompatActivity() {

    private lateinit var queryInput: EditText
    private lateinit var matchCase: CheckBox
    private lateinit var wholeWord: CheckBox
    private lateinit var regex: CheckBox
    private lateinit var resultsList: ListView
    private lateinit var progress: android.widget.ProgressBar
    private var results: List<SearchHit> = emptyList()
    @Volatile private var searchCancelled = false
    private var searchThread: Thread? = null

    private val treeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { runSearch(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_find_in_files)
        title = getString(R.string.action_find_in_files)
        queryInput = findViewById(R.id.fif_query)
        matchCase = findViewById(R.id.fif_match_case)
        wholeWord = findViewById(R.id.fif_whole_word)
        regex = findViewById(R.id.fif_regex)
        resultsList = findViewById(R.id.fif_results)
        progress = findViewById(android.R.id.progress)

        val prefill = intent.getStringExtra(EXTRA_QUERY)
        if (!prefill.isNullOrBlank()) queryInput.setText(prefill)

        findViewById<Button>(R.id.fif_cancel).setOnClickListener {
            searchCancelled = true
        }
        findViewById<Button>(R.id.fif_search).setOnClickListener {
            if (queryInput.text.isNullOrBlank()) {
                Toast.makeText(this, R.string.find_what, Toast.LENGTH_SHORT).show()
            } else {
                treeLauncher.launch(null)
            }
        }

        resultsList.setOnItemClickListener { _, _, position, _ ->
            val hit = results.getOrNull(position) ?: return@setOnItemClickListener
            val uri = hit.uri?.let { Uri.parse(it) }
            if (uri != null) {
                setResult(RESULT_OK, Intent().apply {
                    putExtra(EXTRA_OPEN_URI, uri.toString())
                    putExtra(EXTRA_OPEN_LINE, hit.line)
                })
                finish()
            }
        }
    }

    private fun runSearch(treeUri: Uri) {
        val opts = FindOptions(
            query = queryInput.text.toString(),
            matchCase = matchCase.isChecked,
            wholeWord = wholeWord.isChecked,
            regex = regex.isChecked
        )
        progress.visibility = View.VISIBLE
        thread {
            val hits = mutableListOf<SearchHit>()
            val resolver = contentResolver
            val root = DocumentFile.fromTreeUri(this, treeUri)
            if (root != null) {
                walk(root, opts, resolver) { hits.addAll(it) }
            }
            runOnUiThread {
                progress.visibility = View.GONE
                results = hits
                if (hits.isEmpty()) {
                    Toast.makeText(this, R.string.toast_no_match, Toast.LENGTH_SHORT).show()
                }
                resultsList.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_2,
                    android.R.id.text1,
                    hits.map { "${it.file}:${it.line}:${it.column}" }
                ).also { adapter ->
                    // two-line rows via override is skipped for simplicity: use custom bind
                }
                bindResults(hits)
            }
        }
    }

    private fun bindResults(hits: List<SearchHit>) {
        resultsList.adapter = object : ArrayAdapter<SearchHit>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, hits
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                val hit = hits[position]
                v.findViewById<android.widget.TextView>(android.R.id.text1).text =
                    "${hit.file}:${hit.line}:${hit.column}"
                v.findViewById<android.widget.TextView>(android.R.id.text2).text = hit.lineText
                return v
            }
        }
    }

    private fun walk(
        dir: DocumentFile,
        opts: FindOptions,
        resolver: android.content.ContentResolver,
        onFile: (List<SearchHit>) -> Unit,
        onScanned: () -> Unit,
        isCancelled: () -> Boolean
    ) {
        dir.listFiles().forEach { doc ->
            if (isCancelled()) return
            if (doc.isDirectory) {
                walk(doc, opts, resolver, onFile, onScanned, isCancelled)
            } else if (doc.isFile) {
                val hits = searchFile(doc, opts, resolver)
                onScanned()
                if (hits.isNotEmpty()) onFile(hits)
            }
        }
    }

    private fun searchFile(doc: DocumentFile, opts: FindOptions, resolver: android.content.ContentResolver): List<SearchHit> {
        val size = doc.length()
        // skip huge binary-looking files: cap at 5 MB
        if (size <= 0 || size > 5 * 1024 * 1024) return emptyList()
        return runCatching {
            val bytes = resolver.openInputStream(doc.uri)?.use { it.readBytes() } ?: return emptyList()
            // crude binary check
            val probe = bytes.copyOf(minOf(bytes.size, 1024))
            if (probe.count { it == 0.toByte() } > probe.size / 8) return emptyList()
            val text = String(bytes, Charsets.UTF_8)
            findAll(text, opts, doc.name ?: doc.uri.lastPathSegment ?: "?", doc.uri.toString())
        }.getOrDefault(emptyList())
    }

    companion object {
        const val EXTRA_QUERY = "query"
        const val EXTRA_OPEN_URI = "open_uri"
        const val EXTRA_OPEN_LINE = "open_line"
        fun start(context: Context, query: String) {
            context.startActivity(Intent(context, FindInFilesActivity::class.java).apply {
                putExtra(EXTRA_QUERY, query)
            })
        }
    }
}
