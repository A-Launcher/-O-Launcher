package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads the official Forge/NeoForge installer JAR exactly as Mojo's ForgelikeUtils does. */
object OEngineInstallerJarStager {
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

        val folder = File(gameDirectory, "oengine/installers/${loader.id}/$game").canonicalFile
        val root = File(gameDirectory, "oengine/installers").canonicalFile
        require(folder.toPath().startsWith(root.toPath())) { "Unsafe installer destination." }
        if (!folder.exists() && !folder.mkdirs()) throw IllegalStateException("Could not create installer cache.")
        val target = File(folder, "$selected-installer.jar")
        if (target.isFile && target.length() > 0L && sha1(target) == expectedSha1) {
            return@withContext StagedInstaller(loader, game, selected, target, expectedSha1, target.length())
        }
        val temporary = File(folder, target.name + ".part")
        temporary.delete()
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
        }
        var received = 0L
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Official $loader installer download failed (HTTP ${connection.responseCode}).")
            }
            val total = connection.contentLengthLong.coerceAtLeast(0L)
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

    private fun requestText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Official installer checksum request failed (HTTP ${connection.responseCode}).")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
