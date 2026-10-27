package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.Document
import com.notedroidplusplus.app.tools.XmlTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRegressionTest {

    @Test
    fun documentDefaultLineEndingIsRealNewline() {
        val doc = Document(uri = null, fileName = "x", text = "a\nb", language = LanguageMode.NORMAL_TEXT)
        assertEquals("\n", doc.lineEnding)
        assertEquals(2, doc.lineCount)
    }

    @Test
    fun xmlDoctypeIsRejected() {
        val xxe = """<!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><root>&xxe;</root>"""
        assertTrue(XmlTool.validate(xxe).isFailure)
        assertTrue(XmlTool.prettify(xxe).isFailure)
    }

    @Test
    fun xmlWellFormedStillParses() {
        assertTrue(XmlTool.validate("<a><b>1</b></a>").isSuccess)
    }
}
