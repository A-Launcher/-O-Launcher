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

    @Test fun preservesNestedMojoDrawerAndJoystickDefinitionsWithoutClaimingTheyAreExecutable() {
        val json = """{"version":9,
            "mControlDataList":[{"name":"A","keycodes":[29,-1],"dynamicX":"1","dynamicY":"1"}],
            "mDrawerDataList":[{"orientation":"RIGHT","properties":{"name":"Tools"},"buttonProperties":[{"name":"One","keycodes":[30,-1]}]}],
            "mJoystickDataList":[{"name":"Move","forwardLock":true,"absolute":false,"dynamicX":"12","dynamicY":"34"}]}"""
        val imported = OEngineMojoControlMapImporter.importJson(json, 1000f, 800f)
        assertEquals(1, imported.controls.size)
        assertEquals(1, imported.unsupportedDrawers)
        assertEquals(1, imported.unsupportedJoysticks)
        assertEquals(1, imported.preservedDrawerJson.size)
        assertTrue(imported.preservedDrawerJson.single().contains("buttonProperties"))
        assertTrue(imported.preservedDrawerJson.single().contains("RIGHT"))
        assertEquals(1, imported.preservedJoystickJson.size)
        assertTrue(imported.preservedJoystickJson.single().contains("forwardLock"))
        assertTrue(imported.preservedJoystickJson.single().contains("absolute"))
    }

    @Test fun roundTripsPreservedCompoundControlsForDurableStorage() {
        val original = OEngineMojoControlMapImporter.PreservedCompoundControls(
            drawers = listOf("""{"orientation":"RIGHT","buttonProperties":[{"name":"Open","keycodes":[30,-1]}]}"""),
            joysticks = listOf("""{"name":"Move","forwardLock":true,"deadZone":0.2}""")
        )
        val encoded = OEngineMojoControlMapImporter.encodePreservedCompoundControls(original)
        val decoded = OEngineMojoControlMapImporter.decodePreservedCompoundControls(encoded)
        assertEquals(1, decoded.drawers.size)
        assertTrue(decoded.drawers.single().contains("buttonProperties"))
        assertTrue(decoded.drawers.single().contains("Open"))
        assertEquals(1, decoded.joysticks.size)
        assertTrue(decoded.joysticks.single().contains("forwardLock"))
        assertTrue(decoded.joysticks.single().contains("deadZone"))
    }

    @Test fun decodesMojoJoystickRuntimeFlagsWithSafeDefaults() {
        val configured = OEngineMojoControlMapImporter.decodeJoystickRuntimeConfig(
            """{"name":"Move","forwardLock":true,"absolute":true}"""
        )
        assertTrue(configured.forwardLock)
        assertTrue(configured.absolute)

        val defaults = OEngineMojoControlMapImporter.decodeJoystickRuntimeConfig("""{"name":"Move"}""")
        assertFalse(defaults.forwardLock)
        assertFalse(defaults.absolute)
    }

    @Test fun readsFixedMojoJoystickPositionAndSizeButDoesNotGuessDynamicExpressions() {
        val fixed = OEngineMojoControlMapImporter.decodeJoystickRuntimeConfig(
            """{"dynamicX":"42","dynamicY":"128","width":180,"height":160,"absolute":true}"""
        )
        assertEquals(42f, fixed.xDp!!)
        assertEquals(128f, fixed.yDp!!)
        assertEquals(180f, fixed.widthDp)
        assertEquals(160f, fixed.heightDp)

        val dynamic = OEngineMojoControlMapImporter.decodeJoystickRuntimeConfig(
            """{"dynamicX":"${screen_width} - 180","dynamicY":"${bottom} - 20"}"""
        )
        assertNull(dynamic.xDp)
        assertNull(dynamic.yDp)
        assertEquals(150f, dynamic.widthDp)
        assertEquals(150f, dynamic.heightDp)
    }

    @Test fun rejectsUnsupportedOrMalformedPreservedCompoundStorage() {
        try {
            OEngineMojoControlMapImporter.decodePreservedCompoundControls(
                """{"format":"other","version":1,"drawers":[],"joysticks":[]}"""
            )
            fail("Unknown compound-control storage formats must be rejected.")
        } catch (_: IllegalArgumentException) { }
        try {
            OEngineMojoControlMapImporter.decodePreservedCompoundControls(
                """{"format":"oengine-mojo-compound-preserved","version":1,"drawers":["not-json"],"joysticks":[]}"""
            )
            fail("Malformed preserved definitions must be rejected.")
        } catch (_: Exception) { }
    }

    @Test fun rejectsMalformedCompoundControlEntriesInsteadOfSilentlyDroppingThem() {
        val json = """{"version":9,"mControlDataList":[{"name":"A","keycodes":[29],"dynamicX":"1","dynamicY":"1"}],"mDrawerDataList":[null]}"""
        try {
            OEngineMojoControlMapImporter.importJson(json, 1000f, 800f)
            fail("Malformed compound controls must not be silently discarded.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsMultiKeyChordsRatherThanDroppingPartOfTheAction() {
        val json = """{"version":9,"mControlDataList":[{"name":"Chord","keycodes":[29,30,-1],"dynamicX":"1","dynamicY":"1"}]}"""
        try {
            OEngineMojoControlMapImporter.importJson(json, 1000f, 800f)
            fail("Multi-key chords must not be reduced to a single key.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsMojoSpecialButtonsWithoutPretendingTheyAreKeyEvents() {
        val json = """{"version":9,"mControlDataList":[{"name":"Mouse","keycodes":[-5,-1],"dynamicX":"1","dynamicY":"1"}],"mDrawerDataList":[],"mJoystickDataList":[]}"""
        try {
            OEngineMojoControlMapImporter.importJson(json, 1000f, 800f)
            fail("Mojo special actions need a real action bridge.")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun importsMojoLayoutBitmapsZipContainer() {
        val json = """{"version":9,"mControlDataList":[{"name":"Forward","keycodes":[ W_KEYCODE_PLACEHOLDER , -1],"dynamicX":"10","dynamicY":"20","width":50,"height":50}],"mDrawerDataList":[],"mJoystickDataList":[]}""".replace("W_KEYCODE_PLACEHOLDER", "51")
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("layout.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("assets/unused.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val imported = OEngineMojoControlMapImporter.importBytes(output.toByteArray(), 1000f, 800f)
        assertEquals(1, imported.controls.size)
        assertEquals(KeyEvent.KEYCODE_W, imported.controls.single().keyCode)
    }

    @Test fun rejectsMojoZipWhenAnAssetAppearsBeforeLayoutJson() {
        val json = """{"version":9,"mControlDataList":[{"name":"A","keycodes":[29],"dynamicX":"1","dynamicY":"1"}]}"""
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("assets/first.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
            zip.putNextEntry(java.util.zip.ZipEntry("layout.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        try {
            OEngineMojoControlMapImporter.importBytes(output.toByteArray(), 1000f, 800f)
            fail("Mojo ZIP assets must not precede layout.json.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsMojoZipWithoutLayoutJson() {
        val output = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(output).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("assets/unused.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        try {
            OEngineMojoControlMapImporter.importBytes(output.toByteArray(), 1000f, 800f)
            fail("A Mojo ZIP without layout.json must be rejected.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun decodesSafeMojoDrawerButtonsAndParentGeometry() {
        val decoded = OEngineMojoControlMapImporter.decodeDrawerRuntimeConfig(
            """{"orientation":"RIGHT","properties":{"name":"Tools","dynamicX":"40","dynamicY":"80","width":64,"height":48},
            "buttonProperties":[
                {"name":"Inventory","keycodes":[E_KEY,-1],"isToggle":false},
                {"name":"Jump","keycodes":[62,-1],"isToggle":true}
            ]}""".replace("E_KEY", KeyEvent.KEYCODE_E.toString())
        )
        assertEquals("Tools", decoded.label)
        assertEquals("RIGHT", decoded.orientation)
        assertEquals(40f, decoded.xDp!!)
        assertEquals(80f, decoded.yDp!!)
        assertEquals(64f, decoded.widthDp)
        assertEquals(48f, decoded.heightDp)
        assertEquals(2, decoded.buttons.size)
        assertEquals(KeyEvent.KEYCODE_E, decoded.buttons[0].keyCode)
        assertTrue(decoded.buttons[1].toggle)
    }

    @Test fun rejectsMojoDrawerSpecialActionsAndDynamicPositionsAreNotEvaluated() {
        val special = """{"orientation":"LEFT","properties":{"dynamicX":"1","dynamicY":"2"},
            "buttonProperties":[{"name":"Mouse","keycodes":[-5,-1]}]}"""
        try {
            OEngineMojoControlMapImporter.decodeDrawerRuntimeConfig(special)
            fail("Special Mojo actions must not be dispatched as keyboard keys.")
        } catch (_: IllegalArgumentException) { }

        val dynamic = """{"orientation":"FREE","properties":{"dynamicX":"${right}","dynamicY":"2"},
            "buttonProperties":[{"name":"Jump","keycodes":[62,-1]}]}"""
        val decoded = OEngineMojoControlMapImporter.decodeDrawerRuntimeConfig(dynamic)
        assertNull(decoded.xDp)
        assertEquals("FREE", decoded.orientation)
    }

    @Test fun rejectsMalformedOrOversizedMojoDrawerButtonLists() {
        val malformed = """{"properties":{},"buttonProperties":[null]}"""
        try {
            OEngineMojoControlMapImporter.decodeDrawerRuntimeConfig(malformed)
            fail("Malformed drawer children must be rejected.")
        } catch (_: IllegalArgumentException) { }
    }

}
