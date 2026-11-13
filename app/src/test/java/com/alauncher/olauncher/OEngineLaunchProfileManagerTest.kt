package com.alauncher.olauncher

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
    @Test fun selectingAnotherProfileReplacesActivePointerWithoutLeavingTemporaryFile() {
        val root = Files.createTempDirectory("oengine-profile-switch").toFile()
        try {
            val first = OEngineLaunchProfileManager.create(root, "First", "1.20.1", OEngineModLoader.VANILLA)
            val second = OEngineLaunchProfileManager.create(root, "Second", "1.20.1", OEngineModLoader.VANILLA)
            OEngineLaunchProfileManager.select(root, first.id)
            OEngineLaunchProfileManager.select(root, second.id)
            assertEquals(second.id, OEngineLaunchProfileManager.active(root)?.id)
            assertFalse(java.io.File(root, "active-profile.tmp").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun ignoresProfileDirectoriesThatResolveOutsideProfilesRoot() {
        val root = Files.createTempDirectory("oengine-profile-paths").toFile()
        val outside = Files.createTempDirectory("oengine-profile-outside").toFile()
        try {
            val profiles = java.io.File(root, "profiles")
            assertTrue(profiles.mkdir())
            val externalProfile = java.io.File(outside, "external")
            assertTrue(externalProfile.mkdir())
            val metadata = java.io.File(externalProfile, "profile.properties")
            metadata.writeText("id=external\\nname=External\\nminecraftVersion=1.20.1\\nloader=vanilla\\n")
            val link = java.io.File(profiles, "linked")
            try {
                Files.createSymbolicLink(link.toPath(), externalProfile.toPath())
                assertTrue(OEngineLaunchProfileManager.list(root).isEmpty())
            } catch (_: UnsupportedOperationException) {
                // Symbolic links are not available on every test filesystem.
            } catch (_: java.io.IOException) {
                // Symbolic links may be disabled by the host filesystem.
            }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

}
