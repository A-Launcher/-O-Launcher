package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OEngineInstanceIdTest {
    @Test fun sanitizesInstanceNamesIntoStableFolderIds() {
        assertEquals("My Survival World", OEngineInstanceManager.sanitizeDisplayName("My Survival World"))
        assertEquals("my-survival-world", OEngineInstanceManager.instanceIdFor("My Survival World"))
        assertEquals("modpack-1-20-1", OEngineInstanceManager.instanceIdFor("Modpack 1.20.1"))
        assertEquals("instance", OEngineInstanceManager.instanceIdFor("🔥"))
    }

    @Test fun sharedInstancesResolveToTheDefaultMinecraftRoot() {
        val base = File("/data/user/0/example/files/minecraft")
        val instance = File(base, "instances/shared-world")
        assertEquals(base, OEngineInstanceManager.gameRootFor(base, instance, true))
        assertNotEquals(instance, OEngineInstanceManager.gameRootFor(base, instance, true))
    }

    @Test fun managedInstanceDirectoryMustBeDirectChildOfInstancesRoot() {
        val temp = Files.createTempDirectory("oengine-instances").toFile()
        val escaped = File(temp.parentFile, "outside-" + temp.name)
        try {
            val managed = File(temp, "survival").apply { mkdirs() }
            escaped.mkdirs()
            assertTrue(OEngineInstanceManager.isManagedInstanceDirectory(temp, managed))
            assertFalse(OEngineInstanceManager.isManagedInstanceDirectory(temp, File(escaped, "nested")))
        } finally {
            temp.deleteRecursively()
            escaped.deleteRecursively()
        }
    }

    @Test fun symbolicLinkCannotMasqueradeAsManagedInstance() {
        val temp = Files.createTempDirectory("oengine-link-test").toFile()
        val outside = Files.createTempDirectory("oengine-outside").toFile()
        try {
            val link = File(temp, "linked")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
                assertFalse(OEngineInstanceManager.isManagedInstanceDirectory(temp, link))
            } catch (_: UnsupportedOperationException) {
                // Symbolic links are not supported by this test platform.
            } catch (_: java.io.IOException) {
                // The test runner may not grant symbolic-link creation privileges.
            } catch (_: SecurityException) {
                // The test runner may not grant symbolic-link creation privileges.
            }
        } finally {
            temp.deleteRecursively()
            outside.deleteRecursively()
        }
    }
    @Test fun isolatedInstancesResolveToTheirOwnGameDirectory() {
        val base = File("/data/user/0/example/files/minecraft")
        val instance = File(base, "instances/isolated-world")
        assertEquals(File(instance, "game"), OEngineInstanceManager.gameRootFor(base, instance, false))
        assertNotEquals(base, OEngineInstanceManager.gameRootFor(base, instance, false))
    }
}
