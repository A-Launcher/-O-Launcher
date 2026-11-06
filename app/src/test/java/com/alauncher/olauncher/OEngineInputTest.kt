package com.alauncher.olauncher

import org.junit.Assert.*
import org.junit.Test

class OEngineInputTest {
    @Test fun resolvesKeyboardMouseAndGamepadBindings() {
        val bindings = OEngineInputBindings(listOf(
            OEngineControlBinding("move.forward", OEngineInputDevice.KEYBOARD, 87, "W"),
            OEngineControlBinding("camera.primary", OEngineInputDevice.MOUSE, 1, "Left mouse"),
            OEngineControlBinding("jump", OEngineInputDevice.GAMEPAD, 0, "A")
        ))
        assertEquals("move.forward", bindings.resolve(OEngineInputDevice.KEYBOARD, 87)?.controlId)
        assertEquals("camera.primary", bindings.resolve(OEngineInputDevice.MOUSE, 1)?.controlId)
        assertEquals("jump", bindings.resolve(OEngineInputDevice.GAMEPAD, 0)?.controlId)
        assertNull(bindings.resolve(OEngineInputDevice.TOUCH, 87))
    }

    @Test fun rejectsDuplicatePhysicalBindings() {
        try {
            OEngineInputBindings(listOf(
                OEngineControlBinding("jump", OEngineInputDevice.KEYBOARD, 32, "Space"),
                OEngineControlBinding("sneak", OEngineInputDevice.KEYBOARD, 32, "Space")
            ))
            fail("Duplicate physical input should be rejected.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsInvalidEventPayloads() {
        try {
            OEngineInputEvent(OEngineInputDevice.MOUSE, OEngineInputAction.TEXT, 0, text = "x")
            fail("Mouse text input should be rejected.")
        } catch (_: IllegalArgumentException) { }
    }
}
