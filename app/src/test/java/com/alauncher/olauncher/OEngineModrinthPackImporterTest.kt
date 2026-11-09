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
    @Test fun overrideImportDoesNotFollowPrecreatedPartFileSymlink() {
        val temp = Files.createTempDirectory("mrpack-part-symlink").toFile()
        try {
            val archive = File(temp, "test.mrpack")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write("""{"formatVersion":1,"dependencies":{"minecraft":"1.20.1"},"files":[]}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("overrides/config/example.txt"))
                zip.write("safe-content".toByteArray())
                zip.closeEntry()
            }
            val game = File(temp, "game")
            val config = File(game, "config")
            assertTrue(config.mkdirs())
            val outside = File(temp, "outside.txt").apply { writeText("untouched") }
            val planted = File(config, "example.txt.oengine-part")
            Files.createSymbolicLink(planted.toPath(), outside.toPath())

            OEngineModrinthPackImporter.importPack(archive, game)

            assertEquals("untouched", outside.readText())
            assertEquals("safe-content", File(config, "example.txt").readText())
            assertTrue(Files.isSymbolicLink(planted.toPath()))
        } finally { temp.deleteRecursively() }
    }

    @Test fun rejectsSymlinkedOverrideParent() {
        val temp = Files.createTempDirectory("mrpack-parent-symlink").toFile()
        try {
            val archive = File(temp, "test.mrpack")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write("""{"formatVersion":1,"dependencies":{"minecraft":"1.20.1"},"files":[]}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("overrides/config/escape.txt"))
                zip.write("must-not-write".toByteArray())
                zip.closeEntry()
            }
            val game = File(temp, "game")
            assertTrue(game.mkdirs())
            val outside = File(temp, "outside").apply { mkdirs() }
            Files.createSymbolicLink(File(game, "config").toPath(), outside.toPath())
            runCatching { OEngineModrinthPackImporter.importPack(archive, game) }
                .onSuccess { throw AssertionError("Symlinked override parent was accepted.") }
            assertFalse(File(outside, "escape.txt").exists())
        } finally { temp.deleteRecursively() }
    }

    @Test fun preservesUserFileAtLegacyBackupNameWhenReplacingOverride() {
        val temp = Files.createTempDirectory("mrpack-backup-collision").toFile()
        try {
            val archive = File(temp, "test.mrpack")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write("""{"formatVersion":1,"dependencies":{"minecraft":"1.20.1"},"files":[]}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("overrides/config/example.txt"))
                zip.write("new-content".toByteArray())
                zip.closeEntry()
            }
            val game = File(temp, "game")
            val config = File(game, "config").apply { mkdirs() }
            File(config, "example.txt").writeText("old-content")
            val userBackup = File(config, "example.txt.oengine-backup").apply { writeText("user-data") }
            OEngineModrinthPackImporter.importPack(archive, game)
            assertEquals("new-content", File(config, "example.txt").readText())
            assertEquals("user-data", userBackup.readText())
        } finally { temp.deleteRecursively() }
    }

    @Test fun rejectsCaseInsensitiveCollisionsAcrossOverrideRoots() {
        val temp = Files.createTempDirectory("mrpack-override-collision").toFile()
        try {
            val archive = File(temp, "collision.mrpack")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("modrinth.index.json"))
                zip.write("""{"formatVersion":1,"dependencies":{"minecraft":"1.20.1"},"files":[]}""".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("overrides/config/Options.txt"))
                zip.write("first".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("client-overrides/config/options.txt"))
                zip.write("second".toByteArray())
                zip.closeEntry()
            }
            val game = File(temp, "game")
            runCatching { OEngineModrinthPackImporter.importPack(archive, game) }
                .onSuccess { throw AssertionError("Case-insensitive override collision was accepted.") }
        } finally { temp.deleteRecursively() }
    }

}
