package com.notedroidplusplus.app.core

/**
 * Serializable macro action log (Notepad++ Macro menu parity).
 * Recording captures typed events at the existing edit/transform entry points;
 * replay is a pure function over a text buffer so it is unit-testable.
 */
object MacroRecorder {

    sealed class Action {
        data class Insert(val offset: Int, val text: String) : Action()
        data class Delete(val start: Int, val end: Int) : Action()
        data class ReplaceAll(val find: String, val replace: String, val regex: Boolean, val matchCase: Boolean) : Action()
        data class TransformCase(val kind: String, val start: Int, val end: Int) : Action()
        data class GoToLine(val line: Int) : Action()
    }

    /** Minimal text buffer the pure replay operates on. */
    interface Buffer {
        val text: String
        val selection: Int
        fun insert(offset: Int, s: String)
        fun delete(start: Int, end: Int)
        fun select(offset: Int)
    }

    class StringBuffer(initial: String = "") : Buffer {
        private val sb = StringBuilder(initial)
        private var sel = initial.length
        override val text: String get() = sb.toString()
        override val selection: Int get() = sel
        override fun insert(offset: Int, s: String) {
            sb.insert(offset.coerceIn(0, sb.length), s)
        }

        override fun delete(start: Int, end: Int) {
            val s = start.coerceIn(0, sb.length)
            val e = end.coerceIn(s, sb.length)
            sb.delete(s, e)
        }

        override fun select(offset: Int) {
            sel = offset
        }
    }

    /** Applies one [Action] to [buffer]; returns false when the action is not applicable. */
    fun apply(buffer: Buffer, action: Action): Boolean {
        return when (action) {
            is Action.Insert -> {
                if (action.offset < 0 || action.offset > buffer.text.length) return false
                buffer.insert(action.offset, action.text)
                buffer.select(action.offset + action.text.length)
                true
            }
            is Action.Delete -> {
                if (action.start < 0 || action.end > buffer.text.length || action.start > action.end) return false
                buffer.delete(action.start, action.end)
                buffer.select(action.start)
                true
            }
            is Action.ReplaceAll -> {
                val opts = if (action.regex) {
                    if (action.matchCase) Regex(action.find) else Regex(action.find, RegexOption.IGNORE_CASE)
                } else {
                    if (action.matchCase) Regex.escape(action.find).toRegex()
                    else Regex(Regex.escape(action.find), RegexOption.IGNORE_CASE)
                }
                val replaced = if (action.regex) buffer.text.replace(opts) { m ->
                    expandRefs(action.replace, m)
                } else buffer.text.replace(opts) { action.replace }
                if (replaced == buffer.text) return false
                val keep = buffer.selection.coerceAtMost(replaced.length)
                buffer.delete(0, buffer.text.length)
                buffer.insert(0, replaced)
                buffer.select(keep)
                true
            }
            is Action.TransformCase -> {
                val text = buffer.text
                val s = action.start.coerceIn(0, text.length)
                val e = action.end.coerceIn(s, text.length)
                val transformed = when (action.kind) {
                    "upper" -> text.substring(s, e).uppercase()
                    "lower" -> text.substring(s, e).lowercase()
                    "title" -> text.substring(s, e).split(" ").joinToString(" ") { w ->
                        w.lowercase().replaceFirstChar { it.uppercase() }
                    }
                    "toggle" -> text.substring(s, e).map { if (it.isUpperCase()) it.lowercaseChar() else it.uppercaseChar() }.joinToString("")
                    "proper" -> TextUtils.properCase(text, s, e, blend = false)
                    "proper-blend" -> TextUtils.properCase(text, s, e, blend = true)
                    "sentence" -> TextUtils.sentenceCase(text, s, e, blend = false)
                    "sentence-blend" -> TextUtils.sentenceCase(text, s, e, blend = true)
                    else -> return false
                }
                buffer.delete(s, e)
                buffer.insert(s, transformed)
                buffer.select(s + transformed.length)
                true
            }
            is Action.GoToLine -> {
                var idx = 0
                var line = 1
                while (line < action.line) {
                    val next = buffer.text.indexOf('\n', idx)
                    if (next < 0) { idx = buffer.text.length; break }
                    idx = next + 1
                    line++
                }
                val target = if (action.line <= 1) 0 else idx
                buffer.select(target.coerceIn(0, buffer.text.length))
                true
            }
        }
    }

    /** Expands $1..$9 and $0 in a regex replacement string. */
    fun expandRefs(replacement: String, m: MatchResult): String {
        val sb = StringBuilder()
        var i = 0
        while (i < replacement.length) {
            val c = replacement[i]
            if (c == '$' && i + 1 < replacement.length && replacement[i + 1].isDigit()) {
                val idx = replacement[i + 1] - '0'
                val group = m.groupValues.getOrNull(idx)
                if (group != null) sb.append(group)
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** Replays a whole action log; returns the number of applied actions. */
    fun replay(buffer: Buffer, actions: List<Action>): Int {
        var applied = 0
        for (a in actions) if (apply(buffer, a)) applied++
        return applied
    }
}
