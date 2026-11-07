package com.alauncher.olauncher

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Imports the client side of a Modrinth .mrpack into one isolated game directory. */
object OEngineModrinthPackImporter {
    data class Result(
        val name: String,
        val versionId: String,
        val minecraftVersion: String,
        val loaders: Map<String, String>,
        val downloadedFiles: Int,
        val copiedOverrideFiles: Int,
        val skippedOptionalFiles: Int
    )

    private const val MAX_FILE = 512L * 1024L * 1024L
    private const val MAX_TOTAL = 2L * 1024L * 1024L * 1024L
    private const val MAX_ENTRIES = 100_000

    fun safeRelativePath(raw: String): String {
        val normalized = raw.replace('\\\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !normalized.startsWith('\\\\'))
        require(!Regex("^[A-Za-z]:").containsMatchIn(normalized))
        val parts = normalized.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) { "Unsafe archive path." }
        require(parts.all { it.matches(Regex("[A-Za-z0-9._ ()+\\-]+")) }) { "Unsupported characters in archive path." }
        return parts.joinToString("/")
    }

    fun importPack(archive: File, gameRoot: File, onProgress: (Long, Long) -> Unit = { _, _ -> }): Result {
        require(archive.isFile && archive.length() in 1..MAX_FILE) { "Select a valid .mrpack archive (up to 512 MiB)." }
        val root = gameRoot.canonicalFile
        require(root.exists() || root.mkdirs()) { "Could not create the target instance." }
        ZipFile(archive).use { zip ->
            require(zip.size() <= MAX_ENTRIES) { "The pack contains too many archive entries." }
            val indexEntry = zip.getEntry("modrinth.index.json") ?: error("This is not a valid Modrinth .mrpack: index is missing.")
            val indexText = zip.getInputStream(indexEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            require(indexText.length <= 8 * 1024 * 1024) { "Modpack index is too large." }
            val index = JSONObject(indexText)
            require(index.optInt("formatVersion", -1) == 1) { "Unsupported Modrinth modpack format version." }
            val name = index.optString("name").trim().ifBlank { archive.nameWithoutExtension }.take(64)
            val versionId = index.optString("versionId")
            val dependencies = index.optJSONObject("dependencies") ?: error("Modpack has no dependency metadata.")
            val minecraft = dependencies.optString("minecraft")
            require(minecraft.matches(Regex("[A-Za-z0-9._-]{1,40}"))) { "Modpack Minecraft version is invalid." }
            val loaders = buildMap {
                for (key in listOf("fabric-loader", "forge", "neoforge", "quilt-loader")) {
                    val value = dependencies.optString(key)
                    if (value.isNotBlank()) put(key, value)
                }
            }
            require(loaders.size <= 1) { "Modpack declares conflicting loader types." }

            val files = index.optJSONArray("files") ?: error("Modpack file list is missing.")
            require(files.length() <= MAX_ENTRIES)
            var expectedTotal = 0L
            var completed = 0L
            var downloaded = 0
            var skipped = 0
            val seen = HashSet<String>()
            for (i in 0 until files.length()) {
                val item = files.optJSONObject(i) ?: continue
                val env = item.optJSONObject("env")
                if (env?.optString("client") == "unsupported") { skipped++; continue }
                val relative = safeRelativePath(item.optString("path"))
                require(seen.add(relative.lowercase())) { "Modpack contains duplicate file paths." }
                val size = item.optLong("fileSize", -1L)
                require(size in 1..MAX_FILE) { "Invalid file size for $relative." }
                expectedTotal += size
                require(expectedTotal <= MAX_TOTAL) { "Modpack downloads exceed the 2 GiB safety limit." }
                val downloads = item.optJSONArray("downloads") ?: error("No download URL for $relative.")
                val url = (0 until downloads.length()).map { downloads.optString(it) }
                    .firstOrNull { it.startsWith("https://") } ?: error("No secure download URL for $relative.")
                val hashes = item.optJSONObject("hashes") ?: error("No integrity hashes for $relative.")
                val sha1 = hashes.optString("sha1").lowercase()
                val sha512 = hashes.optString("sha512").lowercase()
                require(sha1.matches(Regex("[0-9a-f]{40}")) || sha512.matches(Regex("[0-9a-f]{128}"))) {
                    "Missing valid integrity hash for $relative."
                }
                val destination = safeDestination(root, relative)
                require(destination.parentFile?.exists() == true || destination.parentFile?.mkdirs() == true)
                val temp = File(destination.parentFile, destination.name + ".oengine-part")
                downloadVerified(url, temp, size, sha1, sha512)
                moveIntoPlace(temp, destination)
                completed += size
                downloaded++
                onProgress(completed, expectedTotal)
            }

            var copied = 0
            var overrideBytes = 0L
            val firstOverrides = copyOverrides(zip, "overrides/", root, overrideBytes)
            copied += firstOverrides.first
            overrideBytes = firstOverrides.second
            val clientOverrides = copyOverrides(zip, "client-overrides/", root, overrideBytes)
            copied += clientOverrides.first
            return Result(name, versionId, minecraft, loaders, downloaded, copied, skipped)
        }
    }

    private fun safeDestination(root: File, relative: String): File {
        val destination = File(root, relative).canonicalFile
        require(destination.toPath().startsWith(root.toPath()) && destination != root) { "Archive path escapes the instance." }
        return destination
    }

    private fun copyOverrides(zip: ZipFile, prefix: String, root: File, initialBytes: Long): Pair<Int, Long> {
        var copied = 0
        var totalBytes = initialBytes
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
            val relative = safeRelativePath(entry.name.removePrefix(prefix))
            val destination = safeDestination(root, relative)
            require(destination.parentFile?.exists() == true || destination.parentFile?.mkdirs() == true)
            val temp = File(destination.parentFile, destination.name + ".oengine-part")
            zip.getInputStream(entry).use { input ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32768)
                    var count = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        totalBytes += n
                        require(count <= MAX_FILE && totalBytes <= MAX_TOTAL) { "Modpack overrides exceed the extraction safety limit." }
                        output.write(buffer, 0, n)
                    }
                }
            }
            moveIntoPlace(temp, destination)
            copied++
        }
        return copied to totalBytes
    }

    private fun downloadVerified(address: String, destination: File, expectedSize: Long, sha1: String, sha512: String) {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/1.0 (Android)")
        }
        val digest1 = MessageDigest.getInstance("SHA-1")
        val digest512 = MessageDigest.getInstance("SHA-512")
        var count = 0L
        try {
            connection.connect()
            require(connection.responseCode in 200..299) { "Modpack download failed (HTTP ${connection.responseCode})." }
            connection.inputStream.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        require(count <= expectedSize && count <= MAX_FILE) { "Download exceeded declared size." }
                        digest1.update(buffer, 0, n)
                        digest512.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                    }
                }
            }
        } catch (failure: Exception) {
            destination.delete()
            throw failure
        } finally {
            connection.disconnect()
        }
        val actual1 = digest1.digest().joinToString("") { "%02x".format(it) }
        val actual512 = digest512.digest().joinToString("") { "%02x".format(it) }
        if (count != expectedSize || (sha1.isNotBlank() && !actual1.equals(sha1, true)) ||
            (sha512.isNotBlank() && !actual512.equals(sha512, true))) {
            destination.delete()
            error("Integrity verification failed for modpack file.")
        }
    }

    private fun moveIntoPlace(temp: File, destination: File) {
        val backup = File(destination.parentFile, destination.name + ".oengine-backup")
        backup.delete()
        if (destination.exists() && !destination.renameTo(backup)) {
            temp.delete()
            error("Could not safely replace ${destination.name}.")
        }
        if (!temp.renameTo(destination)) {
            if (backup.exists()) backup.renameTo(destination)
            temp.delete()
            error("Could not move verified file into place.")
        }
        backup.delete()
    }
}
