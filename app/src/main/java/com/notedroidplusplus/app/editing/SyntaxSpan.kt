package com.notedroidplusplus.app.editing

import android.text.style.ForegroundColorSpan

/** Colored span applied by the highlighter; carries the syntax color directly. */
class SyntaxSpan(val color: Int) : ForegroundColorSpan(color)
