package com.notedroidplusplus.app.tools

/**
 * Minimal self-contained JSON parser/serializer.
 * Written from scratch so it runs identically on JVM (unit tests) and Android,
 * unlike org.json which is an Android stub in local unit tests.
 */
object Json {

    sealed class Value {
        data class Obj(val entries: List<Pair<String, Value>>) : Value()
        data class Arr(val items: List<Value>) : Value()
        data class Str(val s: String) : Value()
        data class Num(val n: Double) : Value()
        data class Bool(val b: Boolean) : Value()
        object Null : Value()
    }

    fun parse(text: String): Value {
        val p = Parser(text)
        val v = p.parseValue()
        p.skipWs()
        if (!p.atEnd()) throw IllegalArgumentException("Trailing characters at ${p.pos}")
        return v
    }

    fun stringify(v: Value, indent: Int = 0): String {
        val sb = StringBuilder()
        write(v, sb, indent, 0)
        return sb.toString()
    }

    private fun write(v: Value, sb: StringBuilder, indent: Int, depth: Int) {
        when (v) {
            is Value.Obj -> {
                if (v.entries.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                v.entries.forEachIndexed { i, (k, vv) ->
                    if (i > 0) sb.append(',')
                    newline(sb, indent, depth + 1)
                    sb.append(quote(k)).append(':')
                    if (indent > 0) sb.append(' ')
                    write(vv, sb, indent, depth + 1)
                }
                newline(sb, indent, depth)
                sb.append('}')
            }
            is Value.Arr -> {
                if (v.items.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                v.items.forEachIndexed { i, vv ->
                    if (i > 0) sb.append(',')
                    newline(sb, indent, depth + 1)
                    write(vv, sb, indent, depth + 1)
                }
                newline(sb, indent, depth)
                sb.append(']')
            }
            is Value.Str -> sb.append(quote(v.s))
            is Value.Num -> sb.append(
                if (v.n == Math.floor(v.n) && !v.n.isInfinite() && Math.abs(v.n) < 1e15) v.n.toLong().toString() else v.n.toString()
            )
            is Value.Bool -> sb.append(if (v.b) "true" else "false")
            Value.Null -> sb.append("null")
        }
    }

    private fun newline(sb: StringBuilder, indent: Int, depth: Int) {
        if (indent > 0) {
            sb.append('\n')
            repeat(indent * depth) { sb.append(' ') }
        }
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class Parser(val text: String) {
        var pos = 0

        fun atEnd() = pos >= text.length

        fun skipWs() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun parseValue(): Value {
            skipWs()
            if (atEnd()) throw IllegalArgumentException("Unexpected end of input")
            return when (val c = text[pos]) {
                '{' -> parseObj()
                '[' -> parseArr()
                '"' -> Value.Str(parseString())
                't' -> { expect("true"); Value.Bool(true) }
                'f' -> { expect("false"); Value.Bool(false) }
                'n' -> { expect("null"); Value.Null }
                else -> if (c == '-' || c.isDigit()) parseNumber() else throw IllegalArgumentException("Unexpected character '$c' at $pos")
            }
        }

        fun expect(word: String) {
            if (!text.startsWith(word, pos)) throw IllegalArgumentException("Expected $word at $pos")
            pos += word.length
        }

        fun parseObj(): Value.Obj {
            pos++ // {
            val entries = mutableListOf<Pair<String, Value>>()
            skipWs()
            if (pos < text.length && text[pos] == '}') { pos++; return Value.Obj(entries) }
            while (true) {
                skipWs()
                if (atEnd() || text[pos] != '"') throw IllegalArgumentException("Expected key at $pos")
                val key = parseString()
                skipWs()
                if (atEnd() || text[pos] != ':') throw IllegalArgumentException("Expected ':' at $pos")
                pos++
                entries.add(key to parseValue())
                skipWs()
                if (atEnd()) throw IllegalArgumentException("Unterminated object")
                when (text[pos]) {
                    ',' -> pos++
                    '}' -> { pos++; return Value.Obj(entries) }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
        }

        fun parseArr(): Value.Arr {
            pos++ // [
            val items = mutableListOf<Value>()
            skipWs()
            if (pos < text.length && text[pos] == ']') { pos++; return Value.Arr(items) }
            while (true) {
                items.add(parseValue())
                skipWs()
                if (atEnd()) throw IllegalArgumentException("Unterminated array")
                when (text[pos]) {
                    ',' -> pos++
                    ']' -> { pos++; return Value.Arr(items) }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
        }

        fun parseString(): String {
            pos++ // "
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw IllegalArgumentException("Unterminated string")
                when (val c = text[pos]) {
                    '"' -> { pos++; return sb.toString() }
                    '\\' -> {
                        pos++
                        when (val e = text.getOrNull(pos)) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('')
                            'u' -> {
                                val hex = text.substring(pos + 1, minOf(pos + 5, text.length))
                                sb.append(hex.toIntOrNull(16)?.toChar() ?: throw IllegalArgumentException("Bad unicode escape"))
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape '\\$e' at $pos")
                        }
                        pos++
                    }
                    else -> { sb.append(c); pos++ }
                }
            }
        }

        fun parseNumber(): Value.Num {
            val start = pos
            if (text.getOrNull(pos) == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.' || text[pos] == 'e' || text[pos] == 'E' || text[pos] == '+' || text[pos] == '-')) pos++
            val s = text.substring(start, pos)
            return Value.Num(s.toDoubleOrNull() ?: throw IllegalArgumentException("Bad number '$s'"))
        }
    }
}
