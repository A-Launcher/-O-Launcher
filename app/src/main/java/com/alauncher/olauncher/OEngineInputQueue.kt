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
            val previous = events.lastOrNull {
                it.action == OEngineInputAction.MOVE &&
                    it.device == event.device && it.code == event.code
            }
            if (previous != null) {
                events.removeLastOccurrence(previous)
                dropped++
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
