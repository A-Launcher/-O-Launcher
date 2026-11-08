package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class OEngineGraphicsDriverEnvironmentTest {
    @Test fun preparesZinkOnlyWithDriverAndVulkanIcd() {
        val root = Files.createTempDirectory("oengine-zink").toFile()
        try {
            val zink = File(root, "arm64-v8a/dri/zink_dri.so").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val vulkan = File(root, "arm64-v8a/vulkan/libvulkan_freedreno.so").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(4, 5, 6))
            }
            val manifest = File(root, "arm64-v8a/vulkan/icd.d/oengine-vulkan.json").apply {
                parentFile.mkdirs()
                writeText("""{"file_format_version":"1.0.0","ICD":{"library_path":"../libvulkan_freedreno.so","api_version":"1.3.0"}}""")
            }

            val plan = OEngineGraphicsDriverEnvironment.prepareZink(root, "arm64-v8a", zink, manifest)

            assertEquals("zink", plan.environment["MESA_LOADER_DRIVER_OVERRIDE"])
            assertEquals("zink", plan.environment["GALLIUM_DRIVER"])
            assertEquals(manifest.canonicalPath, plan.environment["VK_ICD_FILENAMES"])
            assertEquals(vulkan.canonicalFile, plan.vulkanDriver)
            assertTrue(plan.librarySearchPaths.contains(zink.parentFile))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsMissingZinkDriver() {
        val root = Files.createTempDirectory("oengine-zink-missing").toFile()
        try {
            val manifest = File(root, "arm64-v8a/icd.json").apply {
                parentFile.mkdirs()
                writeText("""{"ICD":{"library_path":"/system/lib64/libvulkan.so"}}""")
            }
            assertThrows(IllegalArgumentException::class.java) {
                OEngineGraphicsDriverEnvironment.prepareZink(root, "arm64-v8a", File(root, "missing.so"), manifest)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsMissingVulkanIcdLibrary() {
        val root = Files.createTempDirectory("oengine-zink-no-icd").toFile()
        try {
            val zink = File(root, "arm64-v8a/zink_dri.so").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1)) }
            val manifest = File(root, "arm64-v8a/icd.json").apply {
                writeText("""{"ICD":{"library_path":"missing-vulkan.so"}}""")
            }
            assertThrows(IllegalArgumentException::class.java) {
                OEngineGraphicsDriverEnvironment.prepareZink(root, "arm64-v8a", zink, manifest)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
