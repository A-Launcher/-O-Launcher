package com.alauncher.olauncher

/**
 * Resolves the Java bytecode/runtime era for a Minecraft version.
 *
 * Mojang's javaVersion.majorVersion is authoritative when present. The
 * version-range table is only a fallback for legacy metadata that predates
 * this field. Android runtime availability is separate from the game's
 * historical Java target: Java 8 can execute Java 5/6-era bytecode, and
 * Java 17 can execute Java 16-era bytecode.
 */
data class OEngineJavaCompatibility(
    val targetJavaMajor: Int,
    val runtimeJavaMajor: Int,
    val source: Source,
    val explanation: String
) {
    enum class Source { MOJANG_METADATA, LEGACY_VERSION_POLICY }

    /** Java majors whose HotSpot native-library hook ABI is implemented in O Engine. */
    fun nativeLaunchBlocker(): String? =
        if (runtimeJavaMajor in SUPPORTED_NATIVE_HOOK_JAVA_MAJORS) null
        else "Minecraft requires Java $runtimeJavaMajor, but O Engine's Android native-library hook currently supports Java 8, 17, and 21 only. This version cannot launch until Java $runtimeJavaMajor is integrated."

    companion object {
        val SUPPORTED_NATIVE_HOOK_JAVA_MAJORS = setOf(8, 17, 21)
        fun resolve(versionId: String, metadataMajor: Int? = null): OEngineJavaCompatibility {
            val target = metadataMajor?.takeIf { it > 0 } ?: inferLegacyTarget(versionId)
            val runtime = when (target) {
                5, 6 -> 8
                16 -> 17
                else -> target
            }
            val source = if (metadataMajor != null && metadataMajor > 0) {
                Source.MOJANG_METADATA
            } else {
                Source.LEGACY_VERSION_POLICY
            }
            val explanation = when {
                target == 5 || target == 6 ->
                    "Legacy Java $target target; use Android Java 8 runtime for backward-compatible bytecode."
                target == 16 ->
                    "Java 16 target; use Android Java 17 runtime for backward-compatible execution."
                source == Source.MOJANG_METADATA ->
                    "Java $target selected from Mojang version metadata."
                else ->
                    "Java $target selected from the legacy Minecraft version compatibility table."
            }
            return OEngineJavaCompatibility(target, runtime, source, explanation)
        }

        private fun inferLegacyTarget(versionId: String): Int {
            val match = Regex("""^[a-zA-Z]*(\d+)\.(\d+)(?:\.(\d+))?""").find(versionId)
                ?: return 5 // Pre-Classic/Classic/indev/infdev and unparseable legacy IDs.
            val major = match.groupValues[1].toIntOrNull() ?: return 5
            val minor = match.groupValues[2].toIntOrNull() ?: return 5
            val patch = match.groupValues[3].toIntOrNull() ?: 0

            if (major >= 26) return 25
            if (major > 1) return 25
            return when {
                minor < 6 || (minor == 5 && patch <= 2) -> 5
                minor in 6..11 -> 6
                minor in 12..16 -> 8
                minor == 17 -> 16
                minor == 18 || minor == 19 -> 17
                minor == 20 && patch <= 4 -> 17
                minor == 20 && patch >= 5 -> 21
                minor == 21 -> 21
                minor >= 22 -> 21
                else -> 8
            }
        }
    }
}
