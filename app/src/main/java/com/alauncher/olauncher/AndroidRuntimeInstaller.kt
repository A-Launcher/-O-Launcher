package com.alauncher.olauncher

import android.os.Build
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

data class RuntimeArchiveResult(
    val javaMajor: Int,
    val architecture: String,
    val archive: File,
    val bytes: Long
)

data class RuntimeInstallResult(val javaMajor: Int, val architecture: String, val runtimeHome: File, val javaExecutable: File, val javaVersion: String, val filesExtracted: Int)

object AndroidRuntimeInstaller {
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 60000
    private const val MAX_ARCHIVE_BYTES = 1024L * 1024L * 1024L
    private const val MAX_EXTRACTED_BYTES = 4L * 1024L * 1024L * 1024L
    private const val MAX_ARCHIVE_ENTRIES = 200_000
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
        val connection = openHttpsDownload(address)
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Runtime download failed (HTTP ${connection.responseCode}).")
            }
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            require(total <= MAX_ARCHIVE_BYTES) {
                "Runtime archive exceeds the 1 GiB download limit."
            }
            var received = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        require(received <= MAX_ARCHIVE_BYTES) {
                            "Runtime archive exceeded the 1 GiB download limit."
                        }
                        output.write(buffer, 0, count)
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

    private fun openHttpsDownload(address: String): HttpURLConnection {
        var current = URL(address)
        require(current.protocol.equals("https", true) && current.host.isNotBlank() && current.userInfo == null) {
            "Runtime downloads must use a valid HTTPS URL."
        }
        repeat(6) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "O-Launcher/0.1.0")
            }
            val status = connection.responseCode
            if (status !in setOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(!location.isNullOrBlank()) { "Runtime server returned a redirect without a destination." }
            current = URL(current, location)
            require(current.protocol.equals("https", true) && current.host.isNotBlank() && current.userInfo == null) {
                "Runtime server attempted an insecure or invalid redirect."
            }
        }
        error("Runtime server exceeded the allowed redirect count.")
    }

    suspend fun installRuntime(archive: File, javaMajor: Int, gameDirectory: File, onProgress: (String) -> Unit): RuntimeInstallResult = withContext(Dispatchers.IO) {
        require(archive.isFile && archive.length() > 0L) { "Download the Java runtime archive first." }
        require(javaMajor in supportedJava) { "Unsupported Java runtime version: " + javaMajor }
        val architecture = deviceArchitecture()
        val root = File(gameDirectory, "runtimes").canonicalFile
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Could not create the installed runtimes directory.")
        val runtimeName = "Java-" + javaMajor + "-" + architecture
        val destination = File(root, runtimeName)
        val staging = File(root, runtimeName + ".installing")
        if (staging.exists()) staging.deleteRecursively()
        if (!staging.mkdirs()) throw IllegalStateException("Could not create the runtime staging directory.")
        var extracted = 0
        var entriesSeen = 0
        var extractedBytes = 0L
        try {
            onProgress("Extracting Android Java " + javaMajor + "…")
            XZInputStream(BufferedInputStream(archive.inputStream())).use { xz ->
                TarArchiveInputStream(xz).use { tar ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        entriesSeen++
                        if (entriesSeen > MAX_ARCHIVE_ENTRIES) {
                            throw IllegalStateException("Runtime archive contains too many entries.")
                        }
                        if (entry.size < 0L || entry.size > MAX_EXTRACTED_BYTES - extractedBytes) {
                            throw IllegalStateException("Runtime archive exceeds the 4 GiB extraction limit.")
                        }
                        extractedBytes += entry.size
                        val name = entry.name
                        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\")) throw IllegalStateException("Unsafe path in runtime archive.")
                        val rootPath = staging.toPath().toAbsolutePath().normalize()
                        val targetPath = rootPath.resolve(name).normalize()
                        if (!targetPath.startsWith(rootPath)) throw IllegalStateException("Runtime archive contains a path outside its install folder.")
                        val target = targetPath.toFile()
                        val parent = target.parentFile ?: throw IllegalStateException("Invalid runtime archive path.")
                        if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("Could not create runtime directory: " + parent.name)
                        if (!parent.canonicalFile.toPath().startsWith(staging.canonicalFile.toPath())) throw IllegalStateException("Runtime archive attempts to write through an unsafe link.")
                        when {
                            entry.isSymbolicLink -> {
                                val linkTarget = parent.toPath().resolve(entry.linkName).normalize()
                                if (!linkTarget.startsWith(rootPath)) throw IllegalStateException("Runtime archive contains an unsafe symbolic link.")
                                if (target.exists() || java.nio.file.Files.isSymbolicLink(target.toPath())) target.delete()
                                Os.symlink(entry.linkName, target.absolutePath)
                            }
                            entry.isDirectory -> {
                                if (!target.exists() && !target.mkdirs()) throw IllegalStateException("Could not create runtime directory: " + name)
                            }
                            entry.isFile -> {
                                if (target.exists() && !target.delete()) throw IllegalStateException("Could not replace runtime file: " + name)
                                var fileBytes = 0L
                                FileOutputStream(target).use { output ->
                                    while (true) {
                                        val count = tar.read(buffer)
                                        if (count < 0) break
                                        fileBytes += count
                                        if (fileBytes > entry.size) throw IllegalStateException("Runtime archive entry exceeded its declared size: $name")
                                        output.write(buffer, 0, count)
                                    }
                                }
                                if (fileBytes != entry.size) throw IllegalStateException("Runtime archive entry was truncated: $name")
                                extracted++
                                val mode = entry.mode
                                if ((mode and 73) != 0) target.setExecutable(true, false)
                                if ((mode and 292) != 0) target.setReadable(true, false)
                                if ((mode and 146) != 0) target.setWritable(true, true)
                                if (entriesSeen % 250 == 0) onProgress("Extracting Java runtime… " + extracted + " files")
                            }
                            entry.isLink -> throw IllegalStateException("Runtime archive contains an unsupported hard link: " + name)
                        }
                    }
                }
            }
            val release = File(staging, "release")
            val releaseText = if (release.isFile) release.readText() else ""
            val match = Regex("""JAVA_VERSION="(?:1\.)?(\d+)""").find(releaseText)
                ?: throw IllegalStateException("Extracted runtime has no valid Java release metadata.")
            val actualMajor = match.groupValues[1].toIntOrNull() ?: throw IllegalStateException("Could not read the extracted Java version.")
            if (actualMajor != javaMajor) throw IllegalStateException("Downloaded Java " + actualMajor + ", but Java " + javaMajor + " was requested.")
            val java = File(staging, "bin/java")
            if (!java.isFile) throw IllegalStateException("Runtime archive is missing bin/java.")
            java.setExecutable(true, false)
            if (destination.exists() && !destination.deleteRecursively()) throw IllegalStateException("Could not replace the previous installed runtime.")
            if (!staging.renameTo(destination)) throw IllegalStateException("Could not finalize the extracted Java runtime.")
            RuntimeInstallResult(javaMajor, architecture, destination, File(destination, "bin/java"), match.value, extracted)
        } catch (error: Exception) {
            staging.deleteRecursively()
            throw error
        }
    }


    /** Reuse an installed runtime or automatically download and extract Android OpenJDK. */
    suspend fun ensureRuntime(
        javaMajor: Int,
        gameDirectory: File,
        onDownloadProgress: (Long, Long) -> Unit,
        onStatus: (String) -> Unit
    ): RuntimeInstallResult = withContext(Dispatchers.IO) {
        require(javaMajor in supportedJava) {
            "Java $javaMajor has no supported Android runtime package in the configured source."
        }
        val architecture = deviceArchitecture()
        val runtimeHome = File(File(gameDirectory, "runtimes"), "Java-$javaMajor-$architecture")
        val release = File(runtimeHome, "release")
        val java = File(runtimeHome, "bin/java")
        if (java.isFile && release.isFile) {
            val major = Regex("""JAVA_VERSION="(?:1\.)?(\d+)""")
                .find(release.readText())?.groupValues?.get(1)?.toIntOrNull()
            if (major == javaMajor) {
                onStatus("Reusing installed Android Java $javaMajor runtime.")
                return@withContext RuntimeInstallResult(
                    javaMajor, architecture, runtimeHome, java, "JAVA_VERSION=$javaMajor", 0
                )
            }
        }
        onStatus("Downloading Android Java $javaMajor automatically…")
        val archive = downloadRuntime(javaMajor, gameDirectory, onDownloadProgress)
        onStatus("Installing Android Java $javaMajor…")
        installRuntime(archive.archive, javaMajor, gameDirectory, onStatus)
    }

}
