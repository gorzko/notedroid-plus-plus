package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.UdlExportQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UdlExportQueueTest {

    @Test
    fun beginRegistersPayloadAndPending() {
        val q = UdlExportQueue()
        assertFalse(q.pending)
        q.begin("KSL")
        assertTrue(q.pending)
    }

    @Test
    fun consumeLatestReturnsAndClearsPayload() {
        val q = UdlExportQueue()
        q.begin("KSL")
        assertEquals("KSL", q.consumeLatest())
        assertFalse(q.pending)
        assertNull(q.consumeLatest())
    }

    @Test
    fun interleavedLaunchesConsumeInReverseOrder() {
        val q = UdlExportQueue()
        q.begin("A")
        q.begin("B")
        assertEquals("B", q.consumeLatest())
        assertEquals("A", q.consumeLatest())
        assertFalse(q.pending)
    }

    @Test
    fun clearDropsAllPayloads() {
        val q = UdlExportQueue()
        q.begin("A")
        q.begin("B")
        q.clear()
        assertFalse(q.pending)
        assertNull(q.consumeLatest())
    }
}
