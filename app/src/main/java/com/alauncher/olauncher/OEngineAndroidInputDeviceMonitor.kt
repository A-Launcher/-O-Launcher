package com.alauncher.olauncher

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

/**
 * Releases held gamepad state when Android reports that a physical controller was removed.
 *
 * The event bridge intentionally aggregates controller controls into one GLFW joystick, so a
 * controller removal neutralizes the aggregate gamepad state rather than leaving stale buttons
 * or axes active in Minecraft.
 */
class OEngineAndroidInputDeviceMonitor(
    context: Context,
    private val onGamepadDisconnected: () -> Unit
) : AutoCloseable {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val handler = Handler(Looper.getMainLooper())
    private val knownGamepads = mutableSetOf<Int>()

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refresh(deviceId)
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            if (refresh(deviceId)) onGamepadDisconnected()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            if (knownGamepads.remove(deviceId)) onGamepadDisconnected()
        }
    }

    init {
        InputDevice.getDeviceIds().forEach(::refresh)
        inputManager.registerInputDeviceListener(listener, handler)
    }

    /** Returns true when a previously tracked gamepad stops advertising gamepad sources. */
    private fun refresh(deviceId: Int): Boolean {
        val wasGamepad = knownGamepads.contains(deviceId)
        val device = InputDevice.getDevice(deviceId)
        val isGamepad = device != null && isGamepadSources(device.sources)
        if (isGamepad) knownGamepads.add(deviceId) else knownGamepads.remove(deviceId)
        return wasGamepad && !isGamepad
    }

    override fun close() {
        inputManager.unregisterInputDeviceListener(listener)
        knownGamepads.clear()
    }

    companion object {
        internal fun isGamepadSources(sources: Int): Boolean =
            sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
    }
}
