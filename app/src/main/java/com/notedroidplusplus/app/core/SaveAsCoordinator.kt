package com.notedroidplusplus.app.core

/**
 * Serializes Save As flows so only one encoding-dialog/SAF-picker is in
 * flight at a time. A request that arrives while another flow is active is
 * queued (FIFO) and started when the active flow finishes or is cancelled.
 * The single ActivityResultLauncher callback then only ever observes the
 * one active flow, so a picker result can never be applied to another
 * flow's session.
 *
 * Cancellation is owner-scoped: cancelling the active flow also drops
 * queued requests with the same non-null owner (e.g. the remaining tabs of
 * a Save All series — preserving the "cancel aborts Save All" behavior),
 * but never touches requests owned by other flows. Null-owner flows are
 * each in their own scope: a standalone Save As / Close-with-Save cancel
 * drops only itself.
 *
 * Pure Kotlin, no Android dependencies — interleavings covered by JVM tests.
 */
class SaveAsCoordinator {

    data class Flow(val key: Int, val owner: String?)

    data class RequestResult(val flow: Flow, val startNow: Boolean)

    /** Next flow to start after a cancellation, plus every queued flow dropped by it. */
    data class CancelResult(val next: Flow?, val dropped: List<Flow>)

    private val queue = ArrayDeque<Flow>()
    private var activeFlow: Flow? = null
    private var nextKey = 1

    val active: Flow? get() = activeFlow
    val queued: List<Flow> get() = queue.toList()

    /** Registers a request. Returns its [Flow]; [RequestResult.startNow] is false when it was queued behind an active flow. */
    fun request(owner: String?): RequestResult {
        val flow = Flow(nextKey++, owner)
        if (activeFlow != null) {
            queue.addLast(flow)
            return RequestResult(flow, startNow = false)
        }
        activeFlow = flow
        return RequestResult(flow, startNow = true)
    }

    /** Success path: closes the active flow (key must match) and returns the next flow to start, if any. */
    fun finish(key: Int): Flow? {
        if (activeFlow?.key != key) return null
        activeFlow = null
        return startNext()
    }

    /** Cancel path: drops the active flow plus same-owner queued flows; returns the next flow to start. */
    fun cancel(key: Int): CancelResult {
        val a = activeFlow ?: return CancelResult(null, emptyList())
        if (a.key != key) return CancelResult(null, emptyList())
        activeFlow = null
        val dropped = mutableListOf<Flow>()
        if (a.owner != null) {
            val kept = ArrayDeque<Flow>()
            while (queue.isNotEmpty()) {
                val next = queue.removeFirst()
                if (next.owner == a.owner) dropped.add(next) else kept.addLast(next)
            }
            queue.addAll(kept)
        }
        return CancelResult(startNext(), dropped)
    }

    private fun startNext(): Flow? {
        val next = queue.removeFirstOrNull() ?: return null
        activeFlow = next
        return next
    }
}
