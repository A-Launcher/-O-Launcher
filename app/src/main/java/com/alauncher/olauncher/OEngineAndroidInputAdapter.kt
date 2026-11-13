package com.alauncher.olauncher

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent

/** Shared source classification: many Android controllers report their D-pad separately. */
internal fun isOEngineGamepadSource(sources: Int): Boolean =
    sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
        sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
        sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD

/** Converts Android hardware/touch events into the explicit GLFW-compatible input model. */
object OEngineAndroidInputAdapter {
    fun fromKeyEvent(event: KeyEvent): OEngineInputEvent? {
        val sources = event.source or (event.device?.sources ?: 0)
        val isGamepad = isOEngineGamepadSource(sources)
        val device = if (isGamepad) OEngineInputDevice.GAMEPAD else OEngineInputDevice.KEYBOARD
        val action = when {
            event.action == KeyEvent.ACTION_UP -> OEngineInputAction.UP
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 -> OEngineInputAction.REPEAT
            event.action == KeyEvent.ACTION_DOWN -> OEngineInputAction.DOWN
            else -> return null
        }
        val gamepadButton = if (isGamepad) OEngineAndroidGamepadMapper.buttonIndex(event.keyCode) else null
        val triggerAxis = if (isGamepad) OEngineAndroidGamepadMapper.triggerAxisIndex(event.keyCode) else null
        if (isGamepad && gamepadButton == null && triggerAxis == null) return null
        val code = when {
            gamepadButton != null -> gamepadButton
            triggerAxis != null -> triggerAxis
            else -> OEngineGlfwKeyMapper.key(event.keyCode) ?: return null
        }
        val mappedAction = if (triggerAxis != null) OEngineInputAction.MOVE else action
        val meta = OEngineGlfwKeyMapper.modifiers(event.metaState)
        val rawCodepoint = event.getUnicodeChar(event.metaState)
        val codepoint = if (rawCodepoint < 0) 0 else
            rawCodepoint and KeyCharacterMap.COMBINING_ACCENT_MASK.inv()
        return OEngineInputEvent(
            device = device,
            action = mappedAction,
            code = code,
            scanCode = event.scanCode,
            value = if (action == OEngineInputAction.UP) 0f else 1f,
            timestampNanos = event.eventTime * 1_000_000L,
            modifiers = meta,
            codepoint = codepoint.coerceIn(0, 0x10FFFF)
        )
    }

    fun fromMotionEvent(event: MotionEvent): List<OEngineInputEvent> {
        val source = event.source
        val isGamepad = isOEngineGamepadSource(source)
        val isRelativeMouse = android.os.Build.VERSION.SDK_INT >= 26 &&
            source and InputDevice.SOURCE_MOUSE_RELATIVE == InputDevice.SOURCE_MOUSE_RELATIVE
        val isTouchpad = android.os.Build.VERSION.SDK_INT >= 26 &&
            source and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD
        val isMouse = source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
            isRelativeMouse || isTouchpad
        val device = when {
            isGamepad -> OEngineInputDevice.GAMEPAD
            isMouse -> OEngineInputDevice.MOUSE
            else -> OEngineInputDevice.TOUCH
        }
        val timestamp = event.eventTime * 1_000_000L
        val output = mutableListOf<OEngineInputEvent>()

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                if (isGamepad) {
                    val axes = intArrayOf(
                        MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
                        MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER
                    )
                    axes.forEachIndexed { index, axis ->
                        output += OEngineInputEvent(
                            OEngineInputDevice.GAMEPAD, OEngineInputAction.MOVE, index,
                            value = event.getAxisValue(axis), timestampNanos = timestamp
                        )
                    }
                    val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                    val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                    // Always publish the hat state, including neutral (0, 0), so
                    // releasing a D-pad direction cannot leave a key stuck in-game.
                    output += OEngineInputEvent(
                        OEngineInputDevice.GAMEPAD, OEngineInputAction.MOVE, 6,
                        x = hatX, y = hatY, timestampNanos = timestamp
                    )
                } else if (isMouse) {
                    // Android sends captured mouse deltas in getX()/getY() for
                    // SOURCE_MOUSE_RELATIVE. Captured touchpads expose their deltas
                    // through AXIS_RELATIVE_X/Y. Preserve every batched sample.
                    if (isRelativeMouse || isTouchpad) {
                        for (history in 0 until event.historySize) {
                            val dx = if (isTouchpad) event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, history)
                                else event.getHistoricalX(0, history)
                            val dy = if (isTouchpad) event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, history)
                                else event.getHistoricalY(0, history)
                            output += OEngineInputEvent(
                                OEngineInputDevice.MOUSE, OEngineInputAction.MOVE, 0, dx, dy,
                                timestampNanos = event.getHistoricalEventTime(history) * 1_000_000L
                            )
                        }
                    }
                    val mouseX = if (isTouchpad) event.getAxisValue(MotionEvent.AXIS_RELATIVE_X) else event.x
                    val mouseY = if (isTouchpad) event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) else event.y
                    output += OEngineInputEvent(
                        OEngineInputDevice.MOUSE, OEngineInputAction.MOVE, 0, mouseX, mouseY,
                        timestampNanos = timestamp
                    )
                } else {
                    for (index in 0 until event.pointerCount) {
                        output += OEngineInputEvent(
                            OEngineInputDevice.TOUCH, OEngineInputAction.MOVE,
                            event.getPointerId(index), event.getX(index), event.getY(index),
                            timestampNanos = timestamp
                        )
                    }
                }
            }
            MotionEvent.ACTION_SCROLL -> if (isMouse) {
                output += OEngineInputEvent(
                    OEngineInputDevice.MOUSE, OEngineInputAction.SCROLL, 0,
                    x = event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                    y = event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                    timestampNanos = timestamp
                )
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (!isMouse && !isGamepad) {
                val down = event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_POINTER_DOWN
                val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                output += OEngineInputEvent(
                    OEngineInputDevice.TOUCH,
                    if (down) OEngineInputAction.DOWN else OEngineInputAction.UP,
                    event.getPointerId(index),
                    event.getX(index), event.getY(index),
                    value = if (down) 1f else 0f,
                    timestampNanos = timestamp
                )
            }
            MotionEvent.ACTION_CANCEL -> if (device == OEngineInputDevice.TOUCH) {
                for (index in 0 until event.pointerCount) {
                    output += OEngineInputEvent(
                        OEngineInputDevice.TOUCH, OEngineInputAction.UP,
                        event.getPointerId(index), event.getX(index), event.getY(index),
                        value = 0f, timestampNanos = timestamp
                    )
                }
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> if (isMouse) {
                val down = event.actionMasked == MotionEvent.ACTION_DOWN
                output += OEngineInputEvent(
                    OEngineInputDevice.MOUSE,
                    if (down) OEngineInputAction.DOWN else OEngineInputAction.UP,
                    0, event.x, event.y, value = if (down) 1f else 0f,
                    timestampNanos = timestamp
                )
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> if (isMouse) {
                val button = OEngineGlfwKeyMapper.mouseButton(event.actionButton) ?: return output
                val down = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
                output += OEngineInputEvent(
                    OEngineInputDevice.MOUSE,
                    if (down) OEngineInputAction.DOWN else OEngineInputAction.UP,
                    button, value = if (down) 1f else 0f, timestampNanos = timestamp
                )
            }
        }
        return output
    }
}
