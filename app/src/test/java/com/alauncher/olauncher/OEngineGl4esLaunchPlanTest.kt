package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineGl4esLaunchPlanTest {
    @Test
    fun acceptsGles2ButKeepsPlanBlockedUntilLwjgl2AdapterExists() {
        val root = Files.createTempDirectory("oengine-gl4es-plan").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareGl4es(
                createArtifact(root), root, "arm64-v8a",
                OEngineGlesDriver(major = 2, minor = 0),
                lwjgl2NativeLoaderVerified = false
            )

            assertFalse(plan.readyForNativeBootstrap)
            assertEquals(OEngineGraphicsStack.RendererProvider.GL4ES, plan.provider)
            assertTrue(plan.blockers.any { it.contains("LWJGL 2") })
            assertTrue(plan.preloadBeforeJvm)
            assertTrue(plan.requiresLwjglNativeLoadInterception)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun exportsGl4esCompatibilityEnvironmentSeparatelyFromLtw() {
        val root = Files.createTempDirectory("oengine-gl4es-env").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareGl4es(
                createArtifact(root), root, "arm64-v8a",
                OEngineGlesDriver(major = 2, minor = 0),
                lwjgl2NativeLoaderVerified = true,
                rendererEglLifecycleVerified = true
            )

            assertTrue(plan.readyForNativeBootstrap)
            assertEquals("true", plan.environment["force_glsl_extensions_warn"])
            assertEquals("true", plan.environment["allow_higher_compat_version"])
            assertEquals("true", plan.environment["allow_glsl_extension_directive_midshader"])
            assertEquals("1", plan.environment["LIBGL_NOERROR"])
            assertFalse(plan.environment.containsKey("LIBGL_EGL"))
        } finally {
            root.deleteRecursively()
        }
    }


    @Test
    fun blocksBootstrapWhenLwjgl2AdapterExistsButEglLifecycleIsUnverified() {
        val root = Files.createTempDirectory("oengine-gl4es-egl-block").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareGl4es(
                createArtifact(root), root, "arm64-v8a",
                OEngineGlesDriver(major = 2, minor = 0),
                lwjgl2NativeLoaderVerified = true
            )

            assertFalse(plan.readyForNativeBootstrap)
            assertTrue(plan.blockers.any { it.contains("EGL initialization") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsGlesBelowVersionTwo() {
        val root = Files.createTempDirectory("oengine-gl4es-gles1").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareGl4es(
                createArtifact(root), root, "arm64-v8a",
                OEngineGlesDriver(major = 1, minor = 1),
                lwjgl2NativeLoaderVerified = true
            )

            assertFalse(plan.readyForNativeBootstrap)
            assertTrue(plan.blockers.any { it.contains("OpenGL ES 2.0") })
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createArtifact(root: File): OEngineNativeRendererArtifact {
        val library = File(root, "arm64-v8a/${OEngineGraphicsStack.GL4ES_LIBRARY}")
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
            provider = OEngineGraphicsStack.RendererProvider.GL4ES,
            version = "test",
            abi = "arm64-v8a",
            libraryFile = library,
            license = OEngineGraphicsStack.GL4ES_LICENSE,
            upstreamRevision = OEngineGraphicsStack.GL4ES_UPSTREAM_COMMIT
        )
    }
}
