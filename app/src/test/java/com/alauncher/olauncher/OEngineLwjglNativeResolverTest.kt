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
                "org/lwjgl/system/linux/DynamicLinkLoader.class" to lwjglClass(methodName = "other"))
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
                "org/lwjgl/system/linux/DynamicLinkLoader.class" to lwjglClass())
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
            val core = jar(root, "org/lwjgl/lwjgl/2.9.4/lwjgl-2.9.4.jar",
                "org/lwjgl/Sys.class" to "org.lwjgl.librarypath loadLibrary java/lang/System".toByteArray())
            val result = OEngineLwjglNativeResolver.resolve(listOf(core))
            assertEquals(OEngineLwjglGeneration.LWJGL2, result.generation)
            assertEquals(OEngineLwjglLoaderSurface.LEGACY_LWJGL2_SYSTEM_LOAD, result.loaderSurface)
            assertTrue(result.blockers.any { it.contains("org.lwjgl.opengl.libname") })
            assertTrue(result.blockers.any { it.contains("GL4ES") })
        } finally { root.deleteRecursively() }
    }


    @Test
    fun rejectsNdlopenConstantPoolStringsWithoutNativeStaticMethod() {
        val root = Files.createTempDirectory("oengine-lwjgl3-decoy").toFile()
        try {
            val core = jar(root, "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                "org/lwjgl/system/linux/DynamicLinkLoader.class" to lwjglClass(native = false))
            val resolution = OEngineLwjglNativeResolver.resolve(listOf(core))
            assertFalse(resolution.ndlopenSignaturePresent)
            assertTrue(resolution.blockers.any { it.contains("ndlopen(long,int)") })
        } finally { root.deleteRecursively() }
    }

    private fun lwjglClass(
        methodName: String = "ndlopen",
        descriptor: String = "(JI)J",
        native: Boolean = true,
        static: Boolean = true
    ): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(0xCAFEBABE.toInt())
            out.writeShort(0)
            out.writeShort(52)
            out.writeShort(7)
            out.writeByte(1); out.writeUTF("org/lwjgl/system/linux/DynamicLinkLoader")
            out.writeByte(7); out.writeShort(1)
            out.writeByte(1); out.writeUTF("java/lang/Object")
            out.writeByte(7); out.writeShort(3)
            out.writeByte(1); out.writeUTF(methodName)
            out.writeByte(1); out.writeUTF(descriptor)
            out.writeShort(0x0021)
            out.writeShort(2)
            out.writeShort(4)
            out.writeShort(0) // interfaces
            out.writeShort(0) // fields
            out.writeShort(1) // methods
            var access = 0x0001
            if (native) access = access or 0x0100
            if (static) access = access or 0x0008
            out.writeShort(access)
            out.writeShort(5)
            out.writeShort(6)
            out.writeShort(0) // method attributes
            out.writeShort(0) // class attributes
        }
        return bytes.toByteArray()
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
