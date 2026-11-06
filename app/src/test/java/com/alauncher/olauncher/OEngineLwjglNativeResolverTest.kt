package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineLwjglNativeResolverTest {
    @Test
    fun blocksUpstreamLwjgl3WhenNdlopenSignatureIsAbsent() {
        val root = Files.createTempDirectory("oengine-lwjgl3").toFile()
        try {
            val core = jar(root, "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                "org/lwjgl/system/linux/DynamicLinkLoader.class" to "dlopen dlsym dlclose".toByteArray())
            val resolution = OEngineLwjglNativeResolver.resolve(listOf(core))

            assertEquals(OEngineLwjglGeneration.LWJGL3, resolution.generation)
            assertEquals(OEngineLwjglLoaderSurface.UPSTREAM_LWJGL3_LINUX, resolution.loaderSurface)
            assertTrue(resolution.dynamicLinkLoaderClassPresent)
            assertFalse(resolution.ndlopenSignaturePresent)
            assertTrue(resolution.blockers.any { it.contains("ndlopen(long,int)") })
        } finally { root.deleteRecursively() }
    }

    @Test
    fun recognizesUpstreamNdlopenSignatureForOEngineHook() {
        val root = Files.createTempDirectory("oengine-mojo-marker").toFile()
        try {
            val core = jar(root, "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                "org/lwjgl/system/linux/DynamicLinkLoader.class" to "ndlopen (JI)J".toByteArray())
            val resolution = OEngineLwjglNativeResolver.resolve(listOf(core))

            assertEquals(OEngineLwjglLoaderSurface.UPSTREAM_LWJGL3_LINUX, resolution.loaderSurface)
            assertTrue(resolution.ndlopenSignaturePresent)
            assertTrue(resolution.blockers.isEmpty())
            assertTrue(resolution.warnings.any { it.contains("register its retained-renderer ndlopen hook") })
        } finally { root.deleteRecursively() }
    }

    @Test
    fun blocksMixedLwjglGenerationsAndConflictingModuleVersions() {
        val root = Files.createTempDirectory("oengine-lwjgl-mixed").toFile()
        try {
            val lwjgl2 = jar(root, "org/lwjgl/lwjgl/2.9.4/lwjgl-2.9.4.jar")
            val lwjgl3 = jar(root, "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                "org/lwjgl/system/linux/DynamicLinkLoader.class" to byteArrayOf(1))
            val lwjglGlfw = jar(root, "org/lwjgl/lwjgl-glfw/3.3.2/lwjgl-glfw-3.3.2.jar")
            val result = OEngineLwjglNativeResolver.resolve(listOf(lwjgl2, lwjgl3, lwjglGlfw))

            assertEquals(OEngineLwjglGeneration.UNKNOWN, result.generation)
            assertTrue(result.blockers.any { it.contains("mixes LWJGL major generations") })
        } finally { root.deleteRecursively() }
    }

    @Test
    fun recognizesLwjgl2AsLegacyAndUnsupported() {
        val root = Files.createTempDirectory("oengine-lwjgl2").toFile()
        try {
            val core = jar(root, "org/lwjgl/lwjgl/2.9.4/lwjgl-2.9.4.jar")
            val result = OEngineLwjglNativeResolver.resolve(listOf(core))
            assertEquals(OEngineLwjglGeneration.LWJGL2, result.generation)
            assertEquals(OEngineLwjglLoaderSurface.LEGACY_LWJGL2, result.loaderSurface)
            assertTrue(result.blockers.isNotEmpty())
        } finally { root.deleteRecursively() }
    }

    private fun jar(root: File, relative: String, vararg entries: Pair<String, ByteArray>): File {
        val file = File(root, relative)
        file.parentFile?.mkdirs()
        JarOutputStream(file.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(JarEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
        }
        return file
    }
}
