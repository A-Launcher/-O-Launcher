package com.alauncher.olauncher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineRendererProviderContractTest {
    @Test
    fun gl4esDescriptorUsesPinnedAndroidArtifactAndLicense() {
        val descriptor = OEngineRendererProviders.descriptor(
            OEngineGraphicsStack.RendererProvider.GL4ES
        )
        assertEquals("GL4ES", descriptor.displayName)
        assertEquals(OEngineGraphicsStack.GL4ES_LIBRARY, descriptor.expectedLibraryName)
        assertEquals(OEngineGraphicsStack.GL4ES_UPSTREAM_COMMIT, descriptor.pinnedRevision)
        assertEquals("MIT", descriptor.license)
    }

    @Test
    fun gl4esIsNotReportedAvailableWithoutARealAdapter() {
        val result = OEngineRendererProviders.inspect(
            provider = OEngineGraphicsStack.RendererProvider.GL4ES,
            adapters = emptyList(),
            privateRendererRoot = File("unused"),
            abi = "arm64-v8a"
        )
        assertFalse(result.available)
        assertTrue(result.reason.contains("no verified O Engine adapter"))
        assertEquals(OEngineGraphicsStack.GL4ES_LIBRARY, result.descriptor.expectedLibraryName)
    }

    @Test
    fun rendererSelectionRemainsExclusive() {
        OEngineRendererProviders.requireExclusiveSelection(
            setOf(OEngineGraphicsStack.RendererProvider.GL4ES)
        )
        var rejected = false
        try {
            OEngineRendererProviders.requireExclusiveSelection(
                setOf(
                    OEngineGraphicsStack.RendererProvider.LTW,
                    OEngineGraphicsStack.RendererProvider.GL4ES
                )
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
