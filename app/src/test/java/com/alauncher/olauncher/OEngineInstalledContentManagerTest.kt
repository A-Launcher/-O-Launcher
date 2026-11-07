package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OEngineInstalledContentManagerTest {
    @Test fun listsOnlySupportedContentInKnownFolders() {
        val root = Files.createTempDirectory("oengine-content").toFile()
        try {
            File(root, "mods").mkdirs()
            File(root, "mods/example.jar").writeText("mod")
            File(root, "mods/readme.txt").writeText("ignore")
            File(root, "resourcepacks").mkdirs()
            File(root, "resourcepacks/pack.zip").writeText("pack")
            val entries = OEngineInstalledContentManager.list(root)
            assertEquals(listOf("example.jar", "pack.zip"), entries.map { it.name })
            assertEquals(2, entries.size)
        } finally { root.deleteRecursively() }
    }

    @Test fun removeDeletesOnlySelectedInstanceFile() {
        val root = Files.createTempDirectory("oengine-remove").toFile()
        try {
            val mods = File(root, "mods").apply { mkdirs() }
            val mod = File(mods, "example.jar").apply { writeText("mod") }
            val outside = File(root, "outside.jar").apply { writeText("keep") }
            val entry = OEngineInstalledContentManager.list(root).single()
            assertTrue(OEngineInstalledContentManager.remove(root, entry))
            assertFalse(mod.exists())
            assertTrue(outside.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun sizeFormattingIsReadable() {
        assertEquals("512 B", OEngineInstalledContentManager.formatSize(512))
        assertEquals("1.0 KB", OEngineInstalledContentManager.formatSize(1024))
    }
}
