package com.alauncher.olauncher

import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Resolves the version profile produced by an official Forge/NeoForge installer.
 * Snapshot JSON fingerprints before launch so both new and rewritten profile IDs
 * can be detected without accepting an unchanged, stale profile.
 */
object OEngineInstallerProfileWatcher {
    private const val MAX_PROFILE_JSON_BYTES = 8 * 1024 * 1024

    enum class Loader(val libraryPrefix: String) {
        FORGE("net.minecraftforge:forge:"),
        NEOFORGE("net.neoforged:neoforge:")
    }

    data class Snapshot internal constructor(internal val fingerprints: Map<String, String>)

    data class InstalledProfile(
        val versionId: String,
        val versionJson: File,
        val loader: Loader,
        val minecraftVersion: String
    )

    fun snapshot(gameDirectory: File): Snapshot {
        val versions = File(gameDirectory, "versions").canonicalFile
        if (!versions.exists()) return Snapshot(emptyMap())
        require(versions.isDirectory && !java.nio.file.Files.isSymbolicLink(versions.toPath())) {
            "Minecraft versions path is not a safe directory."
        }
        val fingerprints = linkedMapOf<String, String>()
        versions.listFiles().orEmpty().forEach { directory ->
            if (!directory.isDirectory || java.nio.file.Files.isSymbolicLink(directory.toPath())) return@forEach
            val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return@forEach
            if (!canonical.toPath().startsWith(versions.toPath())) return@forEach
            val json = File(canonical, "${directory.name}.json")
            if (!json.isFile || java.nio.file.Files.isSymbolicLink(json.toPath()) ||
                json.length() !in 1..MAX_PROFILE_JSON_BYTES.toLong()) return@forEach
            val bytes = runCatching { json.readBytes() }.getOrNull() ?: return@forEach
            fingerprints[directory.name] = sha256(bytes)
        }
        return Snapshot(fingerprints)
    }

    /** Rehydrates a previously persisted snapshot for the post-installer completion step. */
    fun restoreSnapshot(encoded: String): Snapshot {
        require(encoded.length <= 4 * 1024 * 1024) { "Installer profile snapshot is too large." }
        val json = JSONObject(encoded)
        require(json.length() <= 100_000) { "Installer profile snapshot contains too many entries." }
        val fingerprints = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val fingerprint = json.optString(id)
            require(id.isNotBlank() && id != "." && id != ".." &&
                !id.contains('/') && !id.contains('\\') && fingerprint.matches(Regex("[a-f0-9]{64}"))) {
                "Installer profile snapshot contains invalid data."
            }
            fingerprints[id] = fingerprint
        }
        return Snapshot(fingerprints)
    }

    fun resolve(
        gameDirectory: File,
        before: Snapshot,
        minecraftVersion: String,
        loader: Loader
    ): InstalledProfile {
        require(minecraftVersion.matches(Regex("""\d+(?:\.\d+){1,2}(?:[-+][A-Za-z0-9._-]+)?"""))) {
            "Invalid Minecraft version."
        }
        val root = File(gameDirectory, "versions").canonicalFile
        require(root.isDirectory && !java.nio.file.Files.isSymbolicLink(root.toPath())) {
            "Minecraft versions directory is missing or unsafe."
        }
        val candidates = root.listFiles().orEmpty().asSequence()
            .filter { it.isDirectory && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .mapNotNull { directory ->
                val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return@mapNotNull null
                if (!canonical.toPath().startsWith(root.toPath())) return@mapNotNull null
                val jsonFile = File(canonical, "${directory.name}.json")
                if (!jsonFile.isFile || java.nio.file.Files.isSymbolicLink(jsonFile.toPath()) ||
                    jsonFile.length() !in 1..MAX_PROFILE_JSON_BYTES.toLong()) return@mapNotNull null
                val bytes = runCatching { jsonFile.readBytes() }.getOrNull() ?: return@mapNotNull null
                if (before.fingerprints[directory.name] == sha256(bytes)) return@mapNotNull null
                val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return@mapNotNull null
                if (json.optString("id") != directory.name) return@mapNotNull null
                if (json.optString("inheritsFrom") != minecraftVersion) return@mapNotNull null
                val libraries = json.optJSONArray("libraries") ?: return@mapNotNull null
                val hasLoader = (0 until libraries.length()).any { index ->
                    val name = libraries.optJSONObject(index)?.optString("name").orEmpty()
                    name.startsWith(loader.libraryPrefix)
                }
                if (!hasLoader) return@mapNotNull null
                InstalledProfile(directory.name, jsonFile, loader, minecraftVersion)
            }.toList()

        require(candidates.size == 1) {
            when (candidates.size) {
                0 -> "No new or updated ${loader.name} profile for Minecraft $minecraftVersion was found."
                else -> "More than one changed ${loader.name} profile matches Minecraft $minecraftVersion; refusing to guess."
            }
        }
        return candidates.single()
    }

    /**
     * Finalizes a persisted installer attempt after the Mojo AWT activity returns.
     * Pending state is retained on every validation failure so a later recovery can retry.
     */
    fun completePending(
        gameDirectory: File,
        preferences: SharedPreferences,
        profileKey: String
    ): InstalledProfile {
        require(profileKey.isNotBlank() && !profileKey.contains('/') && !profileKey.contains('\\\\')) {
            "Invalid launcher profile key."
        }
        val snapshotKey = "pending_installer_profile_snapshot.$profileKey"
        val loaderKey = "pending_installer_loader.$profileKey"
        val versionKey = "pending_installer_mc.$profileKey"
        val encodedSnapshot = preferences.getString(snapshotKey, null)
            ?: error("No pending installer profile snapshot was found.")
        val loaderName = preferences.getString(loaderKey, null)
            ?: error("Pending installer loader is missing.")
        val minecraftVersion = preferences.getString(versionKey, null)
            ?: error("Pending Minecraft version is missing.")
        val loader = when (loaderName) {
            "Forge" -> Loader.FORGE
            "NeoForge" -> Loader.NEOFORGE
            else -> error("Unsupported pending installer loader.")
        }
        val before = restoreSnapshot(encodedSnapshot)
        val installed = resolve(gameDirectory, before, minecraftVersion, loader)
        val saved = preferences.edit()
            .putString("loader_profile.$profileKey", installed.versionId)
            .remove(snapshotKey)
            .remove(loaderKey)
            .remove(versionKey)
            .remove("pending_installer_agent.$profileKey")
            .remove("pending_installer_args.$profileKey")
            .commit()
        check(saved) { "Could not save the installed loader profile." }
        return installed
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
