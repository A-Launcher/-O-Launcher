package com.alauncher.olauncher

/**
 * Process-local handoff from the responsive Binder ingress thread to the future native
 * GLFW/game-thread event pump. No EGL, JNI, or Minecraft calls are made by enqueue().
 */
object OEngineGameInputBridge {
    private val lock = Any()
    private val queue = OEngineInputQueue()
    private val maxTrackedControls = OEngineInputQueue.DEFAULT_CAPACITY * 2
    private val stateTracker = OEngineInputStateTracker(maxTrackedControls)

    val inputResetRequired: Boolean
        get() = queue.inputResetRequired

    /** Accepts one validated event. Duplicate digital transitions are safely suppressed. */
    @JvmStatic
    fun enqueue(event: OEngineInputEvent): Boolean = synchronized(lock) {
        if (event.action == OEngineInputAction.DOWN &&
            stateTracker.pressedCount >= maxTrackedControls &&
            !stateTracker.isPressed(event.device, event.code)) {
            queue.requestInputReset()
            return@synchronized false
        }
        val acceptedEvent = stateTracker.accept(event) ?: return@synchronized true
        val queued = queue.offer(acceptedEvent)
        // If an UP cannot fit, retain the pressed state so the consumer's overflow reset
        // still emits an UP for a key/button that may already have reached the game.
        if (!queued && acceptedEvent.action == OEngineInputAction.UP) {
            stateTracker.accept(acceptedEvent.copy(action = OEngineInputAction.DOWN, value = 1f))
        }
        queued
    }

    /**
     * Called by the future GLFW event-poll hook on the actual game thread. When transition
     * overflow occurs, queued transitions are discarded and all tracked controls are released.
     */
    @JvmStatic
    fun drainForGameThread(maxEvents: Int = OEngineInputQueue.DEFAULT_CAPACITY): Array<OEngineInputEvent> =
        synchronized(lock) {
            require(maxEvents >= 0)
            if (queue.consumeInputResetRequired()) {
                val releases = stateTracker.releaseAll(System.nanoTime())
                queue.clear()
                return@synchronized releases.toTypedArray()
            }
            queue.drain(maxEvents).toTypedArray()
        }

    /** Clears a dead session; never call this while a game-thread consumer is still active. */
    @JvmStatic
    fun clearSession() = synchronized(lock) {
        stateTracker.releaseAll(System.nanoTime())
        queue.clear()
    }

    internal fun resetForTests() = clearSession()
}
