package com.alauncher.olauncher

import android.os.Build
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

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
    private const val RUNTIME_REPOSITORY = "AngelAuraMC/angelauramc-openjdk-build"
    // SHA-256 values published by the GitHub Releases API for the pinned runtime assets.
    private val runtimeSha256 = mapOf(
        (8 to "arm") to "9dbee3b09af5f170e2ed9dd596bc81d0e573c88f8bf760c59c8fadbb9073d1e7",
        (8 to "arm64") to "9a59124d9791957d55c68be664ab76831f336cf2e1e1cd4414220c6fdbf0e06d",
        (8 to "x86") to "b96ce49fab52b28688dccc1a7d85dfc6d0f4048636aac826e1967524b28f75af",
        (8 to "x86_64") to "b1fbcef4965c17925894febe8216d089c6dd47b37950b5f945a89616443c1d0e",
        (17 to "arm") to "4a9134f1ebf6340dd855805d351712462cddab6f3c2684da7e7da10ccf06648d",
        (17 to "arm64") to "e162c860fe05ee4a4e4af7606437419879f6c748386a7b09fa77d10db6a64091",
        (17 to "x86") to "223a2d54606a9eb853c8451cf1d6bda1a8f09cb357f40b790140e57d45731ed7",
        (17 to "x86_64") to "893e27d2aed8b40407f29fe939e2a0f193e5d55f72892a303db634b0808a2b61",
        (21 to "arm") to "96c297487def64666e379a9a363d9955c05b1a0b091b0cf24af88359a66f394a",
        (21 to "arm64") to "8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe",
        (21 to "x86") to "9b8c7d10c5f751acb3b33506593da44ece52a0fd03e0b3c283ba08a7f285a40f",
        (21 to "x86_64") to "cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b",
        (25 to "arm") to "9ae13aee9cba7b2d2d8f40965061667e876d7380d866f37a992db3eff296ffb5",
        (25 to "arm64") to "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37",
        (25 to "x86_64") to "7fca862ee1b2d5fe23cd9c9c3d9b7ad3c241947ad1a6cc9464ef2e674867105d"
    )
    internal fun expectedArchiveSha256(javaMajor: Int, architecture: String): String? =
        runtimeSha256[javaMajor to architecture]

    internal fun selectCompatibleRuntimeMajor(requiredMajor: Int, availableMajors: Collection<Int>): Int? {
        require(requiredMajor >= 8) { "A Java runtime target must be Java 8 or newer after legacy mapping." }
        return availableMajors.filter { it >= requiredMajor }.minOrNull()
    }

    private fun installedRuntimeMajors(gameDirectory: File, architecture: String): Set<Int> {
        val runtimes = File(gameDirectory, "runtimes")
        return runtimes.listFiles().orEmpty().mapNotNull { directory ->
            val match = Regex("^Java-(\\d+)-" + Regex.escape(architecture) + "$").matchEntire(directory.name)
                ?: return@mapNotNull null
            val major = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val release = File(directory, "release")
            val java = File(directory, "bin/java")
            val actual = if (release.isFile) Regex("""JAVA_VERSION="(?:1\.)?(\d+)""")
                .find(release.readText())?.groupValues?.get(1)?.toIntOrNull() else null
            major.takeIf { it >= 8 && it == actual && java.isFile }
        }.toSet()
    }

    private fun discoverPublishedRuntimeMajors(architecture: String): Set<Int> {
        val endpoint = "https://api.github.com/repos/" + RUNTIME_REPOSITORY + "/releases?per_page=100"
        val connection = openHttpsDownload(endpoint)
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Could not query the Android Java runtime catalog (HTTP " + connection.responseCode + ").")
            }
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            return buildSet {
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    val tag = release.optString("tag_name")
                    val major = Regex("""^download_jre(\d+)$""").matchEntire(tag)
                        ?.groupValues?.get(1)?.toIntOrNull() ?: continue
                    val expectedName = "jre" + major + "-android-" + architecture + ".tar.xz"
                    val assets = release.optJSONArray("assets") ?: continue
                    val hasVerifiedAsset = (0 until assets.length()).any { assetIndex ->
                        val asset = assets.optJSONObject(assetIndex) ?: return@any false
                        if (asset.optString("name") != expectedName) return@any false
                        val digest = asset.optString("digest").removePrefix("sha256:")
                        digest.matches(Regex("[a-fA-F0-9]{64}")) ||
                            expectedArchiveSha256(major, architecture) != null
                    }
                    if (hasVerifiedAsset) add(major)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private data class RuntimeAsset(val url: String, val sha256: String)

    private fun resolveRuntimeAsset(javaMajor: Int, architecture: String): RuntimeAsset {
        val filename = "jre" + javaMajor + "-android-" + architecture + ".tar.xz"
        val pinned = expectedArchiveSha256(javaMajor, architecture)
        if (pinned != null) {
            val tag = "download_jre" + javaMajor
            val url = "https://github.com/" + RUNTIME_REPOSITORY + "/releases/download/" + tag + "/" + filename
            return RuntimeAsset(url, pinned)
        }
        val endpoint = "https://api.github.com/repos/" + RUNTIME_REPOSITORY + "/releases/tags/download_jre" + javaMajor
        val connection = openHttpsDownload(endpoint)
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Android Java " + javaMajor + " is not published for " + architecture + " (HTTP " + connection.responseCode + ").")
            }
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val assets = release.optJSONArray("assets") ?: JSONArray()
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                if (asset.optString("name") != filename) continue
                val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
                    .takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: pinned
                    ?: throw IllegalStateException("Java " + javaMajor + " runtime asset has no published SHA-256 digest.")
                val url = asset.optString("browser_download_url")
                require(url.startsWith("https://github.com/" + RUNTIME_REPOSITORY + "/releases/download/")) {
                    "Runtime catalog returned an unexpected download host."
                }
                return RuntimeAsset(url, digest)
            }
            throw IllegalStateException("Java " + javaMajor + " Android runtime asset for " + architecture + " is not published.")
        } finally {
            connection.disconnect()
        }
    }

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
        require(javaMajor >= 8) { "Legacy Java targets must be mapped to Java 8 or newer before runtime installation." }
        val architecture = deviceArchitecture()
        val filename = "jre" + javaMajor + "-android-" + architecture + ".tar.xz"
        val asset = resolveRuntimeAsset(javaMajor, architecture)
        val expectedSha256 = asset.sha256
        val address = asset.url
        val directory = File(gameDirectory, "oengine/runtime-archives")
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
            val actualSha256 = sha256(temporary)
            require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                "Downloaded Java $javaMajor runtime failed SHA-256 verification."
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

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
        require(javaMajor >= 8) { "Unsupported Java runtime version: " + javaMajor }
        val gameRoot = gameDirectory.canonicalFile
        require(gameRoot.isDirectory || gameRoot.mkdirs()) { "Could not create the Minecraft game directory." }
        OEngineMojoAwtInstallerHandoff.migrateLegacyRuntimeLayout(
            OEngineMojoAwtRuntimeLayout.paths(gameRoot)
        )
        val architecture = deviceArchitecture()
        val root = File(gameRoot, "runtimes").canonicalFile
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Could not create the installed runtimes directory.")
        val runtimeName = "Java-" + javaMajor + "-" + architecture
        val destination = File(root, runtimeName)
        val stagingRoot = File(gameRoot, "oengine/runtime-staging")
        if (!stagingRoot.exists() && !stagingRoot.mkdirs()) {
            throw IllegalStateException("Could not create the runtime staging directory.")
        }
        val staging = File(stagingRoot, runtimeName + ".installing")
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
        require(javaMajor >= 8) {
            "Legacy Java targets must be mapped to Java 8 or newer before runtime installation."
        }
        val root = gameDirectory.canonicalFile
        require(root.isDirectory || root.mkdirs()) { "Could not create the Minecraft game directory." }
        OEngineMojoAwtInstallerHandoff.migrateLegacyRuntimeLayout(
            OEngineMojoAwtRuntimeLayout.paths(root)
        )
        val architecture = deviceArchitecture()
        val installed = installedRuntimeMajors(root, architecture)
        val bundled = runtimeSha256.keys.filter { it.second == architecture }.map { it.first }
        var selectedMajor = selectCompatibleRuntimeMajor(javaMajor, installed + bundled)
        if (selectedMajor == null) {
            val published = runCatching { discoverPublishedRuntimeMajors(architecture) }.getOrElse { emptySet() }
            selectedMajor = selectCompatibleRuntimeMajor(javaMajor, installed + bundled + published)
        }
        val selectedRuntimeMajor = selectedMajor
            ?: throw IllegalStateException("No Android Java runtime at or above Java " + javaMajor + " is installed or published for " + architecture + ". O Launcher will use a newer compatible runtime automatically when its Android package becomes available.")
        val runtimeHome = File(File(root, "runtimes"), "Java-" + selectedRuntimeMajor + "-" + architecture)
        val release = File(runtimeHome, "release")
        val java = File(runtimeHome, "bin/java")
        if (java.isFile && release.isFile) {
            val major = Regex("""JAVA_VERSION="(?:1\.)?(\d+)""")
                .find(release.readText())?.groupValues?.get(1)?.toIntOrNull()
            if (major == selectedRuntimeMajor) {
                onStatus("Reusing installed Android Java " + selectedRuntimeMajor + " runtime for the Java " + javaMajor + " game target.")
                return@withContext RuntimeInstallResult(
                    selectedRuntimeMajor, architecture, runtimeHome, java, "JAVA_VERSION=" + selectedRuntimeMajor, 0
                )
            }
        }
        onStatus("Downloading Android Java " + selectedRuntimeMajor + " automatically for the Java " + javaMajor + " game target…")
        val archive = downloadRuntime(selectedRuntimeMajor, gameDirectory, onDownloadProgress)
        onStatus("Installing Android Java " + selectedRuntimeMajor + "…")
        installRuntime(archive.archive, selectedRuntimeMajor, gameDirectory, onStatus)
    }

}
