package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineLegacyLwjgl2CompatibilityTest {
    @Test
    fun identifiesOnlyTheLegacyLwjgl2CoreCoordinate() {
        assertTrue(OEngineLegacyLwjgl2Compatibility.isLegacyLwjgl2Coordinate(
            "org.lwjgl.lwjgl:lwjgl:2.9.4"
        ))
        assertTrue(OEngineLegacyLwjgl2Compatibility.isLegacyLwjgl2Coordinate(
            "org.lwjgl.lwjgl:lwjgl:2.9.1"
        ))
        assertFalse(OEngineLegacyLwjgl2Compatibility.isLegacyLwjgl2Coordinate(
            "org.lwjgl.lwjgl:lwjgl_util:2.9.4"
        ))
        assertFalse(OEngineLegacyLwjgl2Compatibility.isLegacyLwjgl2Coordinate(
            "org.lwjgl:lwjgl:3.3.3"
        ))
    }

    @Test
    fun pinsMojoJavaArtifactAndItsChecksums() {
        assertEquals("bf01b1b05c417b55cffc04d85dbc3a9cfa3b93df",
            OEngineLegacyLwjgl2Compatibility.UPSTREAM_COMMIT)
        assertEquals(1_052_773L, OEngineLegacyLwjgl2Compatibility.JAVA_ARCHIVE_SIZE)
        assertEquals(40, OEngineLegacyLwjgl2Compatibility.JAVA_ARCHIVE_SHA1.length)
        assertEquals(64, OEngineLegacyLwjgl2Compatibility.JAVA_ARCHIVE_SHA256.length)
        assertTrue(OEngineLegacyLwjgl2Compatibility.JAVA_ARCHIVE_URL.endsWith("/vv8m/lwjgl.jar"))
    }

    @Test
    fun everySupportedAndroidAbiHasAnArchitectureSpecificNativeArchive() {
        assertEquals(setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"),
            OEngineLegacyLwjgl2Compatibility.supportedAbis())
        assertEquals("liblwjgl64.so",
            OEngineLegacyLwjgl2Compatibility.nativeArchive("arm64-v8a").libraryFileName)
        assertEquals("liblwjgl.so",
            OEngineLegacyLwjgl2Compatibility.nativeArchive("armeabi-v7a").libraryFileName)
        assertEquals("liblwjgl.so",
            OEngineLegacyLwjgl2Compatibility.nativeArchive("x86").libraryFileName)
        assertEquals("liblwjgl64.so",
            OEngineLegacyLwjgl2Compatibility.nativeArchive("x86_64").libraryFileName)
        OEngineLegacyLwjgl2Compatibility.supportedAbis().forEach { abi ->
            val artifact = OEngineLegacyLwjgl2Compatibility.nativeArchive(abi)
            assertEquals(64, artifact.sha256.length)
            assertTrue(artifact.size > 0L)
            assertTrue(artifact.url.endsWith("/vv8m/" + artifact.fileName))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnpublishedAndroidAbi() {
        OEngineLegacyLwjgl2Compatibility.nativeArchive("riscv64")
    }
}
