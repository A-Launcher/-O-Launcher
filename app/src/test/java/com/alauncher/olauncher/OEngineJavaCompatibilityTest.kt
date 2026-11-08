package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineJavaCompatibilityTest {
    @Test fun mapsLegacyAndClassicVersionsToJavaEightRuntime() {
        listOf("c0.0.11a", "1.5.2").forEach { version ->
            val result = OEngineJavaCompatibility.resolve(version)
            assertEquals(5, result.targetJavaMajor)
            assertEquals(8, result.runtimeJavaMajor)
        }
        listOf("b1.7.3", "1.6.1", "1.11.2").forEach { version ->
            val result = OEngineJavaCompatibility.resolve(version)
            assertEquals(6, result.targetJavaMajor)
            assertEquals(8, result.runtimeJavaMajor)
        }
    }

    @Test fun mapsJavaEightAndSixteenEraVersions() {
        assertEquals(8, OEngineJavaCompatibility.resolve("1.16.5").runtimeJavaMajor)
        val java16 = OEngineJavaCompatibility.resolve("1.17.1")
        assertEquals(16, java16.targetJavaMajor)
        assertEquals(17, java16.runtimeJavaMajor)
        assertEquals(17, OEngineJavaCompatibility.resolve("1.18").runtimeJavaMajor)
        assertEquals(17, OEngineJavaCompatibility.resolve("1.20.4").runtimeJavaMajor)
    }

    @Test fun mapsJavaTwentyOneAndTwentyFiveEras() {
        assertEquals(21, OEngineJavaCompatibility.resolve("1.20.5").runtimeJavaMajor)
        assertEquals(21, OEngineJavaCompatibility.resolve("1.21.11").runtimeJavaMajor)
        assertEquals(25, OEngineJavaCompatibility.resolve("26.1").runtimeJavaMajor)
        assertEquals(25, OEngineJavaCompatibility.resolve("26.4-snapshot-7").runtimeJavaMajor)
    }

    @Test fun officialMetadataOverridesFallbackTable() {
        val result = OEngineJavaCompatibility.resolve("1.20.1", metadataMajor = 21)
        assertEquals(21, result.targetJavaMajor)
        assertEquals(21, result.runtimeJavaMajor)
        assertEquals(OEngineJavaCompatibility.Source.MOJANG_METADATA, result.source)
        assertTrue(result.explanation.contains("Mojang"))
    }

    @Test fun unknownMetadataTargetsRemainExplicit() {
        val result = OEngineJavaCompatibility.resolve("1.20.1", metadataMajor = 22)
        assertEquals(22, result.targetJavaMajor)
        assertEquals(22, result.runtimeJavaMajor)
    }

    @Test fun blocksJavaTwentyFiveBeforeRuntimeDownloadUntilNativeHookIsSupported() {
        val result = OEngineJavaCompatibility.resolve("26.1")
        assertEquals(25, result.runtimeJavaMajor)
        assertTrue(result.nativeLaunchBlocker()?.contains("supports Java 8, 17, and 21 only") == true)
    }

    @Test fun permitsAllCurrentlyImplementedNativeHookJavaMajors() {
        listOf(8, 17, 21).forEach { major ->
            val result = OEngineJavaCompatibility.resolve("1.20.1", metadataMajor = major)
            assertEquals(null, result.nativeLaunchBlocker())
        }
    }
}
