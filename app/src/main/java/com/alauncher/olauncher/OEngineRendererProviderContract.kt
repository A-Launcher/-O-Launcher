package com.alauncher.olauncher

import java.io.File

/**
 * Stable engine boundary for Android desktop-GL compatibility providers.
 *
 * Implementations are intentionally not supplied until each upstream project has
 * a verified, supported Android loading contract. This contract does not load code.
 */
data class OEngineRendererProviderDescriptor(
    val provider: OEngineGraphicsStack.RendererProvider,
    val displayName: String,
    val upstream: String,
    val pinnedRevision: String,
    val license: String,
    val expectedLibraryName: String
)

data class OEngineRendererProviderAvailability(
    val descriptor: OEngineRendererProviderDescriptor,
    val available: Boolean,
    val libraryFile: File?,
    val reason: String
)

/**
 * Adapter implementations must report availability without loading native code.
 * A future load-capable extension must own and close exactly one provider session.
 */
interface OEngineRendererProviderAdapter {
    val descriptor: OEngineRendererProviderDescriptor

    fun inspect(
        privateRendererRoot: File,
        abi: String
    ): OEngineRendererProviderAvailability
}

/** Central provider catalog and exclusive-selection policy for one game session. */
object OEngineRendererProviders {
    val ltw = OEngineRendererProviderDescriptor(
        provider = OEngineGraphicsStack.RendererProvider.LTW,
        displayName = "LTW",
        upstream = "https://github.com/MojoLauncher/LTW",
        pinnedRevision = OEngineGraphicsStack.LTW_TARGET_COMMIT,
        license = OEngineGraphicsStack.LTW_LICENSE,
        expectedLibraryName = "libltw.so"
    )

    val gl4es = OEngineRendererProviderDescriptor(
        provider = OEngineGraphicsStack.RendererProvider.GL4ES,
        displayName = "GL4ES",
        upstream = "https://github.com/MojoLauncher/MojoLauncher",
        pinnedRevision = OEngineGraphicsStack.GL4ES_UPSTREAM_COMMIT,
        license = OEngineGraphicsStack.GL4ES_LICENSE,
        expectedLibraryName = OEngineGraphicsStack.GL4ES_LIBRARY
    )

    val mobileGlues = OEngineRendererProviderDescriptor(
        provider = OEngineGraphicsStack.RendererProvider.MOBILE_GLUES,
        displayName = "MobileGlues",
        upstream = "https://github.com/MobileGL-Dev/MobileGlues-release",
        pinnedRevision = "2.0.0",
        license = OEngineGraphicsStack.MOBILEGLUES_LICENSE,
        expectedLibraryName = "libmobileglues.so"
    )

    /** No provider is reported available until a verified adapter is registered. */
    fun inspect(
        provider: OEngineGraphicsStack.RendererProvider,
        adapters: List<OEngineRendererProviderAdapter>,
        privateRendererRoot: File,
        abi: String
    ): OEngineRendererProviderAvailability {
        val descriptor = descriptor(provider)
        val matches = adapters.filter { it.descriptor.provider == provider }
        if (matches.size != 1) {
            return OEngineRendererProviderAvailability(
                descriptor = descriptor,
                available = false,
                libraryFile = null,
                reason = if (matches.isEmpty()) {
                    "${descriptor.displayName} has no verified O Engine adapter."
                } else {
                    "Multiple adapters claim ${descriptor.displayName}; provider selection is ambiguous."
                }
            )
        }
        val adapter = matches.single()
        if (adapter.descriptor != descriptor) {
            return OEngineRendererProviderAvailability(
                descriptor = descriptor,
                available = false,
                libraryFile = null,
                reason = "Adapter metadata does not match O Engine's pinned provider descriptor."
            )
        }
        return adapter.inspect(privateRendererRoot, abi)
    }

    fun descriptor(provider: OEngineGraphicsStack.RendererProvider): OEngineRendererProviderDescriptor =
        when (provider) {
            OEngineGraphicsStack.RendererProvider.LTW -> ltw
            OEngineGraphicsStack.RendererProvider.GL4ES -> gl4es
            OEngineGraphicsStack.RendererProvider.MOBILE_GLUES -> mobileGlues
        }

    /** Rejects a session configuration that tries to activate both providers. */
    fun requireExclusiveSelection(selected: Set<OEngineGraphicsStack.RendererProvider>) {
        require(selected.size <= 1) {
            "LTW, GL4ES, and MobileGlues are alternative providers; select at most one per game session."
        }
    }
}
