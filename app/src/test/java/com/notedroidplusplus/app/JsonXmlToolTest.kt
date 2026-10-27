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
}
