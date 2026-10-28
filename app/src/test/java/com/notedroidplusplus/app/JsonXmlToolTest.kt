package com.notedroidplusplus.app

import com.notedroidplusplus.app.tools.JsonTool
import com.notedroidplusplus.app.tools.XmlTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonXmlToolTest {

    @Test
    fun jsonPrettify() {
        val result = JsonTool.prettify("""{"a":1,"b":[true,null]}""").getOrThrow()
        assertTrue(result.contains("\"a\": 1"))
        assertTrue(result.lines().size > 1)
    }

    @Test
    fun jsonMinify() {
        val pretty = "{\n  \"a\": 1\n}"
        assertEquals("{\"a\":1}", JsonTool.minify(pretty).getOrThrow())
    }

    @Test
    fun jsonInvalidFails() {
        assertTrue(JsonTool.prettify("{invalid}").isFailure)
        assertTrue(JsonTool.minify("[1,").isFailure)
    }

    @Test
    fun xmlPrettifyIndents() {
        val result = XmlTool.prettify("<a><b>1</b></a>").getOrThrow()
        assertTrue(result.contains("\n"))
        assertTrue(result.contains("<b>"))
    }

    @Test
    fun xmlMinifyRemovesWhitespace() {
        val min = XmlTool.minify("<a>\n  <b>1</b>\n</a>").getOrThrow()
        assertEquals("<a><b>1</b></a>", min.replace("""\s*<\?xml[^>]*>\s*""".toRegex(), "").trim())
    }

    @Test
    fun xmlValidate() {
        assertTrue(XmlTool.validate("<a><b/></a>").isSuccess)
        assertTrue(XmlTool.validate("<a><b></a>").isFailure)
    }

    @Test
    fun jsonLargeIntegerPrecisionPreserved() {
        val src = "{\"id\": 12345678901234567890}"
        val prettied = JsonTool.prettify(src).getOrThrow()
        assertTrue(prettied.contains("12345678901234567890"))
        assertEquals(src, JsonTool.minify(prettied).getOrThrow())
        assertEquals(src, JsonTool.minify(src).getOrThrow())
        assertEquals("{\"a\":1.5}", JsonTool.minify("{\"a\": 1.5}").getOrThrow())
    }

    @Test
    fun jsonRoundTripPrettifyMinifyPreservesNumbers() {
        val src = "{\"snowflake\":1759288472932874111,\"neg\":-9007199254740993,\"float\":3.14e2}"
        val pretty = JsonTool.prettify(src).getOrThrow()
        assertEquals(src, JsonTool.minify(pretty).getOrThrow())
    }
}
