package com.alauncher.olauncher

/**
 * Exact JDK-internal native-library hook contracts.
 *
 * These signatures are version-specific and must be verified against the selected runtime.
 * They are based on the upstream Android launcher compatibility research recorded in
 * ENGINE_UPSTREAM_COMPONENTS.md. Unknown runtime majors fail closed.
 */
data class OEngineNativeLoadMethod(
    val ownerClass: String,
    val methodName: String,
    val descriptor: String
)

data class OEngineJvmNativeLoadProfile(
    val javaMajor: Int,
    val methods: List<OEngineNativeLoadMethod>
) {
    companion object {
        private val profiles = mapOf(
            8 to OEngineJvmNativeLoadProfile(
                8,
                listOf(OEngineNativeLoadMethod(
                    "java/lang/ClassLoader\$NativeLibrary",
                    "load",
                    "(Ljava/lang/String;Z)V"
                ))
            ),
            17 to OEngineJvmNativeLoadProfile(
                17,
                listOf(
                    OEngineNativeLoadMethod(
                        "jdk/internal/loader/NativeLibraries",
                        "load",
                        "(Ljdk/internal/loader/NativeLibraries\$NativeLibraryImpl;Ljava/lang/String;ZZZ)Z"
                    ),
                    OEngineNativeLoadMethod(
                        "jdk/internal/loader/NativeLibraries",
                        "load",
                        "(Ljdk/internal/loader/NativeLibraries\$NativeLibraryImpl;Ljava/lang/String;ZZ)Z"
                    )
                )
            ),
            21 to OEngineJvmNativeLoadProfile(
                21,
                listOf(OEngineNativeLoadMethod(
                    "jdk/internal/loader/NativeLibraries",
                    "load",
                    "(Ljdk/internal/loader/NativeLibraries\$NativeLibraryImpl;Ljava/lang/String;ZZ)Z"
                ))
            )
        )

        fun forJavaMajor(javaMajor: Int): OEngineJvmNativeLoadProfile? = profiles[javaMajor]
    }
}
