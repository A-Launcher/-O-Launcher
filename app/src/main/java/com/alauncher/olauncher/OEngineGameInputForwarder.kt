package com.alauncher.olauncher

import java.util.ArrayDeque

/**
 * Serializes Android input state and forwards bounded batches to the isolated game process.
 * Failed Binder sends remain queued so a release is never silently lost after a DOWN.
 */
class OEngineGameInputForwarder(
    private val sendBatch: (List<OEngineInputEvent>) -> Boolean,
    private val tracker: OEngineInputStateTracker = OEngineInputStateTracker()
) {
    private val lock = Any()
    private val pending = ArrayDeque<List<OEngineInputEvent>>()

    fun forward(events: List<OEngineInputEvent>): Boolean = synchronized(lock) {
        // Do not advance the held-control tracker while earlier events are still unsent.
        if (!flushPending()) return@synchronized false
        sendBatches(events.mapNotNull(tracker::accept))
    }

    fun releaseAll(timestampNanos: Long = System.nanoTime()): Boolean = synchronized(lock) {
        queueBatches(tracker.releaseAll(timestampNanos))
        flushPending()
    }

    fun releaseDevice(device: OEngineInputDevice, timestampNanos: Long = System.nanoTime()): Boolean =
        synchronized(lock) {
            queueBatches(tracker.releaseDevice(device, timestampNanos))
            flushPending()
        }

    private fun sendBatches(events: List<OEngineInputEvent>): Boolean {
        queueBatches(events)
        return flushPending()
    }

    private fun queueBatches(events: List<OEngineInputEvent>) {
        events.chunked(MAX_BATCH_SIZE).forEach(pending::addLast)
        check(pending.size <= MAX_PENDING_BATCHES) {
            "Too many unsent O Engine input batches; the game process is not accepting input."
        }
    }

    private fun flushPending(): Boolean {
        while (pending.isNotEmpty()) {
            val batch = pending.first()
            if (!sendBatch(batch)) return false
            pending.removeFirst()
        }
        return true
    }

    internal fun pendingBatchCount(): Int = synchronized(lock) { pending.size }

    companion object {
        const val MAX_BATCH_SIZE = 128
        const val MAX_PENDING_BATCHES = 16
    }
}
