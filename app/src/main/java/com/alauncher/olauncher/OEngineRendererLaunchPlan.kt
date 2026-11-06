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
        lwjglResolverHookVerified: Boolean
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

        if (!lwjglResolverHookVerified) {
            blockers += "The selected Minecraft LWJGL native loader has no verified O Engine EGL-handle interception adapter."
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

    private fun validateDriverPath(label: String, path: String?, blockers: MutableList<String>) {
        if (path.isNullOrBlank()) return // LTW uses its system EGL/GLES fallback.
        if (path == "libEGL.so" || path == "libGLESv2.so") return
        val file = File(path)
        if (!file.isAbsolute || !file.isFile || file.length() == 0L) {
            blockers += label + " driver override must be an existing absolute library path."
        }
    }
}
