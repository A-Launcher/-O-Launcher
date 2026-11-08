package com.alauncher.olauncher

import java.io.File
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

    @Test fun isolatedInstancesResolveToTheirOwnGameDirectory() {
        val base = File("/data/user/0/example/files/minecraft")
        val instance = File(base, "instances/isolated-world")
        assertEquals(File(instance, "game"), OEngineInstanceManager.gameRootFor(base, instance, false))
        assertNotEquals(base, OEngineInstanceManager.gameRootFor(base, instance, false))
    }
}
