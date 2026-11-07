package com.alauncher.olauncher

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class OEngineLaunchProfileManagerTest {
    @Test fun keepsDifferentLoadersInSeparateProfiles() {
        val root = Files.createTempDirectory("oengine-profiles").toFile()
        try {
            val fabric = OEngineLaunchProfileManager.create(root, "Performance", "1.20.1", OEngineModLoader.FABRIC, "0.16.9")
            val forge = OEngineLaunchProfileManager.create(root, "Performance", "1.20.1", OEngineModLoader.FORGE, "47.3.0")
            assertNotEquals(fabric.gameDirectory.parentFile, forge.gameDirectory.parentFile)
            assertEquals(2, OEngineLaunchProfileManager.list(root).size)
            assertEquals(OEngineModLoader.FABRIC, OEngineLaunchProfileManager.list(root).first { it.id == fabric.id }.loader)
        } finally { root.deleteRecursively() }
    }

    @Test fun selectedProfileSurvivesReloadAndCreatesItsGameDirectory() {
        val root = Files.createTempDirectory("oengine-active-profile").toFile()
        try {
            val profile = OEngineLaunchProfileManager.create(root, "Quilt SMP", "1.21.1", OEngineModLoader.QUILT, "0.27.1")
            OEngineLaunchProfileManager.select(root, profile.id)
            assertTrue(profile.gameDirectory.isDirectory)
            assertEquals(profile.id, OEngineLaunchProfileManager.active(root)?.id)
        } finally { root.deleteRecursively() }
    }

    @Test fun vanillaHasNoLoaderVersionAndUnknownLoaderFallsBackSafely() {
        val root = Files.createTempDirectory("oengine-vanilla-profile").toFile()
        try {
            assertEquals(OEngineModLoader.VANILLA, OEngineModLoader.fromId("not-a-loader"))
            val profile = OEngineLaunchProfileManager.create(root, "Clean", "1.21.1", OEngineModLoader.VANILLA)
            assertTrue(profile.isVanilla)
            assertNull(profile.loaderVersion)
            assertFalse(profile.gameDirectory.exists())
        } finally { root.deleteRecursively() }
    }
}
