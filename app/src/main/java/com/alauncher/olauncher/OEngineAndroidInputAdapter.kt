package com.alauncher.olauncher

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/** Converts Android hardware/touch events into O Engine's platform-neutral input model. */
object OEngineAndroidInputAdapter {
    fun fromKeyEvent(event: KeyEvent): OEngineInputEvent? {
        val sources = event.source or (event.device?.sources ?: 0)
        val device = if (
            sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        ) {
            OEngineInputDevice.GAMEPAD
        } else {
            OEngineInputDevice.KEYBOARD
        }
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> OEngineInputAction.DOWN
            KeyEvent.ACTION_UP -> OEngineInputAction.UP
            else -> return null
        }
        return OEngineInputEvent(
            device = device,
            action = action,
            code = event.keyCode,
            value = if (action == OEngineInputAction.DOWN) 1f else 0f,
            timestampNanos = event.eventTime * 1_000_000L
        )
    }

    fun fromMotionEvent(event: MotionEvent): List<OEngineInputEvent> {
        val source = event.source
        val device = when {
            source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ->
                OEngineInputDevice.GAMEPAD
            source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ->
                OEngineInputDevice.MOUSE
            else -> OEngineInputDevice.TOUCH
        }
        val timestamp = event.eventTime * 1_000_000L
        val output = mutableListOf<OEngineInputEvent>()

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val x = if (device == OEngineInputDevice.GAMEPAD) {
                    event.getAxisValue(MotionEvent.AXIS_X)
                } else event.x
                val y = if (device == OEngineInputDevice.GAMEPAD) {
                    event.getAxisValue(MotionEvent.AXIS_Y)
                } else event.y
                output += OEngineInputEvent(device, OEngineInputAction.MOVE, 0, x, y,
                    timestampNanos = timestamp)
            }
            MotionEvent.ACTION_SCROLL -> if (device == OEngineInputDevice.MOUSE) {
                output += OEngineInputEvent(
                    device, OEngineInputAction.SCROLL, 0,
                    value = event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                    timestampNanos = timestamp
                )
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_BUTTON_PRESS -> output += OEngineInputEvent(
                device, OEngineInputAction.DOWN, event.actionButton,
                event.getX(event.actionIndex.coerceIn(0, event.pointerCount - 1)),
                event.getY(event.actionIndex.coerceIn(0, event.pointerCount - 1)),
                value = 1f, timestampNanos = timestamp
            )
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_BUTTON_RELEASE -> output += OEngineInputEvent(
                device, OEngineInputAction.UP, event.actionButton,
                value = 0f, timestampNanos = timestamp
            )
        }
        return output
    }
}
