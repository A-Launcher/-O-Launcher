package com.alauncher.olauncher

/** Platform-neutral input contracts; Android adapters and the game bridge are separate layers. */
enum class OEngineInputDevice { TOUCH, KEYBOARD, MOUSE, GAMEPAD }
enum class OEngineInputAction { DOWN, UP, REPEAT, MOVE, SCROLL, TEXT }

data class OEngineInputEvent(
    val device: OEngineInputDevice,
    val action: OEngineInputAction,
    val code: Int,
    val x: Float = 0f,
    val y: Float = 0f,
    val value: Float = 0f,
    val text: String? = null,
    val timestampNanos: Long = 0L,
    val modifiers: Int = 0,
    val codepoint: Int = 0,
    val scanCode: Int = 0
) {
    init {
        require(x.isFinite() && y.isFinite() && value.isFinite())
        require(modifiers >= 0)
        require(codepoint in 0..0x10FFFF && codepoint !in 0xD800..0xDFFF)
        require(action != OEngineInputAction.TEXT || device == OEngineInputDevice.KEYBOARD)
        require(action != OEngineInputAction.SCROLL || device == OEngineInputDevice.MOUSE)
        require(action != OEngineInputAction.TEXT || !text.isNullOrEmpty())
    }
}

data class OEngineControlBinding(
    val controlId: String,
    val device: OEngineInputDevice,
    val code: Int,
    val label: String,
    val analog: Boolean = false
) {
    init {
        require(controlId.matches(Regex("[a-zA-Z0-9_.-]{1,64}")))
        require(label.isNotBlank() && label.length <= 48)
    }
}

/** Deterministic resolver. This contract does not yet inject events into Minecraft. */
class OEngineInputBindings(bindings: List<OEngineControlBinding>) {
    private val byControl: Map<String, OEngineControlBinding>
    private val byPhysical: Map<Pair<OEngineInputDevice, Int>, OEngineControlBinding>

    init {
        require(bindings.map { it.controlId }.distinct().size == bindings.size)
        require(bindings.map { it.device to it.code }.distinct().size == bindings.size)
        byControl = bindings.associateBy { it.controlId }
        byPhysical = bindings.associateBy { it.device to it.code }
    }

    fun binding(controlId: String): OEngineControlBinding? = byControl[controlId]
    fun resolve(device: OEngineInputDevice, code: Int): OEngineControlBinding? = byPhysical[device to code]
    fun all(): List<OEngineControlBinding> = byControl.values.sortedBy { it.controlId }
}


/**
 * Converts Android IME text commits into ordered GLFW character events.
 * A single committed string may contain multiple Unicode code points.
 */
internal fun expandOEngineTextEvents(events: List<OEngineInputEvent>): List<OEngineInputEvent> =
    events.flatMap { event ->
        if (event.action != OEngineInputAction.TEXT) {
            listOf(event)
        } else {
            event.text!!.codePoints().toArray().map { codepoint ->
                event.copy(codepoint = codepoint)
            }
        }
    }.also {
        require(it.size <= 128) { "Expanded native input batches must contain at most 128 events." }
    }
