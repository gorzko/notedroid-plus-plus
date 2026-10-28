package com.notedroidplusplus.app

import com.notedroidplusplus.app.tools.HashTool
import com.notedroidplusplus.app.tools.HashTool.Algorithm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class HashToolTest {

    @Test
    fun known_vectors_abc() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72",
            HashTool.hashText(Algorithm.MD5, "abc"))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
            HashTool.hashText(Algorithm.SHA1, "abc"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashTool.hashText(Algorithm.SHA256, "abc"))
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            HashTool.hashText(Algorithm.SHA512, "abc"))
    }

    @Test
    fun known_vector_empty() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", HashTool.hashText(Algorithm.MD5, ""))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            HashTool.hashText(Algorithm.SHA256, ""))
    }

    @Test
    fun stream_matches_direct_hash() {
        val big = buildString { repeat(1000) { append("NoteDroid++ hashing test; ") } }
        val bytes = big.toByteArray(Charsets.UTF_8)
        for (alg in Algorithm.entries) {
            assertEquals(HashTool.hashBytes(alg, bytes),
                HashTool.hashStream(alg, ByteArrayInputStream(bytes)))
        }
    }

    @Test
    fun hex_formatting_is_lowercase_padded() {
        assertEquals("01", HashTool.hex(byteArrayOf(0x01)))
        assertEquals("0f", HashTool.hex(byteArrayOf(0x0F)))
    }
}
