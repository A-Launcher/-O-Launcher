package com.alauncher.olauncher

import java.io.File
import java.util.Properties
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

    private fun profilesRoot(instanceRoot: File, create: Boolean = false): File? {
        val instance = instanceRoot.canonicalFile
        val candidate = File(instanceRoot, PROFILE_DIR)
        if (Files.isSymbolicLink(candidate.toPath())) return null
        if (!candidate.exists() && create && !candidate.mkdirs()) return null
        if (!candidate.exists()) return null
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        if (canonical.parentFile != instance || !canonical.isDirectory) return null
        return canonical
    }

    fun list(instanceRoot: File): List<OEngineLaunchProfile> =
        profilesRoot(instanceRoot)?.listFiles()?.asSequence()
            ?.filter { it.isDirectory && !Files.isSymbolicLink(it.toPath()) }
            ?.mapNotNull(::readProfile)
            ?.sortedWith(compareBy<OEngineLaunchProfile> { it.name.lowercase() }.thenBy { it.id })
            ?.toList()
            .orEmpty()

    fun active(instanceRoot: File): OEngineLaunchProfile? {
        val pointer = File(instanceRoot, ACTIVE_FILE)
        if (Files.isSymbolicLink(pointer.toPath()) || !pointer.isFile) return null
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
        val loaderBuild = cleanLoaderVersion
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9._+-]+"), "-")
            ?.trim('-')
        // Keep distinct loader builds side by side; the old ID omitted loaderVersion,
        // making two Forge/Fabric builds for the same MC version collide.
        val id = listOfNotNull(
            idBase.take(40),
            loader.id,
            cleanMinecraft.take(48),
            loaderBuild?.take(40)
        ).joinToString("-")
        val profiles = profilesRoot(instanceRoot, create = true)
            ?: throw IllegalStateException("The profiles directory is missing or unsafe.")
        val profileRoot = File(profiles, id)
        require(profileRoot.canonicalFile.parentFile == profiles && !profileRoot.exists()) {
            "A profile with this name, version and loader already exists or its path is unsafe."
        }
        require(profileRoot.mkdir()) { "Could not create the profile directory." }

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
        val profileRoot = profile.gameDirectory.parentFile.canonicalFile
        val gameDirectory = profile.gameDirectory
        require(!Files.isSymbolicLink(gameDirectory.toPath())) {
            "The profile game directory cannot be a symbolic link."
        }
        require(gameDirectory.canonicalFile.parentFile == profileRoot) {
            "The profile game directory resolves outside its profile."
        }
        require(gameDirectory.isDirectory || gameDirectory.mkdirs()) {
            "Could not create the profile game directory."
        }
        require(!Files.isSymbolicLink(gameDirectory.toPath()) &&
            gameDirectory.canonicalFile.parentFile == profileRoot) {
            "The profile game directory path is unsafe."
        }
        val target = File(instanceRoot, ACTIVE_FILE)
        val temporary = File(instanceRoot, ACTIVE_FILE + ".tmp")
        try {
            require(!Files.isSymbolicLink(target.toPath()) && !Files.isSymbolicLink(temporary.toPath())) {
                "The active profile pointer path is unsafe."
            }
            temporary.writeText(profile.id)
            try {
                Files.move(
                    temporary.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("Could not update the active profile.", error)
        }
        return profile
    }

    private fun readProfile(directory: File): OEngineLaunchProfile? = runCatching {
        val profilesCandidate = File(directory.parentFile, PROFILE_DIR)
        require(!Files.isSymbolicLink(profilesCandidate.toPath()))
        val profilesRoot = profilesCandidate.canonicalFile
        require(profilesRoot.parentFile == directory.parentFile.canonicalFile)
        require(directory.canonicalFile.parentFile == profilesRoot)
        val metadata = File(directory, METADATA_FILE)
        require(!Files.isSymbolicLink(metadata.toPath()) && metadata.isFile)
        val gameDirectory = File(directory, "game")
        require(!Files.isSymbolicLink(gameDirectory.toPath()))
        require(gameDirectory.canonicalFile.parentFile == directory.canonicalFile)
        val properties = Properties().apply { metadata.inputStream().use { load(it) } }
        val id = properties.getProperty("id") ?: return null
        require(id == directory.name)
        val name = properties.getProperty("name") ?: return null
        val minecraftVersion = properties.getProperty("minecraftVersion") ?: return null
        val loaderId = properties.getProperty("loader") ?: return null
        val loader = OEngineModLoader.entries.firstOrNull { it.id == loaderId } ?: return null
        val loaderVersion = properties.getProperty("loaderVersion")?.takeIf { it.isNotBlank() }
        if (loader == OEngineModLoader.VANILLA && loaderVersion != null) return null
        if (loader != OEngineModLoader.VANILLA && loaderVersion.isNullOrBlank()) return null
        require(name.isNotBlank() && name.length <= 48)
        require(minecraftVersion.matches(Regex("[A-Za-z0-9._-]{1,64}")))
        if (loaderVersion != null) require(loaderVersion.length <= 80)
        OEngineLaunchProfile(id, name, minecraftVersion, loader, loaderVersion, gameDirectory)
    }.getOrNull()
}
