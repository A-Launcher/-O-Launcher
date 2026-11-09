package com.alauncher.olauncher

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class OEngineMojoControlMapImporterTest {
    @Test fun importsFixedPositionMojoV9ButtonsIntoNormalizedControls() {
        val json = """{"version":9,"mControlDataList":[{
            "name":"Jump","keycodes":[62,-1],"dynamicX":"900.0","dynamicY":"600.0",
            "width":80.0,"height":60.0,"isToggle":true,"displayInGame":true
        }],"mDrawerDataList":[],"mJoystickDataList":[]}"""
        val imported = OEngineMojoControlMapImporter.importJson(json, 1000f, 800f, 1f)
        assertEquals(9, imported.sourceVersion)
        assertEquals(1, imported.controls.size)
        val jump = imported.controls.single()
        assertEquals(KeyEvent.KEYCODE_SPACE, jump.keyCode)
        assertEquals("Jump", jump.label)
        assertEquals(.9f, jump.x, .0001f)
        assertEquals(.75f, jump.y, .0001f)
        assertTrue(jump.toggle)
    }

    @Test fun rejectsMojoDynamicExpressionsInsteadOfMisplacingButtons() {
        val json = """{"version":9,"mControlDataList":[{
            "name":"Jump","keycodes":[62],"dynamicX":"${right} - ${margin}","dynamicY":"20",
            "width":50,"height":50
        }],"mDrawerDataList":[],"mJoystickDataList":[]}"""
        try {
            OEngineMojoControlMapImporter.importJson(json, 1000f, 800f)
            fail("Dynamic Mojo positions must not be silently approximated.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsLayoutsWithDrawersOrJoysticksToAvoidDataLoss() {
        val drawer = """{"version":9,"mControlDataList":[{"name":"A","keycodes":[1],"dynamicX":"1","dynamicY":"1"}],"mDrawerDataList":[{}],"mJoystickDataList":[]}"""
        val joystick = """{"version":9,"mControlDataList":[{"name":"A","keycodes":[1],"dynamicX":"1","dynamicY":"1"}],"mDrawerDataList":[],"mJoystickDataList":[{}]}"""
        listOf(drawer, joystick).forEach { json ->
            try {
                OEngineMojoControlMapImporter.importJson(json, 1000f, 800f)
                fail("Unsupported compound controls must be rejected.")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rejectsMojoSpecialButtonsWithoutPretendingTheyAreKeyEvents() {
        val json = """{"version":9,"mControlDataList":[{"name":"Mouse","keycodes":[-5,-1],"dynamicX":"1","dynamicY":"1"}],"mDrawerDataList":[],"mJoystickDataList":[]}"""
        try {
            OEngineMojoControlMapImporter.importJson(json, 1000f, 800f)
            fail("Mojo special actions need a real action bridge.")
        } catch (_: IllegalArgumentException) { }
    }
}
