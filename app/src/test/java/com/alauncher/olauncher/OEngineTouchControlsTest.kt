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

    @Test fun resizeChangesOnlySelectedControlAndRespectsBounds() {
        val controls = OEngineTouchControls.defaults()
        val enlarged = OEngineTouchControls.resize(controls, "jump", 100f)
        val jump = enlarged.first { it.id == "jump" }
        assertEquals(.3f, jump.width)
        assertEquals(.35f, jump.height)
        assertEquals(controls.first { it.id == "forward" }, enlarged.first { it.id == "forward" })
    }

    @Test fun addAndRemoveCustomControlsPreserveUniqueIds() {
        val controls = OEngineTouchControls.defaults()
        val expanded = OEngineTouchControls.add(controls, "DROP", KeyEvent.KEYCODE_Q)
        assertEquals(controls.size + 1, expanded.size)
        assertEquals(KeyEvent.KEYCODE_Q, expanded.last().keyCode)
        assertEquals(expanded.size, expanded.map { it.id }.distinct().size)
        val reduced = OEngineTouchControls.remove(expanded, expanded.last().id)
        assertEquals(controls, reduced)
    }

    @Test fun layoutEncodingRoundTripsAllControlFields() {
        val controls = OEngineTouchControls.defaults()
        assertEquals(controls, OEngineTouchControls.decode(OEngineTouchControls.encode(controls)))
    }

    @Test fun controlDimensionsAndCoordinatesAreValidated() {
        try {
            OEngineTouchControl("bad", "BAD", KeyEvent.KEYCODE_A, 1.5f, .5f)
            fail("Out-of-range normalized coordinates must be rejected.")
        } catch (_: IllegalArgumentException) { }
    }
}
