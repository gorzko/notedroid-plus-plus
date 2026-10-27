package com.notedroidplusplus.app.editing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Layout
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import androidx.appcompat.widget.AppCompatEditText
import com.notedroidplusplus.app.LanguageMode
import com.notedroidplusplus.app.TokenKind

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
                updateGutter()
                rehighlight()
            }
        })
        viewTreeObserver.addOnScrollChangedListener { invalidate() }
        post { updateGutter() }
    }

    fun applyTheme(dark: Boolean) {
        darkTheme = dark
        val res = resources
        fun c(name: String): Int =
            res.getIdentifier(name, "color", context.packageName).let { if (it != 0) res.getColor(it) else 0xFF000000.toInt() }
        colors = mapOf(
            TokenKind.KEYWORD to c(if (dark) "syn_keyword_dark" else "syn_keyword"),
            TokenKind.TYPE to c(if (dark) "syn_type_dark" else "syn_type"),
            TokenKind.NUMBER to c(if (dark) "syn_number_dark" else "syn_number"),
            TokenKind.STRING to c(if (dark) "syn_string_dark" else "syn_string"),
            TokenKind.COMMENT to c(if (dark) "syn_comment_dark" else "syn_comment"),
            TokenKind.OPERATOR to c(if (dark) "syn_operator_dark" else "syn_operator"),
            TokenKind.PREPROCESSOR to c(if (dark) "syn_preproc_dark" else "syn_preproc"),
            TokenKind.TAG to c(if (dark) "syn_tag_dark" else "syn_tag"),
            TokenKind.ATTRIBUTE to c(if (dark) "syn_attr_dark" else "syn_attr"),
            TokenKind.VALUE to c(if (dark) "syn_value_dark" else "syn_value"),
            TokenKind.HEADING to c(if (dark) "syn_heading_dark" else "syn_heading"),
            TokenKind.LINK to c(if (dark) "syn_link_dark" else "syn_link"),
            TokenKind.ESCAPE to c(if (dark) "syn_escape_dark" else "syn_escape"),
        )
        gutterTextColor = if (dark) 0xFF8A8A8A.toInt() else 0xFF6E6E6E.toInt()
        gutterBgColor = if (dark) 0xFF1F1F1F.toInt() else 0xFFEBEBEB.toInt()
        highlightColor = if (dark) 0xFF2A2A40.toInt() else 0xFFE8E8FF.toInt()
        bracketColor = if (dark) 0xFFAAAAAA.toInt() else 0xFF808080.toInt()
        setBackgroundColor(if (dark) 0xFF262626.toInt() else 0xFFFFFFFF.toInt())
        setTextColor(if (dark) 0xFFE0E0E0.toInt() else 0xFF101010.toInt())
        gutterPaint.textSize = paint.textSize * 0.85f
        updateGutter()
        rehighlight()
    }

    private fun updateGutter() {
        val paint = paint
        gutterPaint.textSize = paint.textSize * 0.85f
        if (showLineNumbers) {
            val lines = (editableText?.toString()?.count { it == '\n' } ?: 0) + 1
            val digits = maxOf(3, lines.toString().length)
            val w = (digits * paint.measureText("8") * 0.9f).toInt() + 16
            if (w != gutterWidth) gutterWidth = w
            setPadding(gutterWidth + 10, paddingTop, paddingRight, paddingBottom)
        } else {
            gutterWidth = 0
            setPadding(10, paddingTop, paddingRight, paddingBottom)
        }
        invalidate()
    }

    fun rehighlight() {
        val e = editableText ?: return
        e.getSpans(0, e.length, SyntaxSpan::class.java).forEach { e.removeSpan(it) }
        if (colors.isNotEmpty()) {
            SyntaxHighlighter.tokenize(e.toString(), language).forEach { tok ->
                val color = colors[tok.kind] ?: return@forEach
                e.setSpan(SyntaxSpan(color), tok.start, tok.end, 0)
            }
        }
        computeBracket()
        invalidate()
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
                val baseline = gl.getLineBaseline(line) + gl.getLineTop(line) - scrollY + paddingTop
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

        val sel = selectionStart
        if (sel >= 0) {
            val line = gl.getLineForOffset(sel)
            val rectTop = gl.getLineTop(line) + paddingTop - scrollY
            val rectBottom = gl.getLineBottom(line) + paddingTop - scrollY
            if (rectBottom >= 0 && rectTop <= height) {
                highlightPaint.color = highlightColor
                canvas.drawRect(
                    scrollX.toFloat(), rectTop.toFloat(), (scrollX + width).toFloat(),
                    rectBottom.toFloat(), highlightPaint
                )
            }
        }

        bracketPair?.let { (s, e) ->
            bracketPaint.color = bracketColor
            drawBracketRect(canvas, gl, gl.getLineForOffset(s), s)
            drawBracketRect(canvas, gl, gl.getLineForOffset(e), e)
        }

        super.onDraw(canvas)
    }

    private fun lineHeight(): Int = (paint.descent - paint.ascent).toInt()

    private fun drawBracketRect(canvas: Canvas, gl: Layout, line: Int, offset: Int) {
        val top = gl.getLineTop(line) + paddingTop - scrollY
        val bottom = gl.getLineBottom(line) + paddingTop - scrollY
        val padLeft = if (showLineNumbers) gutterWidth + 10 else 10
        val startx = gl.getPrimaryHorizontal(offset) + padLeft - compoundPaddingLeft - scrollX
        val endx = gl.getPrimaryHorizontal(minOf(offset + 1, gl.text.length)) + padLeft - compoundPaddingLeft - scrollX
        if (bottom < 0 || top > height) return
        canvas.drawRect(startx, top.toFloat(), endx, bottom.toFloat(), bracketPaint)
    }
}
