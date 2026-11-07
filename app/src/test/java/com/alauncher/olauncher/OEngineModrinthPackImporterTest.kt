package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OEngineModrinthPackImporterTest {
    @Test fun rejectsTraversalAndAbsolutePaths() {
        listOf("../escape.jar", "/absolute.jar", "C:/outside.jar", "mods/../../escape.jar").forEach { path ->
            runCatching { OEngineModrinthPackImporter.safeRelativePath(path) }
                .onSuccess { throw AssertionError("Accepted unsafe path: $path") }
        }
        assertEquals("mods/example.jar", OEngineModrinthPackImporter.safeRelativePath("mods/example.jar"))
    }

    @Test fun importsClientOverridesIntoInstance() {
        val temp = Files.createTempDirectory("mrpack-test").toFile()
        try {
            val archive = File(temp, "test.mrpack")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write("""{"formatVersion":1,"game":"minecraft","versionId":"pack-v1","name":"Test Pack","dependencies":{"minecraft":"1.20.1","fabric-loader":"0.15.0"},"files":[]}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("overrides/config/example.txt"))
                zip.write("config".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("client-overrides/options.txt"))
                zip.write("options".toByteArray())
                zip.closeEntry()
            }
            val game = File(temp, "instance/game")
            val result = OEngineModrinthPackImporter.importPack(archive, game)
            assertEquals("Test Pack", result.name)
            assertEquals("1.20.1", result.minecraftVersion)
            assertEquals("0.15.0", result.loaders["fabric-loader"])
            assertEquals(2, result.copiedOverrideFiles)
            assertTrue(File(game, "config/example.txt").readText() == "config")
            assertTrue(File(game, "options.txt").readText() == "options")
        } finally { temp.deleteRecursively() }
    }

    @Test fun invalidArchiveDoesNotCreateFilesOutsideInstance() {
        val temp = Files.createTempDirectory("mrpack-invalid").toFile()
        try {
            val archive = File(temp, "bad.mrpack")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write("""{"formatVersion":1,"dependencies":{"minecraft":"1.20.1"},"files":[]}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("overrides/../escape.txt"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
            val game = File(temp, "game")
            runCatching { OEngineModrinthPackImporter.importPack(archive, game) }
                .onSuccess { throw AssertionError("Unsafe override path was accepted.") }
            assertFalse(File(temp, "escape.txt").exists())
        } finally { temp.deleteRecursively() }
    }
}
