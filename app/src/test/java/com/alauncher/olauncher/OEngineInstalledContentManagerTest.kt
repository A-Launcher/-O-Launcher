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

    @Test fun ignoresFoldersThatAreNotMinecraftWorlds() {
        val root = Files.createTempDirectory("oengine-not-world").toFile()
        try {
            File(root, "saves/FolderOnly/datapacks/fake.zip").apply { parentFile.mkdirs(); writeText("fake") }
            val entries = OEngineInstalledContentManager.list(root)
            assertTrue(entries.none { it.kind == OEngineInstalledContentManager.Kind.DATA_PACK })
            assertTrue(OEngineInstalledContentManager.worldNames(root).isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun listsDataPacksInsideEachWorld() {
        val root = Files.createTempDirectory("oengine-world-packs").toFile()
        try {
            File(root, "datapacks/root-pack.zip").apply { parentFile.mkdirs(); writeText("wrong location") }
            File(root, "saves/Survival/level.dat").writeText("world")
            File(root, "saves/Creative/level.dat").writeText("world")
            File(root, "saves/Survival/datapacks/world-pack.zip").apply { parentFile.mkdirs(); writeText("pack") }
            File(root, "saves/Creative/datapacks/another-pack.zip").apply { parentFile.mkdirs(); writeText("pack2") }
            val entries = OEngineInstalledContentManager.list(root)
                .filter { it.kind == OEngineInstalledContentManager.Kind.DATA_PACK }
            assertEquals(setOf("Survival", "Creative"), entries.mapNotNull { it.worldName }.toSet())
            assertEquals(setOf("world-pack.zip", "another-pack.zip"), entries.map { it.name }.toSet())
        } finally { root.deleteRecursively() }
    }

    @Test fun listIgnoresSymbolicLinksToFilesOutsideInstanceFolder() {
        val root = Files.createTempDirectory("oengine-content-links").toFile()
        try {
            val mods = File(root, "mods").apply { mkdirs() }
            val outside = File(root, "outside.jar").apply { writeText("private") }
            val link = File(mods, "linked.jar")
            try {
                Files.createSymbolicLink(link.toPath(), outside.toPath())
            } catch (_: UnsupportedOperationException) {
                return
            } catch (_: SecurityException) {
                return
            }
            assertTrue(OEngineInstalledContentManager.list(root).isEmpty())
            assertTrue(outside.exists())
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

    @Test fun removesDataPackFromItsOwningWorldOnly() {
        val root = Files.createTempDirectory("oengine-remove-world-pack").toFile()
        try {
            File(root, "saves/Survival/level.dat").writeText("world")
            File(root, "saves/Creative/level.dat").writeText("world")
            val pack = File(root, "saves/Survival/datapacks/pack.zip").apply { parentFile.mkdirs(); writeText("pack") }
            val other = File(root, "saves/Creative/datapacks/pack.zip").apply { parentFile.mkdirs(); writeText("other") }
            val entry = OEngineInstalledContentManager.list(root).single()
            assertTrue(OEngineInstalledContentManager.remove(root, entry))
            assertFalse(pack.exists())
            assertTrue(other.exists())
        } finally { root.deleteRecursively() }
    }


    @Test fun ignoresDataPacksReachedThroughSymlinkedWorldFolders() {
        val root = Files.createTempDirectory("oengine-world-link").toFile()
        try {
            val outside = Files.createTempDirectory("oengine-world-outside").toFile()
            try {
                File(outside, "datapacks/hidden.zip").apply { parentFile.mkdirs(); writeText("hidden") }
                val saves = File(root, "saves").apply { mkdirs() }
                try {
                    Files.createSymbolicLink(File(saves, "LinkedWorld").toPath(), outside.toPath())
                } catch (_: UnsupportedOperationException) {
                    return
                } catch (_: SecurityException) {
                    return
                }
                assertTrue(
                    OEngineInstalledContentManager.list(root)
                        .none { it.kind == OEngineInstalledContentManager.Kind.DATA_PACK }
                )
            } finally {
                outside.deleteRecursively()
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun refusesRemovalIfWorldFolderBecomesSymlinkAfterListing() {
        val root = Files.createTempDirectory("oengine-world-replaced").toFile()
        try {
            val world = File(root, "saves/Survival")
            File(world, "level.dat").apply { parentFile.mkdirs(); writeText("world") }
            val pack = File(world, "datapacks/pack.zip").apply { parentFile.mkdirs(); writeText("pack") }
            val entry = OEngineInstalledContentManager.list(root).single()
            val saves = File(root, "saves")
            val outside = Files.createTempDirectory("oengine-world-replacement").toFile()
            try {
                File(outside, "datapacks/pack.zip").apply { parentFile.mkdirs(); writeText("keep") }
                assertTrue(world.deleteRecursively())
                try {
                    Files.createSymbolicLink(world.toPath(), outside.toPath())
                } catch (_: UnsupportedOperationException) {
                    return
                } catch (_: SecurityException) {
                    return
                }
                val failure = runCatching { OEngineInstalledContentManager.remove(root, entry) }.exceptionOrNull()
                assertTrue(failure is IllegalArgumentException)
                assertTrue(File(outside, "datapacks/pack.zip").exists())
                assertFalse(pack.exists())
            } finally {
                outside.deleteRecursively()
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun sizeFormattingIsReadable() {
        assertEquals("512 B", OEngineInstalledContentManager.formatSize(512))
        assertEquals("1.0 KB", OEngineInstalledContentManager.formatSize(1024))
    }
}
