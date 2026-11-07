package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Headless Fabric/Quilt profile installer, following MojoLauncher's FabriclikeUtils flow. */
object OEngineLoaderInstaller {
    data class InstallResult(
        val loader: OEngineModLoader,
        val minecraftVersion: String,
        val loaderVersion: String,
        val profileId: String,
        val metadataFile: File,
        val librariesInstalled: Int,
        val bytesDownloaded: Long
    )

    fun profileUrl(loader: OEngineModLoader, minecraftVersion: String, loaderVersion: String): String {
        val base = when (loader) {
            OEngineModLoader.FABRIC -> "https://meta.fabricmc.net/v2"
            OEngineModLoader.QUILT -> "https://meta.quiltmc.org/v3"
            else -> throw IllegalArgumentException("The headless profile installer supports Fabric and Quilt.")
        }
        return "$base/versions/loader/${encode(minecraftVersion)}/${encode(loaderVersion)}/profile/json"
    }

    /** Resolves Maven coordinates, including optional classifier and @extension, safely. */
    fun mavenArtifactPath(coordinate: String): String? {
        val parts = coordinate.substringBefore('@').split(':')
        val extension = coordinate.substringAfter('@', "jar").ifBlank { "jar" }
        if (parts.size !in 3..4 || parts.any(String::isBlank)) return null
        val (group, artifact, version) = parts
        if (!group.matches(Regex("[A-Za-z0-9_.-]+")) ||
            !artifact.matches(Regex("[A-Za-z0-9_.+-]+")) ||
            !version.matches(Regex("[A-Za-z0-9_.+-]+")) ||
            !extension.matches(Regex("[A-Za-z0-9_-]{1,12}"))) return null
        val classifier = parts.getOrNull(3)?.takeIf(String::isNotBlank)
        if (classifier != null && !classifier.matches(Regex("[A-Za-z0-9_.+-]+"))) return null
        val filename = "$artifact-$version${classifier?.let { "-$it" } ?: ""}.$extension"
        return "${group.replace('.', '/')}/$artifact/$version/$filename"
    }

    suspend fun install(
        loader: OEngineModLoader,
        minecraftVersion: String,
        loaderVersion: String,
        gameDirectory: File,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): InstallResult = withContext(Dispatchers.IO) {
        require(loader == OEngineModLoader.FABRIC || loader == OEngineModLoader.QUILT) {
            "Fabric and Quilt use the headless profile flow. Forge-family installers need MojoLauncher's installer-agent."
        }
        val game = minecraftVersion.trim()
        val selectedLoader = loaderVersion.trim()
        require(game.matches(Regex("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?"))) { "Invalid Minecraft version." }
        require(selectedLoader.isNotBlank() && selectedLoader.length <= 80) { "Choose a valid loader version." }
        require(File(gameDirectory, "versions/$game/$game.json").isFile) {
            "Install Minecraft $game first; the loader profile inherits its client and assets."
        }

        val profileText = requestText(profileUrl(loader, game, selectedLoader))
        val profile = JSONObject(profileText)
        val profileId = profile.optString("id").trim()
        require(profileId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "The loader service returned an invalid profile ID." }
        require(profile.optString("inheritsFrom") == game) {
            "The loader profile does not inherit from Minecraft $game."
        }
        require(profile.optString("mainClass").isNotBlank()) { "The loader profile has no main class." }

        val versionsRoot = File(gameDirectory, "versions").canonicalFile
        val profileDirectory = File(versionsRoot, profileId).canonicalFile
        require(profileDirectory.toPath().startsWith(versionsRoot.toPath())) { "Unsafe loader profile path." }
        if (!profileDirectory.exists() && !profileDirectory.mkdirs()) {
            throw IllegalStateException("Could not create loader profile directory.")
        }
        val metadataFile = File(profileDirectory, "$profileId.json")
        val temporaryMetadata = File(profileDirectory, "$profileId.json.part")
        temporaryMetadata.writeText(profileText)
        if (metadataFile.exists() && !metadataFile.delete()) {
            temporaryMetadata.delete()
            throw IllegalStateException("Could not replace previous loader profile.")
        }
        if (!temporaryMetadata.renameTo(metadataFile)) {
            temporaryMetadata.copyTo(metadataFile, overwrite = true)
            temporaryMetadata.delete()
        }

        val entries = mutableListOf<JSONObject>()
        val libraryArray = profile.optJSONArray("libraries")
        if (libraryArray != null) for (i in 0 until libraryArray.length()) {
            val library = libraryArray.optJSONObject(i) ?: continue
            if (MinecraftVersionInstaller.isAllowedForAndroid(library.optJSONArray("rules"))) entries += library
        }
        val libraryRoot = File(gameDirectory, "libraries").canonicalFile
        var bytes = 0L
        var installed = 0
        for ((index, library) in entries.withIndex()) {
            val name = library.optString("name").trim()
            if (name.isBlank()) continue
            val artifact = library.optJSONObject("downloads")?.optJSONObject("artifact")
            val path = artifact?.optString("path")?.takeIf(String::isNotBlank) ?: mavenArtifactPath(name)
                ?: throw IllegalStateException("Unsupported loader library coordinate: $name")
            val target = File(libraryRoot, path).canonicalFile
            require(target.toPath().startsWith(libraryRoot.toPath())) { "Unsafe loader library path: $name" }
            val directUrl = artifact?.optString("url")?.takeIf(String::isNotBlank)
            val repository = library.optString("url").takeIf(String::isNotBlank) ?: "https://libraries.minecraft.net/"
            val address = directUrl ?: repository.trimEnd('/') + "/" + path
            val sha = artifact?.optString("sha1").orEmpty()
            val size = artifact?.optLong("size", -1L) ?: -1L
            if (target.isFile && target.length() > 0L &&
                (size <= 0 || target.length() == size) &&
                (sha.isBlank() || sha1(target).equals(sha, true))) {
                installed++
                onProgress(index + 1, entries.size, "Verified $name")
                continue
            }
            bytes += downloadVerified(address, target, sha, size)
            installed++
            onProgress(index + 1, entries.size, "Installed $name")
        }
        InstallResult(loader, game, selectedLoader, profileId, metadataFile, installed, bytes)
    }

    private fun downloadVerified(address: String, target: File, expectedSha1: String, expectedSize: Long): Long {
        val parent = target.parentFile ?: error("Invalid loader library path.")
        if (!parent.exists() && !parent.mkdirs()) error("Could not create loader library directory.")
        val temporary = File(parent, target.name + ".part")
        temporary.delete()
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 45_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) error("Loader library download failed (HTTP ${connection.responseCode}).")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output, 64 * 1024) }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        val actual = temporary.length()
        if (actual <= 0 || (expectedSize > 0 && actual != expectedSize)) {
            temporary.delete()
            error("Loader library size verification failed: ${target.name}")
        }
        if (expectedSha1.isNotBlank() && !sha1(temporary).equals(expectedSha1, true)) {
            temporary.delete()
            error("Loader library SHA-1 verification failed: ${target.name}")
        }
        if (target.exists() && !target.delete()) {
            temporary.delete()
            error("Could not replace loader library: ${target.name}")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return target.length()
    }

    private fun requestText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("Loader profile request failed (HTTP ${connection.responseCode}).")
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

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
