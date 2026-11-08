package com.alauncher.olauncher

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineAndroidGlfwArtifactInstallerTest {
    @Test
    fun stagesAbiMatchedLibrariesAndWritesVerifiedManifest() {
        val root = Files.createTempDirectory("oengine-android-glfw").toFile()
        try {
            val source = File(root, "apk-libs").apply { mkdirs() }
            val destination = File(root, "private/oengine/android-glfw")
            listOf(
                "libglfw.so", "libmojoexec.so", "liblinkerhook.so", "liblwjgl.so",
                "liblwjgl_opengl.so", "liblwjgl_nanovg.so", "liblwjgl_stb.so",
                "liblwjgl_tinyfd.so", "libopenal.so", "libfopenhook.so"
            ).forEachIndexed { index, name ->
                File(source, name).writeBytes(fakeElf(183, 64, index.toByte()))
            }

            val result = OEngineAndroidGlfwArtifactInstaller.install(source, destination, "arm64-v8a")

            assertEquals(10, result.libraries.size)
            assertTrue(result.libraries.all { it.isFile && it.length() > 0L })
            assertEquals(64, result.sha256ByLibrary.values.first().length)
            val manifest = result.manifest.readText()
            assertTrue(manifest.contains("\"abi\":\"arm64-v8a\""))
            assertEquals(10, Regex("\"name\"").findAll(manifest).count())
            assertTrue(manifest.contains("java.library.path and LD_LIBRARY_PATH"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun stagesOpenAlAndLwjglForArm64AndArm32() {
        val root = Files.createTempDirectory("oengine-arm-native-abis").toFile()
        try {
            listOf(
                Triple("arm64-v8a", 183, 64),
                Triple("armeabi-v7a", 40, 32)
            ).forEach { (abi, machine, bits) ->
                val source = File(root, "$abi/apk-libs").apply { mkdirs() }
                val destination = File(root, "$abi/private")
                listOf(
                    "libglfw.so", "libmojoexec.so", "liblinkerhook.so", "liblwjgl.so",
                    "liblwjgl_opengl.so", "liblwjgl_nanovg.so", "liblwjgl_stb.so",
                    "liblwjgl_tinyfd.so", "libopenal.so"
                ).forEach { name -> File(source, name).writeBytes(fakeElf(machine, bits)) }
                if (abi == "arm64-v8a") File(source, "libfopenhook.so").writeBytes(fakeElf(machine, bits))

                val result = OEngineAndroidGlfwArtifactInstaller.install(source, destination, abi)

                assertTrue(result.libraries.any { it.name == "libopenal.so" })
                assertTrue(result.libraries.any { it.name == "liblwjgl.so" })
                assertEquals(abi, result.abi)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsWrongAbiBeforeCopyingAnyLibrary() {
        val root = Files.createTempDirectory("oengine-android-glfw-wrong-abi").toFile()
        try {
            val source = File(root, "apk-libs").apply { mkdirs() }
            listOf("libglfw.so", "libmojoexec.so", "liblinkerhook.so").forEach {
                File(source, it).writeBytes(fakeElf(62, 64))
            }
            val destination = File(root, "private")
            val error = runCatching {
                OEngineAndroidGlfwArtifactInstaller.install(source, destination, "arm64-v8a")
            }.exceptionOrNull()

            assertTrue(error?.message.orEmpty().contains("ELF/ABI checks"))
            assertFalse(File(destination, "arm64-v8a/libglfw.so").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnsupportedAbi() {
        val root = Files.createTempDirectory("oengine-android-glfw-abi").toFile()
        try {
            val error = runCatching {
                OEngineAndroidGlfwArtifactInstaller.install(root, File(root, "out"), "mips")
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun fakeElf(machine: Int, bits: Int, marker: Byte = 1): ByteArray {
        val bytes = ByteArray(64) { marker }
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = if (bits == 64) 2 else 1
        bytes[5] = 1
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(18, machine.toShort())
        return bytes
    }
}
