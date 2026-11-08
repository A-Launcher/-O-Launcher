package com.alauncher.olauncher

import org.json.JSONObject
import java.io.File

/**
 * Resolves the version profile produced by an official Forge/NeoForge installer.
 *
 * Call snapshot() before opening the installer UI, then resolve() after the installer
 * reports success. A profile is accepted only when it is new, inherits the selected
 * Minecraft version, and declares the expected loader library.
 */
object OEngineInstallerProfileWatcher {
    enum class Loader(val libraryPrefix: String) {
        FORGE("net.minecraftforge:forge:"),
        NEOFORGE("net.neoforged:neoforge:")
    }

    data class Snapshot internal constructor(val versionIds: Set<String>)

    data class InstalledProfile(
        val versionId: String,
        val versionJson: File,
        val loader: Loader,
        val minecraftVersion: String
    )

    fun snapshot(gameDirectory: File): Snapshot {
        val versions = File(gameDirectory, "versions").canonicalFile
        if (!versions.exists()) return Snapshot(emptySet())
        require(versions.isDirectory) { "Minecraft versions path is not a directory." }
        return Snapshot(versions.listFiles().orEmpty()
            .filter { it.isDirectory && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .map { it.name }
            .toSet())
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
        require(root.isDirectory) { "Minecraft versions directory is missing." }
        val candidates = root.listFiles().orEmpty().asSequence()
            .filter { it.isDirectory && it.name !in before.versionIds }
            .filter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .mapNotNull { directory ->
                val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return@mapNotNull null
                if (!canonical.toPath().startsWith(root.toPath())) return@mapNotNull null
                val jsonFile = File(canonical, "${directory.name}.json")
                if (!jsonFile.isFile || java.nio.file.Files.isSymbolicLink(jsonFile.toPath())) return@mapNotNull null
                val json = runCatching { JSONObject(jsonFile.readText()) }.getOrNull() ?: return@mapNotNull null
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
                0 -> "No newly-created ${loader.name} profile for Minecraft $minecraftVersion was found."
                else -> "More than one new ${loader.name} profile matches Minecraft $minecraftVersion; refusing to guess."
            }
        }
        return candidates.single()
    }
}
