package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineRendererLaunchPlanTest {
    @Test
    fun requiresVerifiedLwjglResolverHookBeforeNativeBootstrap() {
        val root = Files.createTempDirectory("oengine-ltw-plan").toFile()
        try {
            val artifact = createArtifact(root)
            val plan = OEngineRendererLaunchPlanner.prepareLtw(
                artifact, root, "arm64-v8a",
                OEngineGlesDriver(major = 3, minor = 2),
                lwjglResolverHookVerified = false
            )

            assertFalse(plan.readyForNativeBootstrap)
            assertTrue(plan.blockers.any { it.contains("LWJGL native loader") })
            assertTrue(plan.preloadBeforeJvm)
            assertTrue(plan.requiresLwjglNativeLoadInterception)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsValidatedLtwPlanWhenGlesAndResolverAreReady() {
        val root = Files.createTempDirectory("oengine-ltw-ready").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareLtw(
                createArtifact(root), root, "arm64-v8a",
                OEngineGlesDriver(major = 3, minor = 2),
                lwjglResolverHookVerified = true
            )

            assertTrue(plan.readyForNativeBootstrap)
            assertEquals(OEngineGraphicsStack.RendererProvider.LTW, plan.provider)
            assertTrue(plan.environment.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsGlesBelowVersionThree() {
        val root = Files.createTempDirectory("oengine-ltw-gles2").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareLtw(
                createArtifact(root), root, "arm64-v8a",
                OEngineGlesDriver(major = 2, minor = 0),
                lwjglResolverHookVerified = true
            )

            assertFalse(plan.readyForNativeBootstrap)
            assertTrue(plan.blockers.any { it.contains("OpenGL ES 3.0") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun blocksExternalDriverWhenNamespaceAccessIsUnverified() {
        val root = Files.createTempDirectory("oengine-ltw-namespace").toFile()
        try {
            val egl = File(root, "libEGL_angle.so").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val plan = OEngineRendererLaunchPlanner.prepareLtw(
                createArtifact(root), root, "arm64-v8a",
                OEngineGlesDriver(egl.absolutePath, major = 3, minor = 2, requiresNamespaceBypass = true),
                lwjglResolverHookVerified = true
            )

            assertFalse(plan.readyForNativeBootstrap)
            assertTrue(plan.blockers.any { it.contains("linker-namespace") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun exportsExplicitHostDriverPathsUsingLtwEnvironmentContract() {
        val root = Files.createTempDirectory("oengine-ltw-env").toFile()
        try {
            val egl = File(root, "libEGL_angle.so").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val gles = File(root, "libGLESv2_angle.so").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val plan = OEngineRendererLaunchPlanner.prepareLtw(
                createArtifact(root), root, "arm64-v8a",
                OEngineGlesDriver(egl.absolutePath, gles.absolutePath, 3, 1),
                lwjglResolverHookVerified = true
            )

            assertTrue(plan.readyForNativeBootstrap)
            assertEquals(egl.absolutePath, plan.environment["LIBGL_EGL"])
            assertEquals(gles.absolutePath, plan.environment["LIBGL_GLES"])
            assertTrue(plan.warnings.any { it.contains("GLES 3.1") })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createArtifact(root: File): OEngineNativeRendererArtifact {
        val library = File(root, "arm64-v8a/libltw.so")
        library.parentFile?.mkdirs()
        val bytes = ByteArray(128)
        bytes[0] = 0x7f
        bytes[1] = 0x45
        bytes[2] = 0x4c
        bytes[3] = 0x46
        bytes[4] = 2
        bytes[5] = 1
        bytes[18] = 183.toByte()
        library.writeBytes(bytes)
        return OEngineNativeRendererArtifact(
            provider = OEngineGraphicsStack.RendererProvider.LTW,
            version = "test",
            abi = "arm64-v8a",
            libraryFile = library,
            license = OEngineGraphicsStack.LTW_LICENSE,
            upstreamRevision = OEngineGraphicsStack.LTW_TARGET_COMMIT
        )
    }
}
