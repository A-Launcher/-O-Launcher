package com.alauncher.olauncher

/**
 * Tracks digital controls that are currently down and synthesizes releases when Android
 * focus/surface ownership is lost. Call on the same serialized input-ingress thread.
 */
class OEngineInputStateTracker(maxPressedControls: Int = 512) {
    init { require(maxPressedControls >= 1) }
    private val maxPressedControls = maxPressedControls
    private data class Control(val device: OEngineInputDevice, val code: Int)
    private val pressed = LinkedHashMap<Control, OEngineInputEvent>()

    val pressedCount: Int get() = pressed.size
    @Synchronized fun isPressed(device: OEngineInputDevice, code: Int): Boolean =
        pressed.containsKey(Control(device, code))

    /** Records a digital transition and returns the event to forward. */
    @Synchronized
    fun accept(event: OEngineInputEvent): OEngineInputEvent? {
        val control = Control(event.device, event.code)
        return when (event.action) {
            OEngineInputAction.DOWN -> {
                if (pressed.containsKey(control) || pressed.size >= maxPressedControls) null
                else {
                    pressed[control] = event
                    event
                }
            }
            OEngineInputAction.REPEAT -> if (pressed.containsKey(control)) event else null
            OEngineInputAction.UP -> {
                if (pressed.remove(control) == null) null else event
            }
            else -> event
        }
    }

    /** Synthesizes UP for every held digital control, newest press first. */
    @Synchronized
    fun releaseAll(timestampNanos: Long = 0L): List<OEngineInputEvent> {
        val releases = pressed.entries.toList().asReversed().map { (control, down) ->
            OEngineInputEvent(
                device = control.device,
                action = OEngineInputAction.UP,
                code = control.code,
                timestampNanos = timestampNanos,
                modifiers = down.modifiers
            )
        }
        pressed.clear()
        return releases
    }

    /** Releases only controls belonging to a disconnected Android input device class. */
    @Synchronized
    fun releaseDevice(device: OEngineInputDevice, timestampNanos: Long = 0L): List<OEngineInputEvent> {
        val matching = pressed.entries.filter { it.key.device == device }.asReversed()
        val releases = matching.map { (control, down) ->
            OEngineInputEvent(
                device = control.device,
                action = OEngineInputAction.UP,
                code = control.code,
                timestampNanos = timestampNanos,
                modifiers = down.modifiers
            )
        }
        matching.forEach { pressed.remove(it.key) }
        return releases
    }
}
