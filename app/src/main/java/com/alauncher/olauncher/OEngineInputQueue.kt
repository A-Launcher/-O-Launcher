package com.alauncher.olauncher

import java.util.ArrayDeque

/**
 * Bounded, thread-safe handoff between Android input callbacks and the future game-input pump.
 * Motion/scroll samples are disposable under pressure; key/button transitions are preserved
 * preferentially so a full queue does not routinely leave controls stuck.
 */
class OEngineInputQueue(capacity: Int = DEFAULT_CAPACITY) {
    companion object {
        const val DEFAULT_CAPACITY = 512
    }

    private val lock = Any()
    private val events = ArrayDeque<OEngineInputEvent>()
    private var dropped = 0L

    val capacity: Int = capacity.also { require(it >= 8) { "Input queue capacity must be at least 8." } }

    val size: Int
        get() = synchronized(lock) { events.size }

    val droppedEventCount: Long
        get() = synchronized(lock) { dropped }

    fun offer(event: OEngineInputEvent): Boolean = synchronized(lock) {
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

        if (event.action == OEngineInputAction.MOVE || event.action == OEngineInputAction.SCROLL) {
            dropped++
            return@synchronized false
        }

        // A transition is more important than a stale queued transition when no samples can be evicted.
        events.removeFirst()
        dropped++
        events.addLast(event)
        true
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

    fun clear() = synchronized(lock) {
        events.clear()
    }
}
