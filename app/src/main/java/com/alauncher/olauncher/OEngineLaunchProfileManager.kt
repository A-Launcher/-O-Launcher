package com.alauncher.olauncher

import java.io.File
import java.util.Properties

/** A loader-aware profile keeps each Minecraft/loader combination isolated. */
enum class OEngineModLoader(val id: String, val displayName: String) {
    VANILLA("vanilla", "Vanilla"),
    FABRIC("fabric", "Fabric"),
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge"),
    OPTIFINE("optifine", "OptiFine"),
    QUILT("quilt", "Quilt");

    companion object {
        fun fromId(value: String?): OEngineModLoader =
            entries.firstOrNull { it.id.equals(value?.trim(), ignoreCase = true) } ?: VANILLA
    }
}

data class OEngineLaunchProfile(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loader: OEngineModLoader,
    val loaderVersion: String?,
    val gameDirectory: File
) {
    val isVanilla: Boolean get() = loader == OEngineModLoader.VANILLA
}

/**
 * Persists profiles inside one launcher instance. Loader installers must still
 * validate their artifacts before a profile is considered launch-ready.
 */
object OEngineLaunchProfileManager {
    private const val PROFILE_DIR = "profiles"
    private const val ACTIVE_FILE = "active-profile"
    private const val METADATA_FILE = "profile.properties"

    fun list(instanceRoot: File): List<OEngineLaunchProfile> =
        File(instanceRoot, PROFILE_DIR).listFiles()?.asSequence()
            ?.filter { it.isDirectory }
            ?.mapNotNull(::readProfile)
            ?.sortedWith(compareBy<OEngineLaunchProfile> { it.name.lowercase() }.thenBy { it.id })
            ?.toList()
            .orEmpty()

    fun active(instanceRoot: File): OEngineLaunchProfile? {
        val pointer = File(instanceRoot, ACTIVE_FILE)
        if (!pointer.isFile) return null
        val id = pointer.readText().trim()
        if (id.isBlank()) return null
        return list(instanceRoot).firstOrNull { it.id == id }
    }

    fun create(
        instanceRoot: File,
        name: String,
        minecraftVersion: String,
        loader: OEngineModLoader,
        loaderVersion: String? = null
    ): OEngineLaunchProfile {
        val cleanName = name.trim().replace(Regex("\\s+"), " ").take(48)
        require(cleanName.isNotBlank()) { "Enter a profile name." }
        val cleanMinecraft = minecraftVersion.trim()
        require(cleanMinecraft.matches(Regex("[A-Za-z0-9._-]{1,64}"))) {
            "Enter a valid Minecraft version identifier."
        }
        val cleanLoaderVersion = loaderVersion?.trim()?.takeIf { it.isNotBlank() }
        if (loader == OEngineModLoader.VANILLA) {
            require(cleanLoaderVersion == null) { "Vanilla profiles cannot have a loader version." }
        } else {
            require(cleanLoaderVersion != null && cleanLoaderVersion.length <= 80) {
                "Choose a loader version before creating this profile."
            }
        }

        val idBase = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            .ifBlank { "profile" }
        val id = (idBase + "-" + loader.id + "-" + cleanMinecraft).take(120)
        val profileRoot = File(File(instanceRoot, PROFILE_DIR), id)
        require(!profileRoot.exists()) { "A profile with this name, version and loader already exists." }
        require(profileRoot.mkdirs()) { "Could not create the profile directory." }

        val properties = Properties().apply {
            setProperty("id", id)
            setProperty("name", cleanName)
            setProperty("minecraftVersion", cleanMinecraft)
            setProperty("loader", loader.id)
            cleanLoaderVersion?.let { setProperty("loaderVersion", it) }
        }
        try {
            File(profileRoot, METADATA_FILE).outputStream().use {
                properties.store(it, "O Launcher launch profile")
            }
        } catch (error: Exception) {
            profileRoot.deleteRecursively()
            throw error
        }
        return OEngineLaunchProfile(
            id, cleanName, cleanMinecraft, loader, cleanLoaderVersion, File(profileRoot, "game")
        )
    }

    fun select(instanceRoot: File, profileId: String): OEngineLaunchProfile {
        val profile = list(instanceRoot).firstOrNull { it.id == profileId }
            ?: throw IllegalArgumentException("Unknown launch profile.")
        require(profile.gameDirectory.isDirectory || profile.gameDirectory.mkdirs()) {
            "Could not create the profile game directory."
        }
        val target = File(instanceRoot, ACTIVE_FILE)
        val temporary = File(instanceRoot, ACTIVE_FILE + ".tmp")
        temporary.writeText(profile.id)
        if (target.exists() && !target.delete()) {
            temporary.delete()
            throw IllegalStateException("Could not update the active profile.")
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return profile
    }

    private fun readProfile(directory: File): OEngineLaunchProfile? = runCatching {
        val metadata = File(directory, METADATA_FILE)
        require(metadata.isFile)
        val properties = Properties().apply { metadata.inputStream().use { load(it) } }
        val id = properties.getProperty("id") ?: return null
        require(id == directory.name)
        val name = properties.getProperty("name") ?: return null
        val minecraftVersion = properties.getProperty("minecraftVersion") ?: return null
        val loaderId = properties.getProperty("loader") ?: return null
        val loader = OEngineModLoader.entries.firstOrNull { it.id == loaderId } ?: return null
        val loaderVersion = properties.getProperty("loaderVersion")?.takeIf { it.isNotBlank() }
        if (loader == OEngineModLoader.VANILLA && loaderVersion != null) return null
        if (loader != OEngineModLoader.VANILLA && loaderVersion == null) return null
        OEngineLaunchProfile(id, name, minecraftVersion, loader, loaderVersion, File(directory, "game"))
    }.getOrNull()
}
