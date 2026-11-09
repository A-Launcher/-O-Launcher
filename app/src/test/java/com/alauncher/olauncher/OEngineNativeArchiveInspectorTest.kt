package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineNativeArchiveInspectorTest {
    @Test
    fun extractsOnlySharedObjectsAndPreservesSafeSubdirectories() {
        val root = Files.createTempDirectory("oengine-native-archive").toFile()
        try {
            val archive = File(root, "linux-natives.jar")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("org/lwjgl/liblwjgl.so"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("org/lwjgl/libhelper.so.1"))
                zip.write(byteArrayOf(4, 5))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("readme.txt"))
                zip.write("not a library".toByteArray())
                zip.closeEntry()
            }
            val destination = File(root, "inspection")
            assertEquals(2, OEngineNativeArchiveInspector.extractLinuxNativeArchive(archive, destination))
            assertTrue(File(destination, "org/lwjgl/liblwjgl.so").isFile)
            assertTrue(File(destination, "org/lwjgl/libhelper.so.1").isFile)
            assertTrue(!File(destination, "readme.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsOversizedSharedObjects() {
        val root = Files.createTempDirectory("oengine-native-oversized").toFile()
        try {
            val archive = File(root, "oversized.jar")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("libhuge.so"))
                val block = ByteArray(1024 * 1024)
                repeat(65) { zip.write(block) }
                zip.closeEntry()
            }
            OEngineNativeArchiveInspector.extractLinuxNativeArchive(archive, File(root, "inspection"))
        } finally { root.deleteRecursively() }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsDuplicateSharedObjectPaths() {
        val root = Files.createTempDirectory("oengine-native-duplicate").toFile()
        try {
            val archive = File(root, "duplicate.jar")
            ZipOutputStream(archive.outputStream()).use { zip ->
                repeat(2) {
                    zip.putNextEntry(ZipEntry("libsame.so"))
                    zip.write(byteArrayOf(1, 2, 3))
                    zip.closeEntry()
                }
            }
            OEngineNativeArchiveInspector.extractLinuxNativeArchive(archive, File(root, "inspection"))
        } finally { root.deleteRecursively() }
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsZipSlipEntries() {
        val root = Files.createTempDirectory("oengine-native-zip-slip").toFile()
        try {
            val archive = File(root, "malicious.jar")
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../escape/libbad.so"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
            OEngineNativeArchiveInspector.extractLinuxNativeArchive(archive, File(root, "inspection"))
        } finally {
            root.deleteRecursively()
        }
    }
}
