package com.notedroidplusplus.app.tools

/**
 * XML pretty-printer / minifier and well-formedness checker implemented with the
 * Android-provided javax.xml.transform pipeline. Pure JVM-testable via unit tests.
 */
object XmlTool {

    fun prettify(input: String, indent: Int = 4): Result<String> = transform(input, indent)

    fun minify(input: String): Result<String> = transform(input, 0)

    fun validate(input: String): Result<Unit> {
        input.byteInputStream().use { stream ->
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.newDocumentBuilder().parse(stream)
        }
        return Result.success(Unit)
    }

    private fun transform(input: String, indent: Int): Result<String> = runCatching {
        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        val doc = dbf.newDocumentBuilder().parse(input.byteInputStream())

        val factory = javax.xml.transform.TransformerFactory.newInstance()
        val transformer = factory.newTransformer()
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes")
        if (indent > 0) {
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indent.toString())
        }
        val out = java.io.StringWriter()
        transformer.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(out))
        val s = out.toString()
        if (indent == 0) collapseWhitespace(s) else s.trimEnd() + "\n"
    }

    private fun collapseWhitespace(s: String): String =
        s.replace(">\\s+<".toRegex(), "><").trim()
}
