package com.notedroidplusplus.app.tools

/**
 * XML pretty-printer / minifier and well-formedness checker implemented with the
 * javax.xml.transform pipeline (provided on both JVM and Android).
 */
object XmlTool {

    fun prettify(input: String, indent: Int = 4): Result<String> = runCatching {
        val doc = parse(input)
        val transformer = newTransformer(indent)
        val out = java.io.StringWriter()
        transformer.transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(out))
        val s = out.toString()
        if (indent == 0) collapseWhitespace(s) else s.trimEnd() + "\n"
    }

    fun minify(input: String): Result<String> = prettify(input, 0)

    fun validate(input: String): Result<Unit> = runCatching {
        parse(input)
        Unit
    }

    private fun parse(input: String): org.w3c.dom.Document {
        val dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        return dbf.newDocumentBuilder().parse(input.byteInputStream())
    }

    private fun newTransformer(indent: Int): javax.xml.transform.Transformer {
        val factory = javax.xml.transform.TransformerFactory.newInstance()
        val transformer = factory.newTransformer()
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes")
        if (indent > 0) {
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indent.toString())
        }
        return transformer
    }

    private fun collapseWhitespace(s: String): String =
        s.replace(">\s+<".toRegex(), "><").trim()
}
