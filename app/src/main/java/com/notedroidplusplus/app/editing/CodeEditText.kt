package com.notedroidplusplus.app.editing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatEditText
import com.notedroidplusplus.app.LanguageMode
import com.notedroidplusplus.app.TokenKind
import com.notedroidplusplus.app.settings.EditorTheme

/**
 * Editor view with Notepad++-style features: gutter with line numbers, syntax
 * highlighting via editable spans, current-line highlight, and bracket matching.
 */
class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var language: LanguageMode = LanguageMode.NORMAL_TEXT
        set(value) {
            field = value
            rehighlight()
        }

    var showLineNumbers: Boolean = true
        set(value) {
            field = value
            updateGutter()
            invalidate()
        }

    var darkTheme: Boolean = false
        private set

    var onSelectionChange: (() -> Unit)? = null
    var onOverwriteToggled: (() -> Unit)? = null

    // ---- Bookmarks / marks / show-symbol (#37 #38 #40) ----
    var bookmarks: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    private val markSpans = mutableListOf<MarkSpan>()

    class MarkSpan(val start: Int, val end: Int, val span: android.text.style.BackgroundColorSpan)

    fun addMark(start: Int, end: Int) {
        val e = editableText ?: return
        val span = android.text.style.BackgroundColorSpan(0x66FFCC00)
        e.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        markSpans.add(MarkSpan(start, end, span))
        invalidate()
    }

    fun clearMarks() {
        val e = editableText ?: return
        markSpans.forEach { e.removeSpan(it.span) }
        markSpans.clear()
        invalidate()
    }

    // Show Symbol flags
    var showSymbolFlags: ShowSymbolFlags = ShowSymbolFlags()
        set(value) {
            field = value
            invalidate()
        }
    var tabSize: Int = 4

    /** Skip syntax highlighting above this document size (bytes); 0 = never skip. */
    var highlightThresholdBytes: Long = 0

    var caretWidth: Int = 2
        set(value) {
            field = value
            invalidate()
        }
    var currentLineStyle: String = "highlight"
        set(value) {
            field = value
            invalidate()
        }
    var virtualSpace: Boolean = false
    var scrollPastEnd: Boolean = false
        set(value) {
            field = value
            requestLayout()
        }
    var overwriteMode: Boolean = false
        set(value) {
            field = value
            onOverwriteToggled?.invoke()
            invalidate()
        }
    var leftPaddingDp: Int = 4
        set(value) {
            field = value
            applyEditorPadding()
        }
    var rightPaddingDp: Int = 4
        set(value) {
            field = value
            applyEditorPadding()
        }

    var edgeColumns: List<Int> = emptyList()
        set(value) {
            field = value
            invalidate()
        }
    var edgeBackgroundMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    var showVerticalEdge: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    private val edgePaint = Paint().apply { strokeWidth = 1f }
    private val edgeShadePaint = Paint()

    private fun applyEditorPadding() {
        val density = resources.displayMetrics.density
        val leftPx = (leftPaddingDp * density).toInt()
        val rightPx = (rightPaddingDp * density).toInt()
        setPadding(
            (if (showLineNumbers) gutterWidth + 10 else 10) + leftPx,
            paddingTop,
            rightPx,
            paddingBottom
        )
    }

    fun overwriteInsert(text: String, pos: Int, typed: String): Pair<Int, String> {
        if (!overwriteMode) return pos to typed
        val end = (pos + typed.length).coerceAtMost(text.length)
        return pos to typed + text.substring(pos, end)
    }

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterBgPaint = Paint()
    private val highlightPaint = Paint()
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private var gutterWidth = 0
    private var colors: Map<TokenKind, Int> = emptyMap()
    private var gutterTextColor = 0xFF6E6E6E.toInt()
    private var gutterBgColor = 0xFFEBEBEB.toInt()
    private var highlightColor = 0xFFE8E8FF.toInt()
    private var bracketColor = 0xFF808080.toInt()
    private var bracketPair: Pair<Int, Int>? = null

    init {
        gravity = Gravity.TOP or Gravity.START
        typeface = android.graphics.Typeface.MONOSPACE
        setBackgroundColor(0xFFFFFFFF.toInt())
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                scheduleHighlight()
            }
        })
        viewTreeObserver.addOnScrollChangedListener { invalidate() }
        post { updateGutter() }
    }

    fun applyEditorTheme(theme: EditorTheme) {
        darkTheme = theme.dark
        colors = theme.syntax
        gutterTextColor = theme.gutterText
        gutterBgColor = theme.gutterBg
        highlightColor = theme.currentLine
        bracketColor = theme.bracket
        setBackgroundColor(theme.editorBg)
        setTextColor(theme.editorText)
        gutterPaint.textSize = paint.textSize * 0.85f
        updateGutter()
        rehighlight()
    }

    private fun updateGutter() {
        val paint = paint
        gutterPaint.textSize = paint.textSize * 0.85f
        if (showLineNumbers) {
            val lines = (editableText?.length ?: 0).let { len ->
                var c = 1
                for (i in 0 until len) if (editableText[i] == '\n') c++
                c
            }
            val digits = maxOf(3, lines.toString().length)
            val w = (digits * paint.measureText("8") * 0.9f).toInt() + 16
            if (w != gutterWidth) {
                gutterWidth = w
                setPadding(gutterWidth + 10, paddingTop, paddingRight, paddingBottom)
            }
        } else {
            if (gutterWidth != 0) {
                gutterWidth = 0
                applyEditorPadding()
            }
        }
        if (showLineNumbers) applyEditorPadding()
        invalidate()
    }

    private val highlightRunnable = Runnable {
        updateGutter()
        rehighlightNow()
    }

    private var highlightScheduled = false

    private fun scheduleHighlight() {
        if (highlightScheduled) return
        highlightScheduled = true
        postDelayed(highlightRunnable, 150)
    }

    fun rehighlight() {
        removeCallbacks(highlightRunnable)
        highlightScheduled = false
        rehighlightNow()
    }

    private fun rehighlightNow() {
        highlightScheduled = false
        val e = editableText ?: return
        e.getSpans(0, e.length, SyntaxSpan::class.java).forEach { e.removeSpan(it) }
        val docBytes = e.toString().toByteArray(Charsets.UTF_8).size.toLong()
        val tooLarge = highlightThresholdBytes in 1..docBytes
        if (colors.isNotEmpty() && !tooLarge) {
            SyntaxHighlighter.tokenize(e.toString(), language).forEach { tok ->
                val color = colors[tok.kind] ?: return@forEach
                e.setSpan(SyntaxSpan(color), tok.start, tok.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        computeBracket()
        invalidate()
    }

    fun updateGutterPublic() {
        updateGutter()
        rehighlightNow()
    }

    private fun computeBracket() {
        bracketPair = null
        val text = editableText?.toString() ?: return
        val pos = selectionStart
        if (pos < 0 || pos > text.length) return
        val pairs = mapOf('(' to ')', '[' to ']', '{' to '}', '<' to '>')
        val ch = text.getOrNull(pos)
        val prev = text.getOrNull(pos - 1)
        when {
            ch != null && pairs.containsKey(ch) -> {
                val end = findMatching(text, pos, ch, pairs[ch]!!)
                if (end >= 0) bracketPair = pos to end
            }
            ch != null && pairs.containsValue(ch) -> {
                val open = pairs.entries.first { it.value == ch }.key
                val begin = findMatchingBack(text, pos, ch, open)
                if (begin >= 0) bracketPair = begin to pos
            }
            prev != null && pairs.containsKey(prev) -> {
                val end = findMatching(text, pos - 1, prev, pairs[prev]!!)
                if (end >= 0) bracketPair = (pos - 1) to end
            }
            prev != null && pairs.containsValue(prev) -> {
                val open = pairs.entries.first { it.value == prev }.key
                val begin = findMatchingBack(text, pos - 1, prev, open)
                if (begin >= 0) bracketPair = begin to (pos - 1)
            }
        }
    }

    private fun findMatching(t: String, openPos: Int, open: Char, close: Char): Int {
        var depth = 0
        for (i in openPos until t.length) {
            when (t[i]) {
                open -> depth++
                close -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    private fun findMatchingBack(t: String, closePos: Int, close: Char, open: Char): Int {
        var depth = 0
        for (i in closePos downTo 0) {
            when (t[i]) {
                close -> depth++
                open -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        computeBracket()
        onSelectionChange?.invoke()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val gl = layout ?: return

        if (showLineNumbers) {
            val top = scrollY
            gutterBgPaint.color = gutterBgColor
            canvas.drawRect(
                scrollX.toFloat(), top.toFloat(), (scrollX + gutterWidth).toFloat(),
                (top + height).toFloat(), gutterBgPaint
            )
            gutterPaint.color = gutterTextColor
            for (line in 0 until gl.lineCount) {
                val baseline = gl.getLineBaseline(line) - scrollY + paddingTop
                if (baseline > top + height + lineHeight()) break
                if (baseline >= top - lineHeight()) {
                    val label = (line + 1).toString()
                    canvas.drawText(
                        label,
                        scrollX + gutterWidth - 8 - gutterPaint.measureText(label),
                        baseline.toFloat(),
                        gutterPaint
                    )
                }
            }
        }

        drawVerticalEdge(canvas)
        drawShowSymbol(canvas, gl)
        drawBookmarks(canvas, gl)

        val sel = selectionStart
        if (sel >= 0 && currentLineStyle != "none") {
            val line = gl.getLineForOffset(sel)
            val rectTop = gl.getLineTop(line) + paddingTop - scrollY
            val rectBottom = gl.getLineBottom(line) + paddingTop - scrollY
            if (rectBottom >= 0 && rectTop <= height) {
                if (currentLineStyle == "frame") {
                    bracketPaint.color = bracketColor
                    canvas.drawRect(
                        (scrollX + 1).toFloat(), (rectTop + 1).toFloat(),
                        (scrollX + width - 1).toFloat(), (rectBottom - 1).toFloat(),
                        bracketPaint
                    )
                } else {
                    highlightPaint.color = highlightColor
                    canvas.drawRect(
                        scrollX.toFloat(), rectTop.toFloat(), (scrollX + width).toFloat(),
                        rectBottom.toFloat(), highlightPaint
                    )
                }
            }
        }

        bracketPair?.let { (s, e) ->
            bracketPaint.color = bracketColor
            drawBracketRect(canvas, gl, gl.getLineForOffset(s), s)
            drawBracketRect(canvas, gl, gl.getLineForOffset(e), e)
        }

        super.onDraw(canvas)
    }

    private fun lineHeight(): Int = (paint.fontMetrics.descent - paint.fontMetrics.ascent).toInt()

    /** Draws bookmark circles in the gutter for bookmarked visible lines. */
    private fun drawBookmarks(canvas: Canvas, gl: Layout) {
        if (bookmarks.isEmpty() || !showLineNumbers) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCC8800.toInt()
            style = Paint.Style.FILL
        }
        val top = scrollY
        for (line in 0 until gl.lineCount) {
            if (line !in bookmarks) continue
            val baseline = gl.getLineBaseline(line) - scrollY + paddingTop
            if (baseline > top + height + lineHeight() || baseline < top - lineHeight()) continue
            val cx = (scrollX + gutterWidth - 8 - gutterPaint.measureText("0000") / 2).toFloat()
            val cy = baseline - lineHeight() / 3f
            canvas.drawCircle(cx, cy, lineHeight() / 4f, paint)
        }
    }

    /** Draws Show Symbol decorations on the text canvas (before the text). */
    private fun drawShowSymbol(canvas: Canvas, gl: Layout) {
        val f = showSymbolFlags
        if (!f.spacesAndTabs && !f.endOfLine && !f.nonPrinting && !f.controlChars && !f.indentGuide && !f.wrapSymbol) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = this@CodeEditText.paint.textSize
            color = if (darkTheme) 0x88FFFFFF.toInt() else 0x88000000.toInt()
        }
        val text = editableText?.toString() ?: return
        val top = scrollY
        for (line in 0 until gl.lineCount) {
            val baseline = gl.getLineBaseline(line) - scrollY + paddingTop
            if (baseline > top + height + lineHeight() || baseline < top - lineHeight()) continue
            val lineStart = gl.getLineStart(line)
            val lineEnd = gl.getLineEnd(line)
            val lineText = text.substring(lineStart.coerceIn(0, text.length), lineEnd.coerceIn(0, text.length))
            if (f.indentGuide) drawIndentGuides(canvas, gl, line, lineText, paint)
            if (!f.spacesAndTabs && !f.endOfLine && !f.nonPrinting && !f.controlChars && !f.wrapSymbol) continue
            var xOff = 0f
            for (i in lineText.indices) {
                val c = lineText[i]
                val x = gl.getPrimaryHorizontal(lineStart + i) - scrollX
                when {
                    f.spacesAndTabs && c == ' ' -> {
                        canvas.drawText(ShowSymbol.SPACE_GLYPH, x, baseline.toFloat(), paint)
                    }
                    f.spacesAndTabs && c == '\t' -> {
                        canvas.drawText(ShowSymbol.TAB_GLYPH, x, baseline.toFloat(), paint)
                    }
                    f.nonPrinting && ShowSymbol.isNonPrinting(c) -> {
                        canvas.drawText(ShowSymbol.nonPrintingAbbreviation(c), x, baseline.toFloat(), paint)
                    }
                    f.controlChars && (c.code in 0..31 || c.code == 127) && c != '\n' -> {
                        canvas.drawText(ShowSymbol.abbreviationFor(c.code) ?: "?", x, baseline.toFloat(), paint)
                    }
                }
            }
            // EOL marker at the end of the line (LF for wrapped lines only when wrap is off)
            if (f.endOfLine && lineEnd < text.length && text.getOrNull(lineEnd - 1) != '\n') {
                val x = gl.getPrimaryHorizontal(lineEnd) - scrollX
                canvas.drawText(ShowSymbol.EOL_LF, x, baseline.toFloat(), paint)
            }
            if (f.wrapSymbol && gl.getLineVisibleEnd(line) < lineEnd) {
                val x = gl.getPrimaryHorizontal(gl.getLineVisibleEnd(line)) - scrollX
                canvas.drawText(ShowSymbol.WRAP_GLYPH, x - paint.measureText(ShowSymbol.WRAP_GLYPH), baseline.toFloat(), paint)
            }
        }
    }

    private fun drawIndentGuides(canvas: Canvas, gl: Layout, line: Int, lineText: String, paint: Paint) {
        if (!lineText.startsWith(" ") && !lineText.startsWith("\t")) return
        val indent = ShowSymbol.indentWidth(lineText, tabSize)
        val baseline = gl.getLineBaseline(line) - scrollY + paddingTop
        val charW = paint.measureText("8")
        paint.style = Paint.Style.STROKE
        val path = android.graphics.Path()
        var col = tabSize
        while (col < indent) {
            val x = gl.getPrimaryHorizontal(gl.getLineStart(line)) - scrollX + col * charW
            path.moveTo(x, (baseline - lineHeight()).toFloat())
            path.lineTo(x, baseline.toFloat())
            col += tabSize
        }
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawVerticalEdge(canvas: Canvas) {
        if (!showVerticalEdge || edgeColumns.isEmpty()) return
        val charWidth = paint.measureText("8")
        if (edgeBackgroundMode) {
            val x = paddingLeft + (edgeColumns.min() * charWidth) - scrollX
            edgeShadePaint.color = if (darkTheme) 0x22FFFFFF else 0x11000000
            canvas.drawRect(x, 0f, (scrollX + width).toFloat(), height.toFloat(), edgeShadePaint)
        } else {
            edgePaint.color = if (darkTheme) 0x55FFFFFF.toInt() else 0x33000000
            edgeColumns.forEach { col ->
                val x = paddingLeft + (col * charWidth) - scrollX
                if (x >= 0 && x <= scrollX + width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), edgePaint)
                }
            }
        }
    }

    private fun drawBracketRect(canvas: Canvas, gl: Layout, line: Int, offset: Int) {
        val top = gl.getLineTop(line) + paddingTop - scrollY
        val bottom = gl.getLineBottom(line) + paddingTop - scrollY
        val startx = gl.getPrimaryHorizontal(offset) - scrollX
        val endx = gl.getPrimaryHorizontal(minOf(offset + 1, gl.text.length)) - scrollX
        if (bottom < 0 || top > height) return
        canvas.drawRect(startx, top.toFloat(), endx, bottom.toFloat(), bracketPaint)
    }
}
