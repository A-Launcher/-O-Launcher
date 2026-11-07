package com.alauncher.olauncher

import java.util.ArrayDeque

/**
 * Bounded, thread-safe handoff between Android callbacks and the future game-thread input pump.
 * Motion is coalesced/evicted before transitions. If a transition cannot fit, the consumer is
 * explicitly told to release every pressed control, preventing silently stuck keys/buttons.
 */
class OEngineInputQueue(capacity: Int = DEFAULT_CAPACITY) {
    companion object { const val DEFAULT_CAPACITY = 512 }

    private val lock = Any()
    private val events = ArrayDeque<OEngineInputEvent>()
    private var dropped = 0L
    private var resetRequired = false

    val capacity: Int = capacity.also {
        require(it >= 8) { "Input queue capacity must be at least 8." }
    }
    val size: Int get() = synchronized(lock) { events.size }
    val droppedEventCount: Long get() = synchronized(lock) { dropped }
    val inputResetRequired: Boolean get() = synchronized(lock) { resetRequired }

    fun offer(event: OEngineInputEvent): Boolean = synchronized(lock) {
        val disposableAction = event.action == OEngineInputAction.MOVE ||
            event.action == OEngineInputAction.SCROLL

        if (event.action == OEngineInputAction.MOVE) {
            val snapshot = events.toList()
            val previousIndex = snapshot.indexOfLast {
                it.action == OEngineInputAction.MOVE &&
                    it.device == event.device && it.code == event.code
            }
            if (previousIndex >= 0) {
                // Replace in place: appending here would reorder motion across key transitions.
                events.clear()
                snapshot.forEachIndexed { index, queued ->
                    events.addLast(if (index == previousIndex) event else queued)
                }
                dropped++
                return@synchronized true
            }
        }

        if (events.size < capacity) {
            events.addLast(event)
            return@synchronized true
        }

        val disposable = events.firstOrNull {
            it.action == OEngineInputAction.MOVE || it.action == OEngineInputAction.SCROLL
        }
        if (disposable != null) {
            events.removeFirstOccurrence(disposable)
            dropped++
            events.addLast(event)
            return@synchronized true
        }

        dropped++
        if (!disposableAction) resetRequired = true
        false
    }

    /** Requests a consumer-side all-controls-up reset when upstream state itself cannot be tracked. */
    fun requestInputReset() = synchronized(lock) { resetRequired = true }

    /** Atomically acknowledges an overflow request to release all pressed controls. */
    fun consumeInputResetRequired(): Boolean = synchronized(lock) {
        val required = resetRequired
        resetRequired = false
        required
    }

    fun poll(): OEngineInputEvent? = synchronized(lock) {
        if (events.isEmpty()) null else events.removeFirst()
    }

    fun drain(maxEvents: Int = capacity): List<OEngineInputEvent> = synchronized(lock) {
        require(maxEvents >= 0)
        buildList(minOf(maxEvents, events.size)) {
            repeat(minOf(maxEvents, events.size)) { add(events.removeFirst()) }
        }
    }

    fun clear() = synchronized(lock) { events.clear() }
}
