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
        assertEquals(.88f, forward.y)
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

    @Test fun releasingHeldToggleUsesItsOriginalKeyBeforeRemap() {
        val controls = OEngineTouchControls.defaults()
        val oldKey = OEngineTouchControls.keyCodeToRelease(controls, "sneak", true)
        val remapped = OEngineTouchControls.remap(controls, "sneak", KeyEvent.KEYCODE_F)
        assertEquals(KeyEvent.KEYCODE_SHIFT_LEFT, oldKey)
        assertEquals(KeyEvent.KEYCODE_F, remapped.first { it.id == "sneak" }.keyCode)
        assertNull(OEngineTouchControls.keyCodeToRelease(remapped, "sneak", false))
    }

    @Test fun remapChangesOnlyTheSelectedControlsKey() {
        val controls = OEngineTouchControls.defaults()
        val remapped = OEngineTouchControls.remap(controls, "jump", KeyEvent.KEYCODE_F)
        assertEquals(KeyEvent.KEYCODE_F, remapped.first { it.id == "jump" }.keyCode)
        assertEquals(KeyEvent.KEYCODE_W, remapped.first { it.id == "forward" }.keyCode)
        assertEquals("JUMP", remapped.first { it.id == "jump" }.label)
    }

    @Test fun renameChangesOnlyTheSelectedControlAndTrimsWhitespace() {
        val controls = OEngineTouchControls.defaults()
        val renamed = OEngineTouchControls.rename(controls, "jump", "  HOP  ")
        assertEquals("HOP", renamed.first { it.id == "jump" }.label)
        assertEquals("▲", renamed.first { it.id == "forward" }.label)
    }

    @Test fun renameRejectsBlankAndOverlongLabels() {
        val controls = OEngineTouchControls.defaults()
        listOf("   ", "1234567890123").forEach { label ->
            try {
                OEngineTouchControls.rename(controls, "jump", label)
                fail("Invalid labels must be rejected.")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun layoutPresetsMirrorAndResizeControlsWithoutBreakingBounds() {
        val controls = OEngineTouchControls.defaults()
        val left = OEngineTouchControls.applyPreset(controls, "left-handed")
        val original = controls.first { it.id == "jump" }
        val mirrored = left.first { it.id == "jump" }
        assertEquals(1f - original.x - original.width, mirrored.x, .0001f)
        assertEquals(original.width, mirrored.width)

        val compact = OEngineTouchControls.applyPreset(controls, "compact")
        assertTrue(compact.all { it.width <= controls.first { old -> old.id == it.id }.width })
        assertTrue(compact.all { it.x >= 0f && it.x + it.width <= 1f })
        assertTrue(compact.all { it.y >= 0f && it.y + it.height <= 1f })
    }

    @Test fun unknownLayoutPresetIsRejected() {
        try {
            OEngineTouchControls.applyPreset(OEngineTouchControls.defaults(), "unknown")
            fail("Unknown presets must be rejected.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun layoutEncodingRoundTripsAllControlFields() {
        val controls = OEngineTouchControls.defaults()
        assertEquals(controls, OEngineTouchControls.decode(OEngineTouchControls.encode(controls)))
    }

    @Test fun decodingRejectsMoreThanFortyEightControlsBeforeBuildingLayout() {
        val items = (1..49).joinToString(",") { """{"id":"c$it","label":"C","keyCode":1,"x":0.1,"y":0.1}""" }
        val json = """{"format":"oengine-touch-layout","version":1,"controls":[$items]}"""
        try {
            OEngineTouchControls.decode(json)
            fail("Layouts with more than 48 controls must be rejected.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun decodingRejectsOversizedClipboardPayload() {
        try {
            OEngineTouchControls.decode(" ".repeat(16_385))
            fail("Oversized layouts must be rejected before JSON parsing.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun touchControlsRejectKeysOutsideTheNativeGlfwRange() {
        val controls = OEngineTouchControls.defaults()
        try {
            OEngineTouchControls.add(controls, "INVALID", 349)
            fail("Keys outside the native GLFW range must be rejected.")
        } catch (_: IllegalArgumentException) { }
        val json = OEngineTouchControls.encode(controls).replace("\"keyCode\":${controls.first().keyCode}", "\"keyCode\":999")
        try {
            OEngineTouchControls.decode(json)
            fail("Imported layouts must reject keys the native input bridge cannot accept.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun controlDimensionsAndCoordinatesAreValidated() {
        try {
            OEngineTouchControl("bad", "BAD", KeyEvent.KEYCODE_A, 1.5f, .5f)
            fail("Out-of-range normalized coordinates must be rejected.")
        } catch (_: IllegalArgumentException) { }
    }
}
