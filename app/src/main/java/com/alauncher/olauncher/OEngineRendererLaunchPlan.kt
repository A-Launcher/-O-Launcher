package com.alauncher.olauncher

import java.io.File

/**
 * Resolved host GLES driver supplied to LTW. Null paths mean the Android system
 * driver is used by LTW's normal fallback; explicit paths are passed as env vars.
 */
data class OEngineGlesDriver(
    val eglLibraryPath: String? = null,
    val glesLibraryPath: String? = null,
    val major: Int,
    val minor: Int,
    val requiresNamespaceBypass: Boolean = false
)

/** Immutable, reviewable renderer setup prepared before the game JVM is created. */
data class OEngineRendererLaunchPlan(
    val provider: OEngineGraphicsStack.RendererProvider,
    val rendererLibrary: File,
    val abi: String,
    val glesDriver: OEngineGlesDriver,
    val environment: Map<String, String>,
    val preloadBeforeJvm: Boolean,
    val requiresLwjglNativeLoadInterception: Boolean,
    val blockers: List<String>,
    val warnings: List<String>
) {
    val readyForNativeBootstrap: Boolean get() = blockers.isEmpty()
}

/**
 * Prepares LTW's native startup contract. This class never loads native code,
 * changes process environment, or claims that the LWJGL hook is installed.
 */
object OEngineRendererLaunchPlanner {
    fun prepareLtw(
        artifact: OEngineNativeRendererArtifact,
        rendererRoot: File,
        abi: String,
        glesDriver: OEngineGlesDriver,
        lwjglResolverHookVerified: Boolean,
        namespaceBypassVerified: Boolean = false
    ): OEngineRendererLaunchPlan {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val inspection = OEngineRendererArtifactGate.inspect(
            artifact = artifact,
            rendererRoot = rendererRoot,
            supportedAbis = listOf(abi)
        )

        if (artifact.provider != OEngineGraphicsStack.RendererProvider.LTW) {
            blockers += "The selected native artifact is not LTW."
        }
        if (artifact.abi != abi) {
            blockers += "LTW artifact ABI does not match the selected runtime ABI."
        }
        blockers += inspection.blockers

        if (glesDriver.major < 3) {
            blockers += "LTW requires an OpenGL ES 3.0 or newer host driver."
        } else if (glesDriver.major == 3 && glesDriver.minor < 2) {
            warnings += "The host exposes GLES 3." + glesDriver.minor + "; GLES 3.2 is preferred when available."
        }

        validateDriverPath("EGL", glesDriver.eglLibraryPath, blockers)
        validateDriverPath("GLES", glesDriver.glesLibraryPath, blockers)

        if (glesDriver.requiresNamespaceBypass && !namespaceBypassVerified) {
            blockers += "The selected EGL/GLES provider requires linker-namespace access that O Engine has not verified."
        }

        if (!lwjglResolverHookVerified) {
            blockers += "The selected Minecraft LWJGL native loader has not passed O Engine hook verification."
        }

        val environment = linkedMapOf<String, String>()
        glesDriver.eglLibraryPath?.let { environment["LIBGL_EGL"] = it }
        glesDriver.glesLibraryPath?.let { environment["LIBGL_GLES"] = it }

        val library = artifact.libraryFile.canonicalFile
        return OEngineRendererLaunchPlan(
            provider = OEngineGraphicsStack.RendererProvider.LTW,
            rendererLibrary = library,
            abi = abi,
            glesDriver = glesDriver,
            environment = environment,
            preloadBeforeJvm = true,
            requiresLwjglNativeLoadInterception = true,
            blockers = blockers.distinct(),
            warnings = warnings.distinct()
        )
    }

    /**
     * Prepares GL4ES's Android GLES2 environment without borrowing LTW's LWJGL3 hook.
     * The plan stays blocked until a provider-specific LWJGL2 loader adapter is verified.
     */
    fun prepareGl4es(
        artifact: OEngineNativeRendererArtifact,
        rendererRoot: File,
        abi: String,
        glesDriver: OEngineGlesDriver,
        lwjgl2NativeLoaderVerified: Boolean,
        rendererEglLifecycleVerified: Boolean = false,
        namespaceBypassVerified: Boolean = false,
        minecraftReleaseTime: String? = null
    ): OEngineRendererLaunchPlan {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val inspection = OEngineRendererArtifactGate.inspect(
            artifact = artifact,
            rendererRoot = rendererRoot,
            supportedAbis = listOf(abi)
        )

        if (artifact.provider != OEngineGraphicsStack.RendererProvider.GL4ES) {
            blockers += "The selected native artifact is not GL4ES."
        }
        if (artifact.abi != abi) {
            blockers += "GL4ES artifact ABI does not match the selected runtime ABI."
        }
        blockers += inspection.blockers

        if (glesDriver.major < 2) {
            blockers += "GL4ES requires an OpenGL ES 2.0 or newer host driver."
        }
        validateDriverPath("EGL", glesDriver.eglLibraryPath, blockers)
        validateDriverPath("GLES", glesDriver.glesLibraryPath, blockers)

        if (glesDriver.requiresNamespaceBypass && !namespaceBypassVerified) {
            blockers += "The selected EGL/GLES provider requires linker-namespace access that O Engine has not verified."
        }
        if (!lwjgl2NativeLoaderVerified) {
            blockers += "The selected Minecraft LWJGL 2 native loader has no verified GL4ES-specific adapter."
        }
        if (!rendererEglLifecycleVerified) {
            blockers += "GL4ES wrapper-owned EGL initialization, surface lifecycle, and GL entry-point resolution are not verified."
        }

        val environment = linkedMapOf<String, String>()
        glesDriver.eglLibraryPath?.let { environment["LIBGL_EGL"] = it }
        glesDriver.glesLibraryPath?.let { environment["LIBGL_GLES"] = it }
        val legacyOpenGl = OEngineLegacyOpenGlPolicy.resolve(minecraftReleaseTime)
        environment["LIBGL_ES"] = "2"
        environment["LIBGL_GL"] = legacyOpenGl.gl4esVersion
        environment["force_glsl_extensions_warn"] = "true"
        environment["allow_higher_compat_version"] = "true"
        environment["allow_glsl_extension_directive_midshader"] = "true"
        environment["LIBGL_NOERROR"] = "1"

        return OEngineRendererLaunchPlan(
            provider = OEngineGraphicsStack.RendererProvider.GL4ES,
            rendererLibrary = artifact.libraryFile.canonicalFile,
            abi = abi,
            glesDriver = glesDriver,
            environment = environment,
            preloadBeforeJvm = true,
            requiresLwjglNativeLoadInterception = true,
            blockers = blockers.distinct(),
            warnings = (warnings + legacyOpenGl.reason).distinct()
        )
    }

    private fun validateDriverPath(label: String, path: String?, blockers: MutableList<String>) {
        if (path.isNullOrBlank()) return // LTW uses its system EGL/GLES fallback.
        if (path == "libEGL.so" || path == "libGLESv2.so") return
        val file = File(path)
        if (!file.isAbsolute || !file.isFile || file.length() == 0L) {
            blockers += label + " driver override must be an existing absolute library path."
        }
    }
}
