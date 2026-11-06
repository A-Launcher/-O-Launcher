package com.alauncher.olauncher

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class RuntimeArchiveResult(
    val javaMajor: Int,
    val architecture: String,
    val archive: File,
    val bytes: Long
)

object AndroidRuntimeInstaller {
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 60000
    private val supportedJava = setOf(8, 17, 21, 25)
    private val xzMagic = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)

    fun deviceArchitecture(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.contains("arm64", ignoreCase = true) || abi.contains("aarch64", ignoreCase = true) -> "arm64"
            abi.contains("armeabi") || abi == "arm" -> "arm"
            abi.contains("x86_64", ignoreCase = true) -> "x86_64"
            abi == "x86" || abi.contains("i686", ignoreCase = true) -> "x86"
            else -> throw IllegalStateException("Unsupported Android CPU architecture: $abi")
        }
    }

    suspend fun downloadRuntime(
        javaMajor: Int,
        gameDirectory: File,
        onProgress: (Long, Long) -> Unit
    ): RuntimeArchiveResult = withContext(Dispatchers.IO) {
        require(javaMajor in supportedJava) { "Java $javaMajor runtime is not available from the configured Android runtime source." }
        val architecture = deviceArchitecture()
        if (javaMajor == 25 && architecture == "x86") {
            throw IllegalStateException("Java 25 Android runtime is not published for 32-bit x86.")
        }
        val tag = "download_jre$javaMajor"
        val filename = "jre$javaMajor-android-$architecture.tar.xz"
        val address = "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/$tag/$filename"
        val directory = File(gameDirectory, "runtimes/archives")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create the runtime archive directory.")
        }
        val target = File(directory, filename)
        val temporary = File(directory, "$filename.part")
        if (temporary.exists()) temporary.delete()
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Runtime download failed (HTTP ${connection.responseCode}).")
            }
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            var received = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        onProgress(received, total)
                    }
                }
            }
            if (received < 10_000_000L) {
                throw IllegalStateException("Downloaded runtime archive is unexpectedly small.")
            }
            val header = ByteArray(xzMagic.size)
            temporary.inputStream().use { input ->
                if (input.read(header) != header.size || !header.contentEquals(xzMagic)) {
                    throw IllegalStateException("Downloaded runtime is not a valid XZ archive.")
                }
            }
            if (target.exists() && !target.delete()) {
                throw IllegalStateException("Could not replace the previous runtime archive.")
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            RuntimeArchiveResult(javaMajor, architecture, target, received)
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}
