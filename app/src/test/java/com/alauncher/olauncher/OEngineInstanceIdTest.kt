package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test fun isolatedInstancesResolveToTheirOwnGameDirectory() {
        val base = File("/data/user/0/example/files/minecraft")
        val instance = File(base, "instances/isolated-world")
        assertEquals(File(instance, "game"), OEngineInstanceManager.gameRootFor(base, instance, false))
        assertNotEquals(base, OEngineInstanceManager.gameRootFor(base, instance, false))
    }
    @Test fun rejectsInstancesRootSymlinkedOutsideMinecraftDirectory() {
        val base = Files.createTempDirectory("oengine-instance-base").toFile()
        val outside = Files.createTempDirectory("oengine-instance-outside").toFile()
        try {
            Files.createSymbolicLink(File(base, "instances").toPath(), outside.toPath())
            assertEquals(null, OEngineInstanceManager.instancesRootFor(base))
        } catch (_: UnsupportedOperationException) {
            // Symbolic links are not available on every test filesystem.
        } catch (_: java.io.IOException) {
            // Symbolic links may be disabled by the host filesystem.
        } finally {
            base.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun rejectsInstanceDirectorySymlinksAndEscapedDirectories() {
        val base = Files.createTempDirectory("oengine-instance-root").toFile()
        val outside = Files.createTempDirectory("oengine-instance-external").toFile()
        try {
            val instances = File(base, "instances")
            assertTrue(instances.mkdir())
            val external = File(outside, "external")
            assertTrue(external.mkdir())
            assertEquals(false, OEngineInstanceManager.isSafeInstanceDirectory(instances, external))
            val link = File(instances, "linked")
            try {
                Files.createSymbolicLink(link.toPath(), external.toPath())
            } catch (_: UnsupportedOperationException) {
                return
            } catch (_: java.io.IOException) {
                return
            }
            assertEquals(false, OEngineInstanceManager.isSafeInstanceDirectory(instances, link))
        } finally {
            base.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun createsInstancesRootOnlyInsideMinecraftDirectory() {
        val base = Files.createTempDirectory("oengine-instance-create").toFile()
        try {
            val root = OEngineInstanceManager.instancesRootFor(base, create = true)
            assertNotNull(root)
            assertEquals(base.canonicalFile, root!!.canonicalFile.parentFile)
        } finally {
            base.deleteRecursively()
        }
    }

}
