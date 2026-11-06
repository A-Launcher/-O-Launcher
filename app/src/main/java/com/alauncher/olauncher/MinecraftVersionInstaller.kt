package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class MinecraftRelease(val id: String, val type: String, val url: String)
data class ClientDownloadResult(val version: String, val file: File, val bytes: Long)

object MinecraftVersionInstaller {
    private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000

    suspend fun fetchReleases(): Pair<List<MinecraftRelease>, String> = withContext(Dispatchers.IO) {
        val manifest = JSONObject(requestText(MANIFEST_URL))
        val latest = manifest.getJSONObject("latest").optString("release")
        val entries = manifest.getJSONArray("versions")
        val releases = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                if (item.optString("type") == "release") {
                    add(MinecraftRelease(item.getString("id"), item.optString("type"), item.getString("url")))
                }
            }
        }
        releases to latest
    }

    suspend fun downloadClient(
        version: MinecraftRelease,
        gameDirectory: File,
        onProgress: (Long, Long) -> Unit
    ): ClientDownloadResult = withContext(Dispatchers.IO) {
        val metadataText = requestText(version.url)
        val metadata = JSONObject(metadataText)
        val downloads = metadata.getJSONObject("downloads")
        if (!downloads.has("client")) throw IllegalStateException("Minecraft ${version.id} does not provide a client download.")
        val client = downloads.getJSONObject("client")
        val downloadUrl = client.getString("url")
        val expectedSha1 = client.optString("sha1").lowercase()
        val expectedSize = client.optLong("size", -1L)

        val versionDirectory = File(File(gameDirectory, "versions"), version.id)
        if (!versionDirectory.exists() && !versionDirectory.mkdirs()) {
            throw IllegalStateException("Could not create the Minecraft version folder.")
        }
        File(versionDirectory, "${version.id}.json").writeText(metadataText)
        val target = File(versionDirectory, "${version.id}.jar")
        val temporary = File(versionDirectory, "${version.id}.jar.part")
        if (temporary.exists()) temporary.delete()

        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IllegalStateException("Minecraft download failed (HTTP ${connection.responseCode}).")
            val total = connection.getHeaderFieldLong("Content-Length", expectedSize)
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, if (total > 0) total else expectedSize)
                    }
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }

        if (!temporary.exists() || temporary.length() == 0L) {
            temporary.delete()
            throw IllegalStateException("The downloaded Minecraft file is empty.")
        }
        if (expectedSize > 0 && temporary.length() != expectedSize) {
            val actual = temporary.length()
            temporary.delete()
            throw IllegalStateException("Download size mismatch: expected $expectedSize bytes, received $actual.")
        }
        if (expectedSha1.isNotBlank() && !sha1(temporary).equals(expectedSha1, ignoreCase = true)) {
            temporary.delete()
            throw IllegalStateException("Minecraft client verification failed (SHA-1 mismatch).")
        }
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IllegalStateException("Could not replace the previous client file.")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        ClientDownloadResult(version.id, target, target.length())
    }

    private fun requestText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IllegalStateException("Mojang metadata request failed (HTTP ${connection.responseCode}).")
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
