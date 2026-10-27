package com.notedroidplusplus.app.tools

/**
 * XML pretty-printer / minifier and well-formedness checker implemented with the
 * javax.xml.transform pipeline (provided on both JVM and Android).
 * The parser is hardened against XXE: DOCTYPE declarations and external entities
 * are disabled, since the app opens arbitrary user files.
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
        dbf.isXIncludeAware = false
        dbf.isExpandEntityReferences = false
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false)
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        dbf.setFeature("http://xml.org/sax/features/external-dtd", false)
        dbf.setFeature("http://xml.org/sax/features/validation", false)
        return dbf.newDocumentBuilder().parse(input.byteInputStream())
    }

    private fun newTransformer(indent: Int): javax.xml.transform.Transformer {
        val factory = javax.xml.transform.TransformerFactory.newInstance()
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
        val transformer = factory.newTransformer()
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes")
        if (indent > 0) {
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", indent.toString())
        }
        return transformer
    }

    private fun collapseWhitespace(s: String): String {
        val re = Regex(">" + "\\" + "s+<")
        return s.replace(re, "><").trim()
    }
}
