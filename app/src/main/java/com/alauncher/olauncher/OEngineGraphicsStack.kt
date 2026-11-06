package com.alauncher.olauncher

/**
 * Version policy for the graphics stack.
 *
 * LWJGL is game-version-owned: Mojang's version metadata determines the exact
 * LWJGL modules that belong on a given Minecraft classpath. The latest upstream
 * version below is a compatibility reference, not an instruction to replace them.
 *
 * LTW and MobileGlues are selectable Android GL compatibility providers. O Engine
 * keeps their provider lifecycles separate; only one is selected for a game session.
 * LTW is built from its pinned upstream source in CI, packaged as a separate AAR,
 * copied into O Engine private storage, and explicitly preloaded in the game process.
 * MobileGlues remains a catalog-only provider until its own adapter is integrated. GL4ES is
 * packaged as a pinned Android compatibility dependency, but is not a selectable or active
 * provider until the LWJGL 2 loading adapter and EGL path are verified.
 */
object OEngineGraphicsStack {
    const val LWJGL_LATEST_REFERENCE = "3.4.3"
    const val LTW_TARGET_COMMIT = "3f1f4df6c80c925ea34067b1d943a84acce0184d"
    const val LTW_LICENSE = "LGPL-3.0"
    const val LTW_LWJGL_LIBRARY_ALIAS = "libGLMojo.so"
    const val MOBILEGLUES_LICENSE = "LGPL-2.1"
    const val MOBILEGLUES_TARGET_VERSION = "2.0.0"
    const val MOBILEGLUES_MIN_GLES_MAJOR = 3
    const val MOBILEGLUES_MIN_GLES_MINOR = 0
    const val MOBILEGLUES_PREFERRED_GLES_MAJOR = 3
    const val MOBILEGLUES_PREFERRED_GLES_MINOR = 2
    const val GL4ES_LIBRARY = "libgl4es_114.so"
    const val GL4ES_UPSTREAM_COMMIT = "1b8d90424c1cb791321d45f60ac62c54f4b013fa"
    const val GL4ES_LICENSE = "MIT"

    private val lwjglArtifactPattern = Regex(
        """/org/lwjgl/(lwjgl(?:-[a-z0-9-]+)?)/([0-9][^/]+)/"""
    )

    enum class RendererProvider { LTW, GL4ES, MOBILE_GLUES }

    data class Assessment(
        val lwjglModules: Map<String, Set<String>>,
        val hostGlesMajor: Int,
        val hostGlesMinor: Int,
        val selectedProvider: RendererProvider,
        val ltwTargetCommit: String,
        val mobileGluesTarget: String,
        val blockers: List<String>,
        val warnings: List<String>
    ) {
        val hostMeetsMobileGluesMinimum: Boolean
            get() = hostGlesMajor > MOBILEGLUES_MIN_GLES_MAJOR ||
                (hostGlesMajor == MOBILEGLUES_MIN_GLES_MAJOR &&
                    hostGlesMinor >= MOBILEGLUES_MIN_GLES_MINOR)
    }

    /**
     * Inspect the actual game classpath and the probed Android GLES version.
     * This is a preflight assessment only; it does not claim MobileGlues is loaded.
     */
    fun assess(
        classpath: List<String>,
        hostGlesMajor: Int,
        hostGlesMinor: Int,
        selectedProvider: RendererProvider,
        ltwAvailable: Boolean,
        mobileGluesAvailable: Boolean
    ): Assessment {
        require(hostGlesMajor >= 0 && hostGlesMinor >= 0)
        val modules = linkedMapOf<String, MutableSet<String>>()
        classpath.forEach { path ->
            lwjglArtifactPattern.find(path.replace('\\', '/'))?.let { match ->
                modules.getOrPut(match.groupValues[1]) { linkedSetOf() }
                    .add(match.groupValues[2])
            }
        }

        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (modules.isEmpty()) {
            blockers += "No LWJGL modules were detected in the Minecraft classpath."
        }
        if (selectedProvider == RendererProvider.MOBILE_GLUES) {
            if (hostGlesMajor < MOBILEGLUES_MIN_GLES_MAJOR ||
                (hostGlesMajor == MOBILEGLUES_MIN_GLES_MAJOR &&
                    hostGlesMinor < MOBILEGLUES_MIN_GLES_MINOR)
            ) {
                blockers += "MobileGlues $MOBILEGLUES_TARGET_VERSION requires a host OpenGL ES 3.0 or newer backend."
            } else if (hostGlesMajor == MOBILEGLUES_MIN_GLES_MAJOR &&
                hostGlesMinor < MOBILEGLUES_PREFERRED_GLES_MINOR
            ) {
                warnings += "OpenGL ES $hostGlesMajor.$hostGlesMinor meets the minimum; MobileGlues works best with ES 3.2."
            }
        }

        modules.filterValues { it.size > 1 }.forEach { (module, versions) ->
            warnings += "Multiple versions of $module were found: " + versions.joinToString()
        }
        when (selectedProvider) {
            RendererProvider.LTW -> if (!ltwAvailable) {
                blockers += "LTW provider is not installed or exposed to O Engine yet."
            }
            RendererProvider.GL4ES -> {
                // The artifact can be installed and ELF-checked, but its EGL/GL adapter is not yet device-verified.
                blockers += "GL4ES artifact staging is available, but renderer initialization and EGL handoff are not yet device-verified."
            }
            RendererProvider.MOBILE_GLUES -> if (!mobileGluesAvailable) {
                blockers += "MobileGlues provider is not installed or exposed to O Engine yet."
            }
        }
        if (modules.values.flatten().any { it != LWJGL_LATEST_REFERENCE }) {
            warnings += "Keep Minecraft's metadata-pinned LWJGL versions; $LWJGL_LATEST_REFERENCE is the upstream reference, not a forced upgrade."
        }

        return Assessment(
            lwjglModules = modules.mapValues { it.value.toSet() },
            hostGlesMajor = hostGlesMajor,
            hostGlesMinor = hostGlesMinor,
            selectedProvider = selectedProvider,
            ltwTargetCommit = LTW_TARGET_COMMIT,
            mobileGluesTarget = MOBILEGLUES_TARGET_VERSION,
            blockers = blockers.distinct(),
            warnings = warnings.distinct()
        )
    }
}
