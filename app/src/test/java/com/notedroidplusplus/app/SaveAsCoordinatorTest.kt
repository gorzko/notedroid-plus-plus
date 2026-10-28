package com.notedroidplusplus.app

import com.notedroidplusplus.app.core.SaveAsCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveAsCoordinatorTest {

    // C1: Save As on A, then Close-with-Save on B while A's picker is open.
    // A's result must resolve to A's flow; B's flow must start only after A finishes.
    @Test
    fun overlappingFlowsSerializePickers() {
        val c = SaveAsCoordinator()
        val a = c.request(owner = null)
        assertTrue(a.startNow)
        val b = c.request(owner = null)
        assertFalse(b.startNow)
        assertEquals(a.flow.key, c.active?.key)
        // A's picker returns: only A's flow is active, so the result lands on A.
        val nextAfterA = c.finish(a.flow.key)
        assertEquals(b.flow.key, nextAfterA?.key)
        assertEquals(b.flow.key, c.active?.key)
        // B's picker returns: B finishes, nothing queued.
        assertNull(c.finish(b.flow.key))
        assertNull(c.active)
    }

    @Test
    fun finishIgnoresMismatchedOrStaleKey() {
        val c = SaveAsCoordinator()
        val a = c.request(owner = null)
        assertNull(c.finish(a.flow.key + 100))
        assertEquals(a.flow.key, c.active?.key)
        assertNull(c.finish(999))
        assertEquals(a.flow.key, c.active?.key)
    }

    @Test
    fun queuedFlowFinishesAfterActiveOne() {
        val c = SaveAsCoordinator()
        val first = c.request(owner = "save-all")
        val second = c.request(owner = "save-all")
        val third = c.request(owner = "save-all")
        assertEquals(2, c.queued.size)
        assertEquals(second.flow.key, c.finish(first.flow.key)?.key)
        assertEquals(third.flow.key, c.finish(second.flow.key)?.key)
        assertNull(c.finish(third.flow.key))
        assertEquals(0, c.queued.size)
    }

    // C2: cancelling a standalone flow must never drop another flow's queued requests.
    @Test
    fun cancelIsOwnerScoped() {
        val c = SaveAsCoordinator()
        val f1 = c.request(owner = "save-all")
        c.request(owner = "save-all")
        c.request(owner = "save-all")
        val standalone = c.request(owner = null)
        assertFalse(standalone.startNow)
        val res = c.cancel(standalone.flow.key)
        assertEquals(0, res.dropped.size)
        assertEquals(3, c.queued.size)
        // The save-all series is untouched and still drains.
        assertEquals(f1.flow.key, c.active?.key)
    }

    @Test
    fun cancelDropsSameOwnerQueuedFlows() {
        val c = SaveAsCoordinator()
        val f1 = c.request(owner = "save-all")
        c.request(owner = "save-all")
        c.request(owner = "save-all")
        val standalone = c.request(owner = null)
        val res = c.cancel(f1.flow.key)
        assertEquals(2, res.dropped.size)
        assertEquals(standalone.flow.key, res.next?.key)
        assertEquals(0, c.queued.size)
    }

    @Test
    fun cancelActiveStandaloneDropsOnlyItself() {
        val c = SaveAsCoordinator()
        val a = c.request(owner = null)
        val res = c.cancel(a.flow.key)
        assertEquals(0, res.dropped.size)
        assertNull(res.next)
        assertNull(c.active)
    }

    @Test
    fun cancelWithNoActiveFlowIsNoOp() {
        val c = SaveAsCoordinator()
        val res = c.cancel(42)
        assertNull(res.next)
        assertEquals(0, res.dropped.size)
    }

    @Test
    fun interleavedOwnersKeepIndependentScopes() {
        val c = SaveAsCoordinator()
        val a = c.request(owner = "save-all-A")
        val b = c.request(owner = "save-all-B")
        assertNotNull(b)
        val res = c.cancel(a.flow.key)
        assertEquals(0, res.dropped.size)
        assertEquals(b.flow.key, res.next?.key)
        assertEquals(b.flow.key, c.active?.key)
    }
}
