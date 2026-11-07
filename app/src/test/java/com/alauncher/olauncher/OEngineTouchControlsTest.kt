package com.alauncher.olauncher

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class OEngineTouchControlsTest {
    @Test fun defaultLayoutContainsMovementAndActionControls() {
        val controls = OEngineTouchControls.defaults()
        assertTrue(controls.size >= 7)
        assertTrue(controls.any { it.keyCode == KeyEvent.KEYCODE_W })
        assertTrue(controls.any { it.keyCode == KeyEvent.KEYCODE_SPACE })
        assertTrue(controls.any { it.keyCode == KeyEvent.KEYCODE_E })
        assertEquals(controls.size, controls.map { it.id }.distinct().size)
    }

    @Test fun movingAControlClampsItsNormalizedPosition() {
        val defaults = OEngineTouchControls.defaults()
        val moved = OEngineTouchControls.move(defaults, "forward", -0.5f, 1.5f)
        val forward = moved.first { it.id == "forward" }
        assertEquals(0f, forward.x)
        assertEquals(1f, forward.y)
        assertEquals(defaults.size, moved.size)
        assertEquals(.5f, defaults.first { it.id == "forward" }.x)
    }

    @Test fun movingUnknownControlIsRejected() {
        try {
            OEngineTouchControls.move(OEngineTouchControls.defaults(), "missing", .5f, .5f)
            fail("Unknown controls must not be added by a move operation.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun controlDimensionsAndCoordinatesAreValidated() {
        try {
            OEngineTouchControl("bad", "BAD", KeyEvent.KEYCODE_A, 1.5f, .5f)
            fail("Out-of-range normalized coordinates must be rejected.")
        } catch (_: IllegalArgumentException) { }
    }
}
