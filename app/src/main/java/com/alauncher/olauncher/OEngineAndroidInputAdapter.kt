package com.alauncher.olauncher

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent

/** Converts Android hardware/touch events into the explicit GLFW-compatible input model. */
object OEngineAndroidInputAdapter {
    fun fromKeyEvent(event: KeyEvent): OEngineInputEvent? {
        val sources = event.source or (event.device?.sources ?: 0)
        val isGamepad = sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        val device = if (isGamepad) OEngineInputDevice.GAMEPAD else OEngineInputDevice.KEYBOARD
        val action = when {
            event.action == KeyEvent.ACTION_UP -> OEngineInputAction.UP
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 -> OEngineInputAction.REPEAT
            event.action == KeyEvent.ACTION_DOWN -> OEngineInputAction.DOWN
            else -> return null
        }
        val code = if (isGamepad) event.keyCode else
            OEngineGlfwKeyMapper.key(event.keyCode) ?: return null
        val meta = OEngineGlfwKeyMapper.modifiers(event.metaState)
        val rawCodepoint = event.getUnicodeChar(event.metaState)
        val codepoint = if (rawCodepoint < 0) 0 else
            rawCodepoint and KeyCharacterMap.COMBINING_ACCENT_MASK.inv()
        return OEngineInputEvent(
            device = device,
            action = action,
            code = code,
            value = if (action == OEngineInputAction.UP) 0f else 1f,
            timestampNanos = event.eventTime * 1_000_000L,
            modifiers = meta,
            codepoint = codepoint.coerceIn(0, 0x10FFFF)
        )
    }

    fun fromMotionEvent(event: MotionEvent): List<OEngineInputEvent> {
        val source = event.source
        val isGamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        val isMouse = source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE
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
                    if (hatX != 0f || hatY != 0f) {
                        output += OEngineInputEvent(
                            OEngineInputDevice.GAMEPAD, OEngineInputAction.MOVE, 6,
                            x = hatX, y = hatY, timestampNanos = timestamp
                        )
                    }
                } else if (isMouse) {
                    output += OEngineInputEvent(
                        OEngineInputDevice.MOUSE, OEngineInputAction.MOVE, 0, event.x, event.y,
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
