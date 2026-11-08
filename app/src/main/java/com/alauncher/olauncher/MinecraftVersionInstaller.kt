package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class MinecraftRelease(val id: String, val type: String, val url: String)
data class ClientDownloadResult(val version: String, val file: File, val bytes: Long)

object MinecraftVersionInstaller {
    private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000

    suspend fun fetchReleases(): Pair<List<MinecraftRelease>, String> = withContext(Dispatchers.IO) {
        parseVersionManifest(JSONObject(requestText(MANIFEST_URL)))
    }

    internal fun isSafeVersionId(versionId: String): Boolean =
        versionId.matches(Regex("[A-Za-z0-9._-]{1,128}"))

    internal fun resolveJavaCompatibility(
        versionId: String,
        baseMetadata: JSONObject,
        loaderMetadata: JSONObject?
    ): OEngineJavaCompatibility {
        val loaderMajor = loaderMetadata?.optJSONObject("javaVersion")
            ?.optInt("majorVersion")?.takeIf { it > 0 }
        val baseMajor = baseMetadata.optJSONObject("javaVersion")
            ?.optInt("majorVersion")?.takeIf { it > 0 }
        return OEngineJavaCompatibility.resolve(versionId, loaderMajor ?: baseMajor)
    }

    internal fun parseVersionManifest(manifest: JSONObject): Pair<List<MinecraftRelease>, String> {
        val latest = manifest.getJSONObject("latest").optString("release")
        val entries = manifest.getJSONArray("versions")
        val releases = buildList {
            for (index in 0 until entries.length()) {
                val item = entries.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val type = item.optString("type")
                val url = item.optString("url")
                // Mojang's manifest is the source of truth. Do not whitelist known
                // release categories: future snapshots, experiments, and new version
                // channels should appear without requiring a launcher update.
                if (isSafeVersionId(id) && type.isNotBlank() && url.startsWith("https://")) {
                    add(MinecraftRelease(id, type, url))
                }
            }
        }
        return releases to latest
    }

