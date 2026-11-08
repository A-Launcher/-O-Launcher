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
        val normalized = raw.replace('\\', '/')
        require(normalized.isNotBlank() && !normalized.startsWith('/') && !normalized.startsWith('\\'))
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
            require(!indexEntry.isDirectory) { "Modpack index must be a file." }
            val indexText = zip.getInputStream(indexEntry).use { readBoundedUtf8(it, 8 * 1024 * 1024) }
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
                verifySafeDestination(root, destination)
                val temp = createSafeTempFile(destination)
                try {
                    downloadVerified(url, temp, size, sha1, sha512)
                    moveIntoPlace(temp, destination)
                } catch (failure: Exception) {
                    temp.delete()
                    throw failure
                }
                completed += size
                downloaded++
                onProgress(completed, expectedTotal)
            }

            var copied = 0
            // The 2 GiB cap applies to the complete imported pack, not each source separately.
            var overrideBytes = expectedTotal
            val firstOverrides = copyOverrides(zip, "overrides/", root, overrideBytes, seen)
            copied += firstOverrides.first
            overrideBytes = firstOverrides.second
            val clientOverrides = copyOverrides(zip, "client-overrides/", root, overrideBytes, seen)
            copied += clientOverrides.first
            return Result(name, versionId, minecraft, loaders, downloaded, copied, skipped)
        }
    }

    /** Enforce the byte limit while reading, before allocating an unbounded index string. */
    private fun readBoundedUtf8(input: java.io.InputStream, maxBytes: Int): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Modpack index is too large." }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun safeDestination(root: File, relative: String): File {
        val destination = File(root, relative).canonicalFile
        require(destination.toPath().startsWith(root.toPath()) && destination != root) { "Archive path escapes the instance." }
        require(!destination.isDirectory) { "A modpack file cannot replace an instance directory." }
        return destination
    }

    /** Create a unique, exclusively-created staging file in the verified destination directory. */
    private fun createSafeTempFile(destination: File): File {
        val parent = destination.parentFile ?: error("Modpack destination has no parent directory.")
        require(parent.isDirectory && !java.nio.file.Files.isSymbolicLink(parent.toPath())) {
            "Modpack staging directory is unsafe."
        }
        val temp = File.createTempFile(".oengine-", ".part", parent)
        require(!java.nio.file.Files.isSymbolicLink(temp.toPath()) && temp.isFile) {
            temp.delete()
            "Could not create a safe modpack staging file."
        }
        return temp
    }

    /** Re-resolve after parent creation so a symlinked parent cannot redirect extraction outside the instance. */
    private fun verifySafeDestination(root: File, destination: File) {
        val canonicalRoot = root.canonicalFile.toPath()
        val canonicalParent = destination.parentFile?.canonicalFile?.toPath()
            ?: error("Modpack destination has no parent directory.")
        require(canonicalParent.startsWith(canonicalRoot)) { "Modpack destination parent escapes the instance." }
        require(!destination.isDirectory) { "A modpack file cannot replace an instance directory." }
    }

    private fun copyOverrides(
        zip: ZipFile,
        prefix: String,
        root: File,
        initialBytes: Long,
        seenPaths: MutableSet<String>
    ): Pair<Int, Long> {
        var copied = 0
        var totalBytes = initialBytes
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
            val relative = safeRelativePath(entry.name.removePrefix(prefix))
            require(seenPaths.add(relative.lowercase())) {
                "Modpack contains colliding paths across downloads and override folders."
            }
            val destination = safeDestination(root, relative)
            require(destination.parentFile?.exists() == true || destination.parentFile?.mkdirs() == true)
            verifySafeDestination(root, destination)
            val temp = createSafeTempFile(destination)
            try {
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
            } catch (failure: Exception) {
                temp.delete()
                throw failure
            }
            copied++
        }
        return copied to totalBytes
    }

    /** Follow only HTTPS redirects; never let an archive entry redirect a download to cleartext HTTP. */
    private fun openHttpsDownload(address: String): HttpURLConnection {
        var current = URL(address)
        require(current.protocol.equals("https", true) && !current.host.isNullOrBlank() && current.userInfo == null) {
            "Modpack download URL must be a valid HTTPS URL."
        }
        repeat(6) { hop ->
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "O-Launcher/1.0 (Android)")
            }
            val status = try { connection.responseCode } catch (failure: Exception) {
                connection.disconnect()
                throw failure
            }
            if (status !in 300..399) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            require(hop < 5 && !location.isNullOrBlank()) { "Modpack download redirected too many times or omitted its destination." }
            current = URL(current, location)
            require(current.protocol.equals("https", true) && !current.host.isNullOrBlank() && current.userInfo == null) {
                "Modpack download redirect must remain on a valid HTTPS URL."
            }
        }
        error("Modpack download redirected too many times.")
    }

    private fun downloadVerified(address: String, destination: File, expectedSize: Long, sha1: String, sha512: String) {
        val connection = openHttpsDownload(address)
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
