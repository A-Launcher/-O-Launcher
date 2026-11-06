package com.alauncher.olauncher

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineElfCompatibilityInspectorTest {
    private fun elf(machine: Int, marker: String? = null): ByteArray {
        val bytes = ByteArray(128)
        bytes[0] = 0x7f
        bytes[1] = 'E'.code.toByte()
        bytes[2] = 'L'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 2
        bytes[5] = 1
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(18, machine.toShort())
        marker?.toByteArray()?.copyInto(bytes, 64)
        return bytes
    }

    @Test
    fun recognizesMatchingArm64ButDoesNotCertifyItAsAndroidCompatible() {
        val root = Files.createTempDirectory("oengine-elf-arm64").toFile()
        try {
            val file = File(root, "libexample.so").apply { writeBytes(elf(183)) }
            val result = OEngineElfCompatibilityInspector.inspect(file, "arm64-v8a")
            assertEquals("arm64-v8a", result.detectedAndroidAbi)
            assertEquals(OEngineElfCompatibility.ANDROID_COMPATIBILITY_UNVERIFIED, result.compatibility)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun detectsCommonGlibcMarkersEvenWhenMachineMatches() {
        val root = Files.createTempDirectory("oengine-elf-glibc").toFile()
        try {
            val file = File(root, "libexample.so").apply { writeBytes(elf(183, "GLIBC_2.17")) }
            val result = OEngineElfCompatibilityInspector.inspect(file, "arm64-v8a")
            assertEquals(OEngineElfCompatibility.LIKELY_GLIBC, result.compatibility)
            assertTrue(result.glibcMarkers.contains("GLIBC_"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMismatchedMachineAndInvalidFiles() {
        val root = Files.createTempDirectory("oengine-elf-mismatch").toFile()
        try {
            val mismatch = File(root, "x86.so").apply { writeBytes(elf(62)) }
            val invalid = File(root, "not-elf.so").apply { writeText("not an ELF") }
            assertEquals(
                OEngineElfCompatibility.ABI_MISMATCH,
                OEngineElfCompatibilityInspector.inspect(mismatch, "arm64-v8a").compatibility
            )
            assertEquals(
                OEngineElfCompatibility.INVALID_ELF,
                OEngineElfCompatibilityInspector.inspect(invalid, "arm64-v8a").compatibility
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
