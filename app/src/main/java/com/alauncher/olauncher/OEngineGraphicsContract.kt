package com.alauncher.olauncher

/** Stable engine-facing graphics contract; game code must not depend on Android EGL types. */
data class OEngineGraphicsCapabilities(
    val eglVersion: String,
    val apiVersion: String,
    val vendor: String,
    val renderer: String,
    val apiMajor: Int,
    val extensions: Set<String>,
    val isEmbeddedProfile: Boolean
)

interface OEngineGraphicsBackend : AutoCloseable {
    val capabilities: OEngineGraphicsCapabilities
    fun makeCurrent()
    fun setSwapInterval(interval: Int)
    fun swapBuffers(): Boolean
}

data class OEngineGraphicsCompatibility(
    val compatible: Boolean,
    val requestedProfile: String,
    val availableProfile: String,
    val reasons: List<String>
)

/** OpenGL ES is never treated as desktop OpenGL, even when extensions overlap. */
object OEngineGraphicsCompatibilityGate {
    fun evaluateDesktopOpenGl(
        capabilities: OEngineGraphicsCapabilities,
        requiredMajor: Int,
        requiredMinor: Int
    ): OEngineGraphicsCompatibility {
        require(requiredMajor >= 1 && requiredMinor >= 0)
        val reasons = mutableListOf<String>()
        if (capabilities.isEmbeddedProfile) {
            reasons += "The device backend exposes OpenGL ES, not the desktop OpenGL API."
        } else {
            val versionParts = capabilities.apiVersion.substringBefore(" ").split(".")
            val actualMajor = versionParts.getOrNull(0)?.toIntOrNull()
            val actualMinor = versionParts.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull()
            if (actualMajor == null || actualMinor == null) {
                reasons += "The desktop OpenGL version string could not be parsed."
            } else if (actualMajor < requiredMajor ||
                (actualMajor == requiredMajor && actualMinor < requiredMinor)
            ) {
                reasons += "Desktop OpenGL $requiredMajor.$requiredMinor is required, but $actualMajor.$actualMinor is available."
            }
        }
        return OEngineGraphicsCompatibility(
            compatible = reasons.isEmpty(),
            requestedProfile = "Desktop OpenGL $requiredMajor.$requiredMinor",
            availableProfile = capabilities.apiVersion,
            reasons = reasons
        )
    }
}