    suspend fun downloadClient(
        version: MinecraftRelease,
        gameDirectory: File,
        onProgress: (Long, Long) -> Unit
    ): ClientDownloadResult = withContext(Dispatchers.IO) {
        require(isSafeVersionId(version.id)) { "Minecraft version ID is invalid." }
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
        if (target.isFile && target.length() > 0L &&
            (expectedSize <= 0L || target.length() == expectedSize) &&
            (expectedSha1.isBlank() || sha1(target).equals(expectedSha1, ignoreCase = true))
        ) {
            return@withContext ClientDownloadResult(version.id, target, target.length())
        }
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


    data class DependencyInstallResult(val libraries: Int, val assets: Int, val mappedAssets: Int, val nativeArchives: Int, val nativeFiles: Int, val bytes: Long)

    suspend fun downloadLibrariesAndAssets(
        version: MinecraftRelease,
        gameDirectory: File,
        onProgress: (Long, Long, String) -> Unit
    ): DependencyInstallResult = withContext(Dispatchers.IO) {
        require(isSafeVersionId(version.id)) { "Minecraft version ID is invalid." }
        val metadata = JSONObject(requestText(version.url))
        val libraries = metadata.optJSONArray("libraries")
        val assetIndex = metadata.optJSONObject("assetIndex")
            ?: throw IllegalStateException("This Minecraft version has no asset index.")

        data class Task(
            val url: String,
            val file: File,
            val sha1: String,
            val size: Long,
            val label: String,
            val nativeArchive: Boolean = false
        )
        val tasks = mutableListOf<Task>()
        val librariesDirectory = File(gameDirectory, "libraries")
        var libraryCount = 0
        var nativeArchiveCount = 0

        if (libraries != null) {
            for (i in 0 until libraries.length()) {
                val library = libraries.optJSONObject(i) ?: continue
                if (!isAllowedForAndroid(library.optJSONArray("rules"))) continue
                val artifact = library.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
                val relativePath = artifact.optString("path")
                val url = artifact.optString("url")
                if (relativePath.isBlank() || url.isBlank()) continue
                val target = File(librariesDirectory, relativePath).canonicalFile
                if (!target.toPath().startsWith(librariesDirectory.canonicalFile.toPath())) continue
                tasks.add(Task(url, target, artifact.optString("sha1"), artifact.optLong("size", -1L), "Library: " + library.optString("name")))
                libraryCount++

                // Mojang's metadata commonly describes Linux native classifiers.
                // They are staged for inspection; Linux ELF is not assumed Android-compatible.
                val nativeMap = library.optJSONObject("natives")
                val nativeTemplate = nativeMap?.optString("linux")
                if (!nativeTemplate.isNullOrBlank()) {
                    val deviceArch = AndroidRuntimeInstaller.deviceArchitecture()
                    val archTokens = when (deviceArch) {
                        "arm64" -> listOf("arm64", "aarch64")
                        "arm" -> listOf("arm32", "arm")
                        "x86_64" -> listOf("64", "x86_64")
                        else -> listOf("32", "x86")
                    }
                    val classifierNames = if (nativeTemplate.contains('$' + "{arch}")) {
                        archTokens.map { nativeTemplate.replace('$' + "{arch}", it) }
                    } else listOf(nativeTemplate)
                    val classifiers = library.optJSONObject("downloads")?.optJSONObject("classifiers")
                    val classifier = classifierNames.firstNotNullOfOrNull { classifiers?.optJSONObject(it) }
                    if (classifier != null) {
                        val nativePath = classifier.optString("path")
                        val nativeUrl = classifier.optString("url")
                        if (nativePath.isNotBlank() && nativeUrl.isNotBlank()) {
                            val nativeTarget = File(librariesDirectory, nativePath).canonicalFile
                            if (nativeTarget.toPath().startsWith(librariesDirectory.canonicalFile.toPath())) {
                                tasks.add(Task(nativeUrl, nativeTarget, classifier.optString("sha1"),
                                    classifier.optLong("size", -1L), "Native library archive: " + library.optString("name"),
                                    nativeArchive = true))
                                nativeArchiveCount++
                            }
                        }
                    }
                }
            }
        }

        val loggingFile = metadata.optJSONObject("logging")?.optJSONObject("client")?.optJSONObject("file")
        if (loggingFile != null) {
            val loggingPath = loggingFile.optString("id").ifBlank { version.id + "-client.xml" }
            val safeLoggingName = File(loggingPath).name
            tasks.add(Task(
                loggingFile.getString("url"),
                File(File(gameDirectory, "versions/" + version.id), safeLoggingName),
                loggingFile.optString("sha1"),
                loggingFile.optLong("size", -1L),
                "Client logging configuration"
            ))
        }

        val indexId = assetIndex.optString("id").ifBlank { version.id }
        require(indexId.matches(Regex("[A-Za-z0-9._-]{1,128}"))) {
            "Minecraft asset index contains an invalid index ID."
        }
        val indexRoot = OEngineAssetObjectPath.resolveRoot(gameDirectory, "assets/indexes")
        val indexFile = OEngineAssetObjectPath.resolveMapped(indexRoot, "$indexId.json")
        val indexUrl = assetIndex.getString("url")
        val indexSha1 = assetIndex.optString("sha1")
        val indexSize = assetIndex.optLong("size", -1L)
        require(indexSize > 0L && indexSize <= 64L * 1024L * 1024L) {
            "Minecraft asset index has an invalid or excessive declared size."
        }
        if (!indexFile.isFile || indexFile.length() != indexSize ||
            (indexSha1.isNotBlank() && !sha1(indexFile).equals(indexSha1, ignoreCase = true))
        ) {
            downloadVerified(indexUrl, indexFile, indexSha1, indexSize) { received ->
                onProgress(received, indexSize, "Downloading and verifying asset index")
            }
        }
        // Parse only the exact index file whose Mojang-provided size and SHA-1 passed.
        val assetIndexMetadata = JSONObject(indexFile.readText())
        tasks.add(Task(indexUrl, indexFile, indexSha1, indexSize, "Asset index"))

        val totalObjects = assetIndexMetadata.getJSONObject("objects")
        val objectBase = OEngineAssetObjectPath.resolveRoot(gameDirectory, "assets/objects")
        var assetCount = 0
        val iterator = totalObjects.keys()
        while (iterator.hasNext()) {
            val name = iterator.next()
            val item = totalObjects.getJSONObject(name)
            val hash = item.getString("hash").lowercase()
            val target = OEngineAssetObjectPath.resolve(objectBase, hash)
            val assetSize = item.optLong("size", -1L)
            require(assetSize >= 0L) { "Asset index contains an invalid size for $name." }
            tasks.add(Task(
                "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash,
                target, hash, assetSize, "Asset: " + name
            ))
            assetCount++
        }

        val totalBytes = tasks.sumOf { if (it.size > 0L) it.size else 0L }
        var completedBytes = 0L
        var completedTasks = 0
        for (task in tasks) {
            if (task.file.exists() && task.file.length() > 0L &&
                (task.sha1.isBlank() || sha1(task.file).equals(task.sha1, ignoreCase = true)) &&
                (task.size <= 0L || task.file.length() == task.size)
            ) {
                completedBytes += if (task.size > 0L) task.size else task.file.length()
                completedTasks++
                onProgress(completedBytes, totalBytes, "Already verified: " + task.label + " (" + completedTasks + "/" + tasks.size + ")")
                continue
            }
            val actual = downloadVerified(task.url, task.file, task.sha1, task.size) { received ->
                onProgress(completedBytes + received, totalBytes, task.label + " (" + (completedTasks + 1) + "/" + tasks.size + ")")
            }
            completedBytes += if (task.size > 0L) task.size else actual
            completedTasks++
            onProgress(completedBytes, totalBytes, "Downloaded: " + task.label + " (" + completedTasks + "/" + tasks.size + ")")
        }
        // Linux classifiers are extracted only for inspection, never treated as Android natives.
        val nativeInspectionRoot = File(gameDirectory, "natives/${version.id}/linux-archive")
        val nativeFiles = tasks.filter { it.nativeArchive && it.file.isFile }
            .sumOf { OEngineNativeArchiveInspector.extractLinuxNativeArchive(it.file, nativeInspectionRoot) }
        if (nativeFiles > 0) {
            val expectedAbi = when (AndroidRuntimeInstaller.deviceArchitecture()) {
                "arm64" -> "arm64-v8a"
                "arm" -> "armeabi-v7a"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> "unknown"
            }
            OEngineElfCompatibilityInspector.writeReport(
                nativeInspectionRoot,
                expectedAbi,
                File(nativeInspectionRoot, "oengine-compatibility-report.json")
            )
        }

        val mappedAssets = materializeLegacyAssetMappings(
            gameDirectory = gameDirectory,
            indexId = indexId,
            objects = totalObjects,
            createVirtual = assetIndexMetadata.optBoolean("virtual", false),
            mapToResources = assetIndexMetadata.optBoolean("map_to_resources", false)
        )
        DependencyInstallResult(libraryCount, assetCount, mappedAssets, nativeArchiveCount, nativeFiles, completedBytes)
    }

    private fun materializeLegacyAssetMappings(
        gameDirectory: File,
        indexId: String,
        objects: JSONObject,
        createVirtual: Boolean,
        mapToResources: Boolean
    ): Int {
        if (!createVirtual && !mapToResources) return 0
        val objectBase = OEngineAssetObjectPath.resolveRoot(gameDirectory, "assets/objects")
        val destinations = buildList {
            if (createVirtual) add(OEngineAssetObjectPath.resolveRoot(gameDirectory, "assets/virtual/$indexId"))
            if (mapToResources) add(OEngineAssetObjectPath.resolveRoot(gameDirectory, "resources"))
        }
        var created = 0
        val iterator = objects.keys()
        while (iterator.hasNext()) {
            val assetName = iterator.next()
            val item = objects.getJSONObject(assetName)
            val hash = item.optString("hash").lowercase()
            val source = OEngineAssetObjectPath.resolve(objectBase, hash)
            val mappedSize = item.optLong("size", -1L)
            require(mappedSize >= 0L) { "Asset index contains an invalid size for $assetName." }
            if (!source.isFile || source.length() != mappedSize) {
                throw IllegalStateException("Required asset object is missing or has an invalid size: $assetName")
            }
            if (!sha1(source).equals(hash, ignoreCase = true)) {
                throw IllegalStateException("Required asset object failed SHA-1 verification: $assetName")
            }
            for (destinationRoot in destinations) {
                val target = OEngineAssetObjectPath.resolveMapped(destinationRoot, assetName)
                if (target.isFile && target.length() == source.length() &&
                    sha1(target).equals(hash, ignoreCase = true)
                ) continue
                val parent = target.parentFile ?: continue
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IllegalStateException("Could not create legacy asset folder for $assetName")
                }
                val temporary = File(parent, target.name + ".part")
                if (temporary.exists()) temporary.delete()
                source.copyTo(temporary, overwrite = true)
                if (!sha1(temporary).equals(hash, ignoreCase = true)) {
                    temporary.delete()
                    throw IllegalStateException("Legacy asset mapping verification failed: $assetName")
                }
                if (target.exists() && !target.delete()) {
                    temporary.delete()
                    throw IllegalStateException("Could not replace legacy asset mapping: $assetName")
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                created++
            }
        }
        return created
    }

    data class LaunchPlan(
        val version: String,
        val mainClass: String,
        val javaMajorVersion: Int,
        val targetJavaMajor: Int = javaMajorVersion,
        val javaCompatibilityNote: String = "Java $javaMajorVersion runtime selected.",
        val releaseTime: String? = null,
        val classpath: List<String>,
        val gameArguments: List<String>,
        val jvmArguments: List<String>
    )

    suspend fun prepareLaunchPlan(
        version: MinecraftRelease,
        gameDirectory: File,
        playerName: String = "Player",
        playerUuid: String = "00000000000000000000000000000000",
        accessToken: String = "0",
        loaderProfileId: String? = null,
        maxMemoryMb: Int = 2048
    ): LaunchPlan = withContext(Dispatchers.IO) {
        require(isSafeVersionId(version.id)) { "Minecraft version ID is invalid." }
        val versionDirectory = File(File(gameDirectory, "versions"), version.id)
        val metadataFile = File(versionDirectory, "${version.id}.json")
        val clientJar = File(versionDirectory, "${version.id}.jar")
        if (!metadataFile.isFile || !clientJar.isFile || clientJar.length() == 0L) {
            throw IllegalStateException("Download and verify the Minecraft client before preparing launch settings.")
        }
        val metadata = JSONObject(metadataFile.readText())
        val loaderMetadata = loaderProfileId?.let { id ->
            require(id.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Invalid loader profile ID." }
            val loaderFile = File(File(gameDirectory, "versions/$id"), "$id.json")
            require(loaderFile.isFile) { "Install the selected loader profile before launching." }
            JSONObject(loaderFile.readText()).also { profile ->
                require(profile.optString("inheritsFrom") == version.id) {
                    "The selected loader profile does not inherit from Minecraft ${version.id}."
                }
            }
        }
        val launchVersionId = loaderMetadata?.optString("id")?.takeIf { it.isNotBlank() } ?: version.id
        val javaCompatibility = resolveJavaCompatibility(version.id, metadata, loaderMetadata)
        javaCompatibility.nativeLaunchBlocker()?.let { throw IllegalStateException(it) }
        val javaMajorVersion = javaCompatibility.runtimeJavaMajor
        val mainClass = loaderMetadata?.optString("mainClass")?.takeIf { it.isNotBlank() }
            ?: metadata.optString("mainClass")
        if (mainClass.isBlank()) throw IllegalStateException("Minecraft version metadata does not contain a main class.")
        val classpath = mutableListOf<String>()
        classpath.add(clientJar.absolutePath)
        val libraryRoot = File(gameDirectory, "libraries").canonicalFile
        for (source in listOfNotNull(metadata, loaderMetadata)) {
            val libraries = source.optJSONArray("libraries") ?: continue
            for (i in 0 until libraries.length()) {
                val library = libraries.optJSONObject(i) ?: continue
                if (!isAllowedForAndroid(library.optJSONArray("rules"))) continue
                val artifact = library.optJSONObject("downloads")?.optJSONObject("artifact")
                val relative = artifact?.optString("path")?.takeIf { it.isNotBlank() }
                    ?: OEngineLoaderInstaller.mavenArtifactPath(library.optString("name"))?.takeIf {
                        library.optString("name").isNotBlank()
                    }
                    ?: continue
                val file = File(libraryRoot, relative).canonicalFile
                if (!file.toPath().startsWith(libraryRoot.toPath())) continue
                if (!file.isFile || file.length() == 0L) {
                    throw IllegalStateException("Required Minecraft/loader library is missing: ${library.optString("name")}")
                }
                classpath.add(file.absolutePath)
            }
        }
        val assetIndex = metadata.optJSONObject("assetIndex")?.optString("id").orEmpty()
        val values = mapOf(
            "auth_player_name" to playerName,
            "version_name" to launchVersionId,
            "game_directory" to gameDirectory.absolutePath,
            "assets_root" to File(gameDirectory, "assets").absolutePath,
            "assets_index_name" to assetIndex,
            "auth_uuid" to playerUuid.replace("-", ""),
            "auth_access_token" to accessToken,
            "clientid" to "",
            "auth_xuid" to "",
            "user_type" to if (accessToken.isNotBlank() && accessToken != "0") "msa" else "legacy",
            "version_type" to metadata.optString("type", "release"),
            "user_properties" to "{}",
            "classpath" to classpath.joinToString(File.pathSeparator)
        )
        fun substitute(value: String): String {
            var result = value
            values.forEach { (key, replacement) -> result = result.replace("${'$'}{$key}", replacement) }
            return result
        }
        fun addArgument(target: MutableList<String>, value: Any?) {
            when (value) {
                is org.json.JSONArray -> for (i in 0 until value.length()) addArgument(target, value.opt(i))
                is String -> target.add(substitute(value))
                else -> Unit
            }
        }
        fun collectArguments(array: org.json.JSONArray?, target: MutableList<String>) {
            if (array == null) return
            for (i in 0 until array.length()) {
                when (val item = array.opt(i)) {
                    is String -> target.add(substitute(item))
                    is JSONObject -> {
                        if (!isAllowedForAndroid(item.optJSONArray("rules"))) continue
                        addArgument(target, item.opt("value"))
                    }
                }
            }
        }
        val argumentSources = listOfNotNull(metadata.optJSONObject("arguments"), loaderMetadata?.optJSONObject("arguments"))
        val gameArguments = mutableListOf<String>()
        if (argumentSources.isNotEmpty()) {
            argumentSources.forEach { collectArguments(it.optJSONArray("game"), gameArguments) }
        } else {
            val legacyValues = listOf(metadata.optString("minecraftArguments"), loaderMetadata?.optString("minecraftArguments").orEmpty())
            for (legacy in legacyValues.filter { it.isNotBlank() }) {
                val tokens = Regex("""\"([^\"]*)\"|'([^']*)'|(\\S+)""")
                    .findAll(legacy)
                    .map { match ->
                        match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value.orEmpty()
                    }
                tokens.forEach { gameArguments.add(substitute(it)) }
            }
        }
        val jvmArguments = mutableListOf<String>()
        argumentSources.forEach { collectArguments(it.optJSONArray("jvm"), jvmArguments) }
        applyMemoryArguments(jvmArguments, maxMemoryMb)
        LaunchPlan(
            version = launchVersionId,
            mainClass = mainClass,
            javaMajorVersion = javaMajorVersion,
            targetJavaMajor = javaCompatibility.targetJavaMajor,
            javaCompatibilityNote = javaCompatibility.explanation,
            releaseTime = metadata.optString("releaseTime").takeIf { it.isNotBlank() },
            classpath = classpath.distinct(),
            gameArguments = gameArguments,
            jvmArguments = jvmArguments
        )
    }

    internal fun applyMemoryArguments(arguments: MutableList<String>, maxMemoryMb: Int) {
        require(maxMemoryMb in 512..8192) { "Minecraft memory must be between 512 MiB and 8192 MiB." }
        arguments.removeAll { argument ->
            argument.startsWith("-Xmx", ignoreCase = true) ||
                argument.startsWith("-Xms", ignoreCase = true)
        }
        arguments += "-Xms512M"
        arguments += "-Xmx${maxMemoryMb}M"
    }

    internal fun isAllowedForAndroid(rules: org.json.JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (index in 0 until rules.length()) {
            val rule = rules.optJSONObject(index) ?: continue
            val os = rule.optJSONObject("os")
            var matches = true
            if (os != null) {
                val name = os.optString("name")
                if (name.isNotBlank() && name != "linux") matches = false
                val arch = os.optString("arch")
                if (arch.isNotBlank() && !Regex(arch).containsMatchIn(System.getProperty("os.arch", "aarch64"))) matches = false
                val version = os.optString("version")
                if (version.isNotBlank() && !Regex(version).containsMatchIn(System.getProperty("os.version", ""))) matches = false
            }
            val features = rule.optJSONObject("features")
            if (features != null) {
                val keys = features.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (features.optBoolean(key, false)) matches = false
                }
            }
            if (matches) allowed = rule.optString("action") == "allow"
        }
        return allowed
    }

    private fun downloadVerified(
        address: String,
        target: File,
        expectedSha1: String,
        expectedSize: Long,
        onBytes: (Long) -> Unit
    ): Long {
        val parent = target.parentFile ?: throw IllegalStateException("Invalid download destination.")
        if (!parent.exists() && !parent.mkdirs()) throw IllegalStateException("Could not create download folder.")
        val temporary = File(parent, target.name + ".part")
        if (temporary.exists()) temporary.delete()
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "O-Launcher/0.1.0")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IllegalStateException("Download failed (HTTP " + connection.responseCode + ").")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        onBytes(received)
                    }
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        val actualSize = temporary.length()
        if (actualSize <= 0L || (expectedSize > 0L && actualSize != expectedSize)) {
            temporary.delete()
            throw IllegalStateException("Downloaded file size mismatch for " + target.name + ".")
        }
        if (expectedSha1.isNotBlank() && !sha1(temporary).equals(expectedSha1, ignoreCase = true)) {
            temporary.delete()
            throw IllegalStateException("SHA-1 verification failed for " + target.name + ".")
        }
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IllegalStateException("Could not replace " + target.name + ".")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return target.length()
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
