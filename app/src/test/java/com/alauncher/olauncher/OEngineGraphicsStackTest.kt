package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineGraphicsStackTest {

    @Test
    fun ltwRejectsUnknownOrMalformedGlesVersionInsteadOfAssumingMinimum() {
        assertEquals(3, OEngineEglBootstrap.parseGlesMajor("OpenGL ES 3.2 V@145.0"))
        assertEquals(3, OEngineEglBootstrap.parseGlesMajor("OpenGL ES 3"))
        assertEquals(null, OEngineEglBootstrap.parseGlesMajor("OpenGL 3.2"))
        assertEquals(null, OEngineEglBootstrap.parseGlesMajor("unknown driver string"))
    }

    @Test
    fun ltwRequiresOpenGlesThreeAndUsesRendererEnvironmentDefaults() {
        assertTrue(OEngineGraphicsStack.ltwCompatibilityBlocker(2)?.contains("OpenGL ES 3") == true)
        assertEquals(null, OEngineGraphicsStack.ltwCompatibilityBlocker(3))
        assertEquals(null, OEngineGraphicsStack.ltwCompatibilityBlocker(4))
        assertEquals(
            mapOf(
                "force_glsl_extensions_warn" to "true",
                "allow_higher_compat_version" to "true",
                "allow_glsl_extension_directive_midshader" to "true",
                "LIBGL_NOERROR" to "1"
            ),
            OEngineGraphicsStack.ltwEnvironment()
        )
    }

    @Test
    fun preservesMinecraftPinnedLwjglVersionAsReferenceOnly() {
        val assessment = OEngineGraphicsStack.assess(
            classpath = listOf("/game/libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"),
            hostGlesMajor = 3,
            hostGlesMinor = 2,
            selectedProvider = OEngineGraphicsStack.RendererProvider.LTW,
            ltwAvailable = true,
            mobileGluesAvailable = false
        )

        assertTrue(assessment.blockers.isEmpty())
        assertEquals(setOf("3.3.3"), assessment.lwjglModules["lwjgl"])
        assertTrue(assessment.warnings.any { it.contains("metadata-pinned LWJGL versions") })
    }

    @Test
    fun enforcesMobileGluesGlesMinimumOnlyWhenSelected() {
        val classpath = listOf("/game/libraries/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3.jar")

        val ltwUnsupported = OEngineGraphicsStack.assess(
            classpath, 2, 0,
            OEngineGraphicsStack.RendererProvider.LTW,
            ltwAvailable = true,
            mobileGluesAvailable = false
        )
        val ltwSupported = OEngineGraphicsStack.assess(
            classpath, 3, 0,
            OEngineGraphicsStack.RendererProvider.LTW,
            ltwAvailable = true,
            mobileGluesAvailable = false
        )
        val mobileGlues = OEngineGraphicsStack.assess(
            classpath, 2, 0,
            OEngineGraphicsStack.RendererProvider.MOBILE_GLUES,
            ltwAvailable = false,
            mobileGluesAvailable = true
        )

        assertTrue(ltwUnsupported.blockers.any { it.contains("OpenGL ES 3") })
        assertTrue(ltwSupported.blockers.isEmpty())
        assertTrue(mobileGlues.blockers.any { it.contains("OpenGL ES 3.0") })
    }

    @Test
    fun providerIsUnavailableUntilExactlyOnePinnedAdapterExists() {
        val root = Files.createTempDirectory("oengine-provider").toFile()
        try {
            val unavailable = OEngineRendererProviders.inspect(
                OEngineGraphicsStack.RendererProvider.LTW,
                adapters = emptyList(),
                privateRendererRoot = root,
                abi = "arm64-v8a"
            )
            assertFalse(unavailable.available)

            val pinnedDescriptor = OEngineRendererProviders.ltw
            val adapter = object : OEngineRendererProviderAdapter {
                override val descriptor = pinnedDescriptor
                override fun inspect(
                    privateRendererRoot: File,
                    abi: String
                ) = OEngineRendererProviderAvailability(
                    descriptor = pinnedDescriptor,
                    available = true,
                    libraryFile = File(privateRendererRoot, abi + "/libltw.so"),
                    reason = "Test adapter"
                )
            }
            val available = OEngineRendererProviders.inspect(
                OEngineGraphicsStack.RendererProvider.LTW,
                adapters = listOf(adapter),
                privateRendererRoot = root,
                abi = "arm64-v8a"
            )
            assertTrue(available.available)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSelectingBothRendererProviders() {
        OEngineRendererProviders.requireExclusiveSelection(
            setOf(
                OEngineGraphicsStack.RendererProvider.LTW,
                OEngineGraphicsStack.RendererProvider.MOBILE_GLUES
            )
        )
    }
}
