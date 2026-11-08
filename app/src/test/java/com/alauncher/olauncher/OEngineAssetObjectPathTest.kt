package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OEngineAssetObjectPathTest {
    @Test fun resolvesSha1ObjectInsideObjectRoot() {
        val root = Files.createTempDirectory("asset-objects").toFile()
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val target = OEngineAssetObjectPath.resolve(root, hash)
        assertEquals(File(root, "01/$hash").canonicalFile, target)
    }

    @Test fun rejectsMalformedAssetHashes() {
        val root = Files.createTempDirectory("asset-objects").toFile()
        listOf("", "../" + "a".repeat(37), "g".repeat(40), "a".repeat(39)).forEach { hash ->
            assertThrows(IllegalArgumentException::class.java) {
                OEngineAssetObjectPath.resolve(root, hash)
            }
        }
    }

    @Test fun rejectsMappedAssetTraversal() {
        val root = Files.createTempDirectory("asset-mappings").toFile()
        listOf("../outside", "/absolute/path", "").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                OEngineAssetObjectPath.resolveMapped(root, name)
            }
        }
    }

    @Test fun allowsNestedMappedAssetNames() {
        val root = Files.createTempDirectory("asset-mappings").toFile()
        val target = OEngineAssetObjectPath.resolveMapped(root, "minecraft/sounds/music/menu.ogg")
        assertEquals(File(root, "minecraft/sounds/music/menu.ogg").canonicalFile, target)
    }
}
