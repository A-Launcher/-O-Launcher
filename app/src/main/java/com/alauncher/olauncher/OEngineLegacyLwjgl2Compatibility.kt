package com.alauncher.olauncher

/**
 * Pinned MojoLauncher LWJGL 2-on-GLFW compatibility artifacts.
 *
 * The Java fork replaces the original LWJGL 2 Display/Context implementation with
 * GLFW calls. It is separate from LWJGL 3 and must never be substituted into modern
 * Minecraft classpaths.
 */
internal object OEngineLegacyLwjgl2Compatibility {
    const val UPSTREAM_REPOSITORY = "MojoLauncher/lwjgl2-glfw"
    const val UPSTREAM_COMMIT = "bf01b1b05c417b55cffc04d85dbc3a9cfa3b93df"
    const val RELEASE_TAG = "vv8m"
    const val LICENSE = "BSD-3-Clause"

    const val LIBRARY_PREFIX = "org.lwjgl.lwjgl:lwjgl:"
    const val MOJO_LIBRARY_COORDINATE = "org.lwjgl.lwjgl:lwjgl:2.9.4-mojo"
    const val JAVA_ARCHIVE_URL =
        "https://github.com/MojoLauncher/lwjgl2-glfw/releases/download/vv8m/lwjgl.jar"
    const val JAVA_ARCHIVE_SIZE = 1_052_773L
    const val JAVA_ARCHIVE_SHA1 = "aff6d7e63df7bae082bc8bae3b205205390d6406"
    const val JAVA_ARCHIVE_SHA256 = "8258d85e165e291aaea9c3888af9da516fa8c51135288d108b26503eab7b97b9"

    data class NativeArchive(
        val abi: String,
        val fileName: String,
        val size: Long,
        val sha256: String,
        val url: String,
        val libraryFileName: String
    )

    private val nativeArchives = mapOf(
        "arm64-v8a" to NativeArchive(
            "arm64-v8a", "lwjgl-native-arm64-v8a.jar", 99_240L,
            "93126ae6975f1850d25bf9a7c5d26012a12e40ada2b2c101e504f3e8187007a5",
            "https://github.com/MojoLauncher/lwjgl2-glfw/releases/download/vv8m/lwjgl-native-arm64-v8a.jar",
            "liblwjgl64.so"
        ),
        "armeabi-v7a" to NativeArchive(
            "armeabi-v7a", "lwjgl-native-armeabi-v7a.jar", 76_863L,
            "c99a2c617cf29c714128868ac8b3c4a0304a2d8dcfa21dbeee4fa398771b554a",
            "https://github.com/MojoLauncher/lwjgl2-glfw/releases/download/vv8m/lwjgl-native-armeabi-v7a.jar",
            "liblwjgl.so"
        ),
        "x86" to NativeArchive(
            "x86", "lwjgl-native-x86.jar", 107_351L,
            "59be4d04330874a8e55c98eee2c0913f11b913268dc80a91b0f571730c6db3b3",
            "https://github.com/MojoLauncher/lwjgl2-glfw/releases/download/vv8m/lwjgl-native-x86.jar",
            "liblwjgl.so"
        ),
        "x86_64" to NativeArchive(
            "x86_64", "lwjgl-native-x86_64.jar", 100_331L,
            "4129e73bfb44415db29ef9ff9c4057db46cfceafec308c372a41db8bab72a55d",
            "https://github.com/MojoLauncher/lwjgl2-glfw/releases/download/vv8m/lwjgl-native-x86_64.jar",
            "liblwjgl64.so"
        )
    )

    fun isLegacyLwjgl2Coordinate(coordinate: String): Boolean =
        coordinate.startsWith(LIBRARY_PREFIX)

    fun nativeArchive(abi: String): NativeArchive =
        nativeArchives[abi] ?: throw IllegalArgumentException(
            "Mojo LWJGL 2 compatibility has no pinned native archive for ABI $abi."
        )

    fun supportedAbis(): Set<String> = nativeArchives.keys
}
