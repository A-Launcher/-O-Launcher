package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads the official Forge/NeoForge installer JAR exactly as Mojo's ForgelikeUtils does. */
object OEngineInstallerJarStager {
    private const val MAX_INSTALLER_BYTES = 512L * 1024L * 1024L
    private const val MAX_CHECKSUM_BYTES = 4096

    data class StagedInstaller(
        val loader: OEngineModLoader,
        val minecraftVersion: String,
        val loaderVersion: String,
        val file: File,
        val sha1: String,
        val bytes: Long
    )

    suspend fun stage(
        loader: OEngineModLoader,
        minecraftVersion: String,
        loaderVersion: String,
        gameDirectory: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): StagedInstaller = withContext(Dispatchers.IO) {
        require(loader == OEngineModLoader.FORGE || loader == OEngineModLoader.NEOFORGE) {
            "The installer-JAR flow is only for Forge and NeoForge."
        }
        val game = minecraftVersion.trim()
        val selected = loaderVersion.trim()
        val version = OEngineLoaderVersionCatalog.fetch(loader, game)
            .firstOrNull { it.version == selected }
            ?: throw IllegalArgumentException("$loader $selected is not published for Minecraft $game.")
        val address = version.installerUrl
            ?: throw IllegalStateException("No official installer URL is available for $loader $selected.")
        val expectedSha1 = requestText("$address.sha1").trim()
            .substringBefore(' ')
            .substringBefore('\n')
            .trim()
            .lowercase()
        require(expectedSha1.matches(Regex("[0-9a-f]{40}"))) {
            "The official $loader installer did not provide a valid SHA-1 checksum."
        }

        require(selected.matches(Regex("[A-Za-z0-9._+-]{1,80}"))) { "Invalid installer version." }
        val gameRoot = gameDirectory.canonicalFile
        val root = File(gameRoot, "oengine/installers").canonicalFile
        require(root.toPath().startsWith(gameRoot.toPath())) { "Unsafe installer cache root." }
        val folder = File(root, "${loader.id}/$game").canonicalFile
        require(folder.toPath().startsWith(root.toPath())) { "Unsafe installer destination." }
        if (!folder.exists() && !folder.mkdirs()) throw IllegalStateException("Could not create installer cache.")
        require(folder.canonicalFile.toPath().startsWith(root.toPath())) { "Installer cache path changed unexpectedly." }
        val target = File(folder, "$selected-installer.jar")
        require(!target.isDirectory) { "Installer cache target is a directory." }
        if (target.isFile && target.length() > 0L && sha1(target) == expectedSha1) {
            return@withContext StagedInstaller(loader, game, selected, target, expectedSha1, target.length())
        }
        val temporary = File(folder, target.name + ".part")
        temporary.delete()
        val connection = openHttps(address, "application/java-archive, application/octet-stream, */*")
        var received = 0L
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Official $loader installer download failed (HTTP ${connection.responseCode}).")
            }
            val total = connection.contentLengthLong.coerceAtLeast(0L)
            require(total <= MAX_INSTALLER_BYTES) { "Official installer exceeds the 512 MiB safety limit." }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        require(received <= MAX_INSTALLER_BYTES) { "Installer download exceeded the 512 MiB safety limit." }
                        output.write(buffer, 0, count)
                        onProgress(received, total)
                    }
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        if (temporary.length() <= 0L || sha1(temporary) != expectedSha1) {
            temporary.delete()
            throw IllegalStateException("$loader installer failed SHA-1 verification.")
        }
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IllegalStateException("Could not replace cached $loader installer.")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        StagedInstaller(loader, game, selected, target, expectedSha1, target.length())
    }

    /** Follow only HTTPS redirects, rejecting credential-bearing or downgraded URLs. */
    private fun openHttps(address: String, accept: String): HttpURLConnection {
        var current = URL(address)
        require(current.protocol.equals("https", true) && current.host.isNotBlank() && current.userInfo == null) {
            "Official installer URL must be a valid HTTPS URL."
        }
        repeat(6) { hop ->
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "O-Launcher/0.1.0")
                setRequestProperty("Accept", accept)
            }
            val status = try { connection.responseCode } catch (failure: Exception) {
                connection.disconnect()
                throw failure
            }
            if (status !in 300..399) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(hop < 5 && !location.isNullOrBlank()) { "Official installer redirected too many times." }
            current = URL(current, location)
            require(current.protocol.equals("https", true) && current.host.isNotBlank() && current.userInfo == null) {
                "Official installer redirect must remain HTTPS."
            }
        }
        error("Official installer redirected too many times.")
    }

    private fun requestText(address: String): String {
        val connection = openHttps(address, "text/plain")
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Official installer checksum request failed (HTTP ${connection.responseCode})."
            }
            val output = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(256)
                var total = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_CHECKSUM_BYTES) { "Official installer checksum response is too large." }
                    output.append(buffer, 0, count)
                }
            }
            return output.toString()
        } finally {
            connection.disconnect()
        }
    }
    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
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
}
