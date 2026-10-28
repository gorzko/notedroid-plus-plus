package com.notedroidplusplus.app

import com.notedroidplusplus.app.editing.SyntaxHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private fun kinds(text: String, mode: LanguageMode): List<TokenKind> =
        SyntaxHighlighter.tokenize(text, mode).map { it.kind }

    @Test
    fun kotlinKeywordsAndTypes() {
        val text = "val x: Int = 42"
        val ks = kinds(text, LanguageMode.KOTLIN)
        assertTrue(ks.contains(TokenKind.KEYWORD)) // val
        assertTrue(ks.contains(TokenKind.TYPE))    // Int
        assertTrue(ks.contains(TokenKind.NUMBER))  // 42
    }

    @Test
    fun javaBlockComment() {
        val text = "int a; /* hidden */ int b;"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.JAVA)
        val comment = toks.first { it.kind == TokenKind.COMMENT }
        assertEquals("/* hidden */", text.substring(comment.start, comment.end))
    }

    @Test
    fun pythonHashComment() {
        val text = "x = 1 # note"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.PYTHON)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
    }

    @Test
    fun jsonStringsKeysNumbers() {
        val text = """{"a": 1, "b": [true, null, "x"]}"""
        val ks = kinds(text, LanguageMode.JSON)
        assertTrue(ks.contains(TokenKind.ATTRIBUTE))
        assertTrue(ks.contains(TokenKind.NUMBER))
        assertTrue(ks.contains(TokenKind.KEYWORD))
        assertTrue(ks.contains(TokenKind.STRING))
    }

    @Test
    fun htmlTagsAndAttributes() {
        val text = "<div class=\"x\">hi</div>"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.HTML)
        assertTrue(toks.any { it.kind == TokenKind.TAG })
        assertTrue(toks.any { it.kind == TokenKind.ATTRIBUTE })
        assertTrue(toks.any { it.kind == TokenKind.VALUE })
    }

    @Test
    fun xmlComment() {
        val text = "<root><!-- c --><a/></root>"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.XML)
        val comment = toks.first { it.kind == TokenKind.COMMENT }
        assertEquals("<!-- c -->", text.substring(comment.start, comment.end))
    }

    @Test
    fun markdownHeadingAndLink() {
        val text = "# Title\n[link](http://x)"
        val ks = kinds(text, LanguageMode.MARKDOWN)
        assertTrue(ks.contains(TokenKind.HEADING))
        assertTrue(ks.contains(TokenKind.LINK))
    }

    @Test
    fun languageFromFileName() {
        assertEquals(LanguageMode.JSON, LanguageMode.fromFileName("a.json"))
        assertEquals(LanguageMode.MARKDOWN, LanguageMode.fromFileName("README.MD"))
        assertEquals(LanguageMode.MAKEFILE, LanguageMode.fromFileName("Makefile"))
        assertEquals(LanguageMode.NORMAL_TEXT, LanguageMode.fromFileName("noext"))
    }

    @Test
    fun sqlCaseInsensitive() {
        val ks = kinds("SELECT * FROM t", LanguageMode.SQL)
        assertTrue(ks.contains(TokenKind.KEYWORD))
    }

    @Test
    fun emptyTextYieldsNoTokens() {
        assertTrue(SyntaxHighlighter.tokenize("", LanguageMode.JAVA).isEmpty())
    }
}

    // ---- New languages (#32) ----

    @Test
    fun tomlHighlight() {
        val text = "# comment\n[section]\nkey = \"value\"\nn = 42\nflag = true"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.TOML)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.STRING })
        assertTrue(toks.any { it.kind == TokenKind.NUMBER })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
    }

    @Test
    fun powershellHighlight() {
        val text = "# comment\nif (\$x -gt 1) { Write-Host \"hi\" }"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.POWERSHELL)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.STRING })
    }

    @Test
    fun perlHighlight() {
        val text = "# comment\nmy \$x = 42;\nsub foo { return \"bar\"; }"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.PERL)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.STRING })
        assertTrue(toks.any { it.kind == TokenKind.NUMBER })
    }

    @Test
    fun cmakeHighlight() {
        val text = "# comment\nif(WIN32)\n  set(FOO \"bar\")\nendif()"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.CMAKE)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.STRING })
    }

    @Test
    fun diffHighlight() {
        val text = "diff --git a/f b/f\n@@ -1,2 +1,2 @@\n-old\n+new\n context"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.DIFF)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })   // diff header
        assertTrue(toks.any { it.kind == TokenKind.HEADING })   // hunk header
        assertTrue(toks.any { it.kind == TokenKind.STRING })    // +line
        assertTrue(toks.any { it.kind == TokenKind.ATTRIBUTE }) // -line
    }

    @Test
    fun vhdlAndVerilogHighlight() {
        val vhdl = "-- comment\nif clk = '1' then\n  x <= \"0101\";\nend if;"
        val toks = SyntaxHighlighter.tokenize(vhdl, LanguageMode.VHDL)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })  // if / then / end
        assertTrue(toks.any { it.kind == TokenKind.STRING })

        val v = "// comment\nmodule top(input clk);\nalways @(posedge clk) begin end\nendmodule"
        val vtoks = SyntaxHighlighter.tokenize(v, LanguageMode.VERILOG)
        assertTrue(vtoks.any { it.kind == TokenKind.COMMENT })
        assertTrue(vtoks.any { it.kind == TokenKind.KEYWORD })
    }

    @Test
    fun pascalAndAdaHighlight() {
        val pas = "{ comment }\nprogram Hello;\nbegin\n  writeln('Hi');\nend."
        val toks = SyntaxHighlighter.tokenize(pas, LanguageMode.PASCAL)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.STRING })

        val ada = "-- comment\nprocedure Main is\nbegin\n   null;\nend Main;"
        val atoks = SyntaxHighlighter.tokenize(ada, LanguageMode.ADA)
        assertTrue(atoks.any { it.kind == TokenKind.COMMENT })
        assertTrue(atoks.any { it.kind == TokenKind.KEYWORD })
    }

    @Test
    fun fortranAndErlangHighlight() {
        val f = "! comment\nprogram hello\n  integer :: x = 42\nend program"
        val toks = SyntaxHighlighter.tokenize(f, LanguageMode.FORTRAN)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.NUMBER })

        val e = "% comment\nreceive\n  X -> ok\nend."
        val etoks = SyntaxHighlighter.tokenize(e, LanguageMode.ERLANG)
        assertTrue(etoks.any { it.kind == TokenKind.COMMENT })
        assertTrue(etoks.any { it.kind == TokenKind.KEYWORD })
    }

    @Test
    fun haskellHighlight() {
        val text = "-- comment\nmodule M where\nf :: Int -> Int\nf x = 42"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.HASKELL)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.NUMBER })
    }

    @Test
    fun rAndTclHighlight() {
        val r = "# comment\nx <- c(1, 2)\nif (x > 1) { print(\"a\") }"
        val toks = SyntaxHighlighter.tokenize(r, LanguageMode.R)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.STRING })

        val t = "# comment\nproc foo {x} { puts \"hi \$x\" }"
        val ttoks = SyntaxHighlighter.tokenize(t, LanguageMode.TCL)
        assertTrue(ttoks.any { it.kind == TokenKind.COMMENT })
        assertTrue(ttoks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(ttoks.any { it.kind == TokenKind.STRING })
    }

    @Test
    fun matlabHighlight() {
        val text = "% comment\nfunction y = f(x)\n  if x > 1\n    y = 2;\n  end"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.MATLAB)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.NUMBER })
    }

    @Test
    fun objectiveCHighlight() {
        val text = "// comment\n@interface Foo : NSObject\n@end\nNSString *s = @\"hi\";"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.OBJECTIVE_C)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.STRING })
    }

    @Test
    fun visualBasicHighlight() {
        val text = "' comment\nModule M\n  Sub Main()\n    Dim x As Integer = 42\n  End Sub\nEnd Module"
        val toks = SyntaxHighlighter.tokenize(text, LanguageMode.VISUALBASIC)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(toks.any { it.kind == TokenKind.NUMBER })
    }

    @Test
    fun lispAndSchemeHighlight() {
        val l = "; comment\n(defun foo (x) (+ x 1))"
        val toks = SyntaxHighlighter.tokenize(l, LanguageMode.LISP)
        assertTrue(toks.any { it.kind == TokenKind.COMMENT })
        assertTrue(toks.any { it.kind == TokenKind.KEYWORD })

        val s = "; comment\n(define (foo x) (* x 2))"
        val stoks = SyntaxHighlighter.tokenize(s, LanguageMode.SCHEME)
        assertTrue(stoks.any { it.kind == TokenKind.COMMENT })
        assertTrue(stoks.any { it.kind == TokenKind.KEYWORD })
    }

    @Test
    fun dAndNimHighlight() {
        val d = "// comment\nvoid main() { auto x = 42; }"
        val dtoks = SyntaxHighlighter.tokenize(d, LanguageMode.D)
        assertTrue(dtoks.any { it.kind == TokenKind.COMMENT })
        assertTrue(dtoks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(dtoks.any { it.kind == TokenKind.NUMBER })

        val nim = "# comment\nproc main() =\n  let x = 42"
        val ntoks = SyntaxHighlighter.tokenize(nim, LanguageMode.NIM)
        assertTrue(ntoks.any { it.kind == TokenKind.COMMENT })
        assertTrue(ntoks.any { it.kind == TokenKind.KEYWORD })
        assertTrue(ntoks.any { it.kind == TokenKind.NUMBER })
    }

    @Test
    fun newLanguageAutoDetect() {
        assertEquals(LanguageMode.TOML, LanguageMode.fromFileName("config.toml"))
        assertEquals(LanguageMode.POWERSHELL, LanguageMode.fromFileName("script.ps1"))
        assertEquals(LanguageMode.PERL, LanguageMode.fromFileName("tool.pl"))
        assertEquals(LanguageMode.CMAKE, LanguageMode.fromFileName("CMakeLists.txt"))
        assertEquals(LanguageMode.DIFF, LanguageMode.fromFileName("change.patch"))
        assertEquals(LanguageMode.VHDL, LanguageMode.fromFileName("top.vhd"))
        assertEquals(LanguageMode.VERILOG, LanguageMode.fromFileName("rtl.sv"))
        assertEquals(LanguageMode.HASKELL, LanguageMode.fromFileName("Main.hs"))
        assertEquals(LanguageMode.R, LanguageMode.fromFileName("plot.r"))
        assertEquals(LanguageMode.MATLAB, LanguageMode.fromFileName("run.m"))
        assertEquals(LanguageMode.OBJECTIVE_C, LanguageMode.fromFileName("iOS.mm"))
        assertEquals(LanguageMode.VISUALBASIC, LanguageMode.fromFileName("Form1.vb"))
        assertEquals(LanguageMode.COBOL, LanguageMode.fromFileName("prog.cbl"))
        assertEquals(LanguageMode.NORMAL_TEXT, LanguageMode.fromFileName("readme.txt"))
    }
