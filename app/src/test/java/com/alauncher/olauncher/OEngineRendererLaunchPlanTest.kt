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

    @Test
    fun gl4esRequiresBothTheLwjgl2LoaderAndItsOwnEglLifecycle() {
        val root = Files.createTempDirectory("oengine-gl4es-gates").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareGl4es(
                createArtifact(root, OEngineGraphicsStack.RendererProvider.GL4ES, "libgl4es_114.so"),
                root,
                "arm64-v8a",
                OEngineGlesDriver(major = 2, minor = 0),
                lwjgl2NativeLoaderVerified = false,
                rendererEglLifecycleVerified = false
            )

            assertFalse(plan.readyForNativeBootstrap)
            assertTrue(plan.blockers.any { it.contains("LWJGL 2 native loader") })
            assertTrue(plan.blockers.any { it.contains("EGL initialization") })
            assertEquals(OEngineGraphicsStack.RendererProvider.GL4ES, plan.provider)
            assertTrue(plan.preloadBeforeJvm)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun gl4esPlanRequiresValidArtifactAndExportsLegacyCompatibilityEnvironment() {
        val root = Files.createTempDirectory("oengine-gl4es-ready").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareGl4es(
                createArtifact(root, OEngineGraphicsStack.RendererProvider.GL4ES, "libgl4es_114.so"),
                root,
                "arm64-v8a",
                OEngineGlesDriver(major = 2, minor = 0),
                lwjgl2NativeLoaderVerified = true,
                rendererEglLifecycleVerified = true,
                minecraftReleaseTime = "2011-06-07T23:59:59Z"
            )

            assertTrue(plan.readyForNativeBootstrap)
            assertEquals("2", plan.environment["LIBGL_ES"])
            assertEquals("15", plan.environment["LIBGL_GL"])
            assertEquals("true", plan.environment["force_glsl_extensions_warn"])
            assertEquals("true", plan.environment["allow_higher_compat_version"])
            assertEquals("true", plan.environment["allow_glsl_extension_directive_midshader"])
            assertEquals("1", plan.environment["LIBGL_NOERROR"])
            assertTrue(plan.warnings.any { it.contains("predates 2011-06-08") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun gl4esUsesOpenGl21ForMinecraftAtOrAfterLegacyCutoff() {
        val root = Files.createTempDirectory("oengine-gl4es-modern-legacy").toFile()
        try {
            val plan = OEngineRendererLaunchPlanner.prepareGl4es(
                createArtifact(root, OEngineGraphicsStack.RendererProvider.GL4ES, "libgl4es_114.so"),
                root,
                "arm64-v8a",
                OEngineGlesDriver(major = 2, minor = 0),
                lwjgl2NativeLoaderVerified = true,
                rendererEglLifecycleVerified = true,
                minecraftReleaseTime = "2011-06-08T00:00:00Z"
            )

            assertEquals("21", plan.environment["LIBGL_GL"])
            assertTrue(plan.readyForNativeBootstrap)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createArtifact(
        root: File,
        provider: OEngineGraphicsStack.RendererProvider = OEngineGraphicsStack.RendererProvider.LTW,
        libraryName: String = "libltw.so"
    ): OEngineNativeRendererArtifact {
        val library = File(root, "arm64-v8a/$libraryName")
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
            provider = provider,
            version = "test",
            abi = "arm64-v8a",
            libraryFile = library,
            license = if (provider == OEngineGraphicsStack.RendererProvider.LTW)
                OEngineGraphicsStack.LTW_LICENSE else "MIT",
            upstreamRevision = OEngineGraphicsStack.LTW_TARGET_COMMIT
        )
    }
}
