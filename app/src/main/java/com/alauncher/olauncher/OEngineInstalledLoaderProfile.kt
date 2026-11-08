package com.alauncher.olauncher

import android.content.SharedPreferences
import org.json.JSONObject
import java.io.File

/**
 * Resolves the exact installed loader profile selected by the current launcher profile.
 * The persisted label alone is not trusted: the profile JSON and its loader artifact
 * must match the selected loader and Minecraft base version before launch.
 */
object OEngineInstalledLoaderProfile {
    private data class Spec(val storedName: String, val coordinatePrefix: String)

    private fun spec(loader: String): Spec? = when (loader) {
        "Fabric" -> Spec("Fabric", "net.fabricmc:fabric-loader:")
        "Quilt" -> Spec("Quilt", "org.quiltmc:quilt-loader:")
        "Forge" -> Spec("Forge", "net.minecraftforge:forge:")
        "NeoForge" -> Spec("NeoForge", "net.neoforged:neoforge:")
        "OptiFine" -> Spec("OptiFine", "optifine:OptiFine:")
        "Vanilla" -> null
        else -> error("Unsupported selected Minecraft loader: $loader")
    }

    fun resolve(
        gameDirectory: File,
        preferences: SharedPreferences,
        profileKey: String,
        selectedLoader: String,
        minecraftVersion: String
    ): String? {
        require(profileKey.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Invalid launcher profile key." }
        require(minecraftVersion.matches(Regex("""\d+(?:\.\d+){1,2}(?:[-+][A-Za-z0-9._-]+)?"""))) {
            "Invalid selected Minecraft version."
        }
        val expected = spec(selectedLoader) ?: return null
        val savedLoader = preferences.getString("loader_profile_loader.$profileKey", null)
        val savedMinecraft = preferences.getString("loader_profile_mc.$profileKey", null)
        require(savedLoader == expected.storedName && savedMinecraft == minecraftVersion) {
            "Install $selectedLoader for Minecraft $minecraftVersion before launching this profile."
        }
        val profileId = preferences.getString("loader_profile.$profileKey", null)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,128}")) }
            ?: error("No verified $selectedLoader profile is selected for this launcher profile.")
        val versionsRootPath = File(gameDirectory, "versions")
        require(!java.nio.file.Files.isSymbolicLink(versionsRootPath.toPath())) {
            "Minecraft versions directory must not be a symbolic link."
        }
        val versionsRoot = versionsRootPath.canonicalFile
        val profileDirectoryPath = File(versionsRoot, profileId)
        require(!java.nio.file.Files.isSymbolicLink(profileDirectoryPath.toPath())) {
            "Installed loader profile directory must not be a symbolic link."
        }
        val profileDirectory = profileDirectoryPath.canonicalFile
        require(profileDirectory.toPath().startsWith(versionsRoot.toPath()) && profileDirectory.isDirectory) {
            "Installed loader profile directory is missing or unsafe."
        }
        val jsonPath = File(profileDirectory, "$profileId.json")
        require(!java.nio.file.Files.isSymbolicLink(jsonPath.toPath()) && jsonPath.isFile &&
            jsonPath.length() in 1..8L * 1024L * 1024L) {
            "Installed loader profile metadata is missing or unsafe."
        }
        val json = JSONObject(jsonPath.readText())
        require(json.optString("id") == profileId &&
            json.optString("inheritsFrom") == minecraftVersion) {
            "Installed loader profile no longer matches Minecraft $minecraftVersion."
        }
        require(json.optString("mainClass").isNotBlank()) {
            "Installed $selectedLoader profile does not define a main class."
        }
        val libraries = json.optJSONArray("libraries")
            ?: error("Installed $selectedLoader profile has no library metadata.")
        val libraryRootPath = File(gameDirectory, "libraries")
        require(!java.nio.file.Files.isSymbolicLink(libraryRootPath.toPath())) {
            "Minecraft libraries directory must not be a symbolic link."
        }
        val libraryRoot = libraryRootPath.canonicalFile
        var loaderArtifactFound = false
        for (index in 0 until libraries.length()) {
            val library = libraries.optJSONObject(index) ?: continue
            val coordinate = library.optString("name")
            if (!coordinate.startsWith(expected.coordinatePrefix)) continue
            val relative = library.optJSONObject("downloads")?.optJSONObject("artifact")
                ?.optString("path")?.takeIf { it.isNotBlank() }
                ?: OEngineLoaderInstaller.mavenArtifactPath(coordinate)
                ?: continue
            val artifactPath = File(libraryRoot, relative)
            if (java.nio.file.Files.isSymbolicLink(artifactPath.toPath())) continue
            val artifact = artifactPath.canonicalFile
            if (!artifact.toPath().startsWith(libraryRoot.toPath()) ||
                !artifact.isFile || artifact.length() <= 0L) continue
            loaderArtifactFound = true
            break
        }
        require(loaderArtifactFound) {
            "The $selectedLoader profile exists, but its loader library is missing. Reinstall the loader before launching."
        }
        return profileId
    }
}
