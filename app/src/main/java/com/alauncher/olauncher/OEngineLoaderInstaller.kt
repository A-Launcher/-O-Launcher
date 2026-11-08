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

        val (metadataFile, temporaryMetadata) = prepareProfileMetadataPaths(gameDirectory, profileId)
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
        val libraryRoot = prepareLibraryRoot(gameDirectory)
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
            bytes += downloadVerified(address, target, libraryRoot, sha, size)
            installed++
            onProgress(index + 1, entries.size, "Installed $name")
        }
        InstallResult(loader, game, selectedLoader, profileId, metadataFile, installed, bytes)
    }

    /** Ensures loader libraries are written to a real directory inside this instance. */
    internal fun prepareLibraryRoot(gameDirectory: File): File {
        val gameRoot = gameDirectory.canonicalFile
        require(gameRoot.isDirectory) { "Minecraft instance directory is missing." }
        val libraryPath = File(gameRoot, "libraries")
        require(!java.nio.file.Files.isSymbolicLink(libraryPath.toPath())) {
            "Minecraft libraries directory must not be a symbolic link."
        }
        if (!libraryPath.exists() && !libraryPath.mkdirs()) {
            throw IllegalStateException("Could not create Minecraft libraries directory.")
        }
        val libraryRoot = libraryPath.canonicalFile
        require(libraryRoot.isDirectory && libraryRoot.toPath().startsWith(gameRoot.toPath())) {
            "Minecraft libraries directory is missing or escapes the selected instance."
        }
        return libraryRoot
    }

    /**
     * Creates a contained profile directory and rejects symlinks at every metadata
     * write boundary. In particular, an existing profile JSON symlink must never be
     * followed by writeText(), even when its parent directory is inside versions/.
     */
    internal fun prepareProfileMetadataPaths(gameDirectory: File, profileId: String): Pair<File, File> {
        require(profileId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Invalid loader profile ID." }
        val versionsPath = File(gameDirectory, "versions")
        require(!java.nio.file.Files.isSymbolicLink(versionsPath.toPath())) {
            "Minecraft versions directory must not be a symbolic link."
        }
        val versionsRoot = versionsPath.canonicalFile
        require(versionsRoot.isDirectory) { "Minecraft versions directory is missing." }
        val profilePath = File(versionsRoot, profileId)
        require(!java.nio.file.Files.isSymbolicLink(profilePath.toPath())) {
            "Loader profile directory must not be a symbolic link."
        }
        val profileDirectory = profilePath.canonicalFile
        require(profileDirectory.toPath().startsWith(versionsRoot.toPath())) { "Unsafe loader profile path." }
        if (!profileDirectory.exists() && !profileDirectory.mkdirs()) {
            throw IllegalStateException("Could not create loader profile directory.")
        }
        require(profileDirectory.isDirectory && !java.nio.file.Files.isSymbolicLink(profileDirectory.toPath())) {
            "Loader profile directory is missing or unsafe."
        }
        val metadataFile = File(profileDirectory, "$profileId.json")
        val temporaryMetadata = File(profileDirectory, "$profileId.json.part")
        require(!java.nio.file.Files.isSymbolicLink(metadataFile.toPath()) &&
            !java.nio.file.Files.isSymbolicLink(temporaryMetadata.toPath())) {
            "Loader profile metadata path must not be a symbolic link."
        }
        require(metadataFile.canonicalFile.parentFile == profileDirectory &&
            temporaryMetadata.canonicalFile.parentFile == profileDirectory) {
            "Loader profile metadata path escapes its profile directory."
        }
        return metadataFile to temporaryMetadata
    }

    private const val MAX_PROFILE_BYTES = 8L * 1024L * 1024L
    private const val MAX_LIBRARY_BYTES = 512L * 1024L * 1024L

    /** Follow redirects manually so HTTPS cannot be downgraded by a Maven redirect. */
    private fun openHttps(address: String, accept: String): HttpURLConnection {
        var current = URL(address)
        require(current.protocol.equals("https", true) && current.host.isNotBlank() && current.userInfo == null) {
            "Loader downloads must use a valid HTTPS URL."
        }
        repeat(6) { hop ->
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 45_000
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
            require(hop < 5 && !location.isNullOrBlank()) {
                "Loader server redirected too many times or omitted its destination."
            }
            current = URL(current, location)
            require(current.protocol.equals("https", true) && current.host.isNotBlank() && current.userInfo == null) {
                "Loader redirect must remain on a valid HTTPS URL."
            }
        }
        error("Loader server redirected too many times.")
    }

    private fun downloadVerified(
        address: String,
        target: File,
        libraryRoot: File,
        expectedSha1: String,
        expectedSize: Long
    ): Long {
        require(expectedSize <= MAX_LIBRARY_BYTES) { "Loader library exceeds the 512 MiB safety limit." }
        val parent = target.parentFile ?: error("Invalid loader library path.")
        if (!parent.exists() && !parent.mkdirs()) error("Could not create loader library directory.")
        val canonicalRoot = libraryRoot.canonicalFile.toPath()
        val canonicalParent = parent.canonicalFile.toPath()
        require(canonicalParent.startsWith(canonicalRoot)) { "Loader library path escapes the libraries directory." }
        val safeTarget = File(canonicalParent.toFile(), target.name)
        require(!safeTarget.isDirectory) { "A loader library file cannot replace a directory." }
        val temporary = File(canonicalParent.toFile(), target.name + ".part")
        temporary.delete()
        val connection = openHttps(address, "application/java-archive, application/octet-stream, */*")
        try {
            require(connection.responseCode in 200..299) {
                "Loader library download failed (HTTP ${connection.responseCode})."
            }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        require(count <= MAX_LIBRARY_BYTES && (expectedSize <= 0 || count <= expectedSize)) {
                            "Loader library download exceeded its declared size or safety limit."
                        }
                        output.write(buffer, 0, n)
                    }
                }
            }
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
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
        require(parent.canonicalFile.toPath().startsWith(canonicalRoot)) {
            temporary.delete()
            "Loader library destination changed during download."
        }
        if (safeTarget.exists() && !safeTarget.delete()) {
            temporary.delete()
            error("Could not replace loader library: ${safeTarget.name}")
        }
        if (!temporary.renameTo(safeTarget)) {
            temporary.copyTo(safeTarget, overwrite = true)
            temporary.delete()
        }
        return safeTarget.length()
    }

    private fun requestText(address: String): String {
        val connection = openHttps(address, "application/json, application/xml, text/xml")
        try {
            require(connection.responseCode in 200..299) {
                "Loader profile request failed (HTTP ${connection.responseCode})."
            }
            val output = StringBuilder()
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(8192)
                var total = 0
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_PROFILE_BYTES) { "Loader profile metadata is too large." }
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

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
