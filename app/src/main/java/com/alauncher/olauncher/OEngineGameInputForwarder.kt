package com.alauncher.olauncher

/**
 * Serializes Android input state and forwards bounded batches to the isolated game process.
 * The receiver currently stores events in Rust; this class does not imply GLFW consumption.
 */
class OEngineGameInputForwarder(
    private val sendBatch: (List<OEngineInputEvent>) -> Boolean,
    private val tracker: OEngineInputStateTracker = OEngineInputStateTracker()
) {
    private val lock = Any()

    fun forward(events: List<OEngineInputEvent>): Boolean = synchronized(lock) {
        val accepted = events.mapNotNull(tracker::accept)
        sendBatches(accepted)
    }

    fun releaseAll(timestampNanos: Long = System.nanoTime()): Boolean = synchronized(lock) {
        sendBatches(tracker.releaseAll(timestampNanos))
    }

    fun releaseDevice(device: OEngineInputDevice, timestampNanos: Long = System.nanoTime()): Boolean =
        synchronized(lock) {
            sendBatches(tracker.releaseDevice(device, timestampNanos))
        }

    private fun sendBatches(events: List<OEngineInputEvent>): Boolean {
        if (events.isEmpty()) return true
        for (batch in events.chunked(MAX_BATCH_SIZE)) {
            if (!sendBatch(batch)) return false
        }
        return true
    }

    companion object { const val MAX_BATCH_SIZE = 128 }
}
