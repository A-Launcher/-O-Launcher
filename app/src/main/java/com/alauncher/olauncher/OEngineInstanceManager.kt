package com.alauncher.olauncher

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

data class OEngineInstance(val id: String, val name: String, val root: File)

/** Isolated per-instance directories; Default preserves the original game folder. */
object OEngineInstanceManager {
    private const val PREFS = "oengine_instances"
    private const val ACTIVE_KEY = "active_instance"
    private const val DEFAULT_ID = "default"

    internal fun sanitizeDisplayName(displayName: String): String =
        displayName.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "")
            .replace(Regex("\\s+"), " ").take(40)

    internal fun instanceIdFor(displayName: String): String =
        sanitizeDisplayName(displayName).lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "instance" }

    internal fun gameRootFor(base: File, instanceDirectory: File, sharedGameFolder: Boolean): File =
        if (sharedGameFolder) base else File(instanceDirectory, "game")

    /** Returns only the real instances directory directly inside the Minecraft root. */
    internal fun instancesRootFor(base: File, create: Boolean = false): File? {
        if (Files.isSymbolicLink(base.toPath())) return null
        if (!base.exists() && create && !base.mkdirs()) return null
        if (!base.isDirectory) return null
        val canonicalBase = runCatching { base.canonicalFile }.getOrNull() ?: return null
        val candidate = File(base, "instances")
        if (Files.isSymbolicLink(candidate.toPath())) return null
        if (!candidate.exists() && create && !candidate.mkdir()) return null
        if (!candidate.isDirectory) return null
        val canonical = runCatching { candidate.canonicalFile }.getOrNull() ?: return null
        return canonical.takeIf { it.parentFile == canonicalBase }
    }

    internal fun isSafeInstanceDirectory(root: File, directory: File): Boolean =
        !Files.isSymbolicLink(directory.toPath()) &&
            directory.isDirectory &&
            directory.name.matches(Regex("[a-z0-9][a-z0-9-]{0,79}")) &&
            runCatching { directory.canonicalFile.parentFile == root.canonicalFile }.getOrDefault(false)

    private fun safeGameRoot(base: File, directory: File, shared: Boolean): File? {
        if (shared) return base.canonicalFile
        val game = File(directory, "game")
        if (Files.isSymbolicLink(game.toPath())) return null
        val canonical = runCatching { game.canonicalFile }.getOrNull() ?: return null
        return canonical.takeIf { it.parentFile == directory.canonicalFile }
    }

    fun list(context: Context): List<OEngineInstance> {
        val base = File(context.filesDir, "minecraft")
        val result = mutableListOf(OEngineInstance(DEFAULT_ID, "Default", base))
        val instancesRoot = instancesRootFor(base) ?: return result
        instancesRoot.listFiles().orEmpty().forEach { directory ->
            if (!isSafeInstanceDirectory(instancesRoot, directory)) return@forEach
            val metadata = File(directory, "instance.properties")
            if (Files.isSymbolicLink(metadata.toPath())) return@forEach
            val properties = Properties()
            if (metadata.isFile) {
                runCatching { metadata.inputStream().use { properties.load(it) } }
                    .onFailure { return@forEach }
            }
            val name = properties.getProperty("name")?.take(48)?.takeIf { it.isNotBlank() } ?: directory.name
            val shared = properties.getProperty("sharedGameFolder")?.toBooleanStrictOrNull() == true
            val gameRoot = safeGameRoot(base, directory, shared) ?: return@forEach
            result += OEngineInstance(directory.name, name, gameRoot)
        }
        return result.distinctBy { it.id }.sortedBy { if (it.id == DEFAULT_ID) 0 else 1 }
    }

    fun activeId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE_KEY, DEFAULT_ID) ?: DEFAULT_ID

    fun active(context: Context): OEngineInstance =
        list(context).firstOrNull { it.id == activeId(context) } ?: list(context).first()

    fun select(context: Context, id: String): OEngineInstance {
        val instance = list(context).firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown or unsafe O Launcher instance.")
        val base = File(context.filesDir, "minecraft")
        val instancesRoot = instancesRootFor(base)
        if (id != DEFAULT_ID) {
            require(instancesRoot != null &&
                isSafeInstanceDirectory(instancesRoot, File(instancesRoot, id))) {
                "The instance directory is unsafe."
            }
        }
        val root = instance.root
        require(!Files.isSymbolicLink(root.toPath())) { "The game directory cannot be a symbolic link." }
        if (!root.exists() && !root.mkdirs()) {
            throw IllegalStateException("Could not create instance game directory.")
        }
        if (id != DEFAULT_ID) {
            val directory = File(instancesRoot!!, id)
            require(safeGameRoot(base, directory, root.canonicalFile == base.canonicalFile) == root.canonicalFile) {
                "The instance game directory resolves outside its allowed location."
            }
        }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(ACTIVE_KEY, id).commit()
        check(saved) { "Could not save the active instance selection." }
        return instance
    }

    fun rename(context: Context, id: String, displayName: String): OEngineInstance {
        require(id != DEFAULT_ID) { "The Default instance cannot be renamed." }
        val clean = sanitizeDisplayName(displayName)
        require(clean.isNotBlank()) { "Enter a valid instance name." }
        val current = list(context).firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown or unsafe O Launcher instance.")
        val base = File(context.filesDir, "minecraft")
        val parent = instancesRootFor(base) ?: throw IllegalStateException("The instances directory is unsafe.")
        val directory = File(parent, id)
        require(isSafeInstanceDirectory(parent, directory)) { "The instance directory is invalid." }
        val metadata = File(directory, "instance.properties")
        val temporary = File(directory, "instance.properties.tmp")
        require(!Files.isSymbolicLink(metadata.toPath()) && !Files.isSymbolicLink(temporary.toPath())) {
            "The instance metadata path is unsafe."
        }
        val shared = current.root.canonicalFile == base.canonicalFile
        temporary.writeText("name=$clean\nsharedGameFolder=$shared\n")
        try {
            try {
                Files.move(temporary.toPath(), metadata.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), metadata.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("Could not save the new instance name.", error)
        }
        return list(context).first { it.id == id }
    }

    fun delete(context: Context, id: String) {
        require(id != DEFAULT_ID) { "The Default instance cannot be deleted." }
        val base = File(context.filesDir, "minecraft")
        val parent = instancesRootFor(base) ?: throw IllegalStateException("The instances directory is unsafe.")
        val directory = File(parent, id)
        require(isSafeInstanceDirectory(parent, directory)) { "The instance directory is invalid." }
        require(directory.deleteRecursively()) {
            "Could not completely delete the instance directory."
        }
        if (activeId(context) == id) {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ACTIVE_KEY, DEFAULT_ID).commit()
            check(saved) { "The instance was deleted, but the active selection could not be reset." }
        }
    }

    fun create(context: Context, displayName: String, sharedGameFolder: Boolean = false): OEngineInstance {
        val clean = sanitizeDisplayName(displayName)
        require(clean.isNotBlank()) { "Enter a valid instance name." }
        val id = instanceIdFor(clean)
        val base = File(context.filesDir, "minecraft")
        val instancesRoot = instancesRootFor(base, create = true)
            ?: throw IllegalStateException("Could not create or validate the instances directory.")
        val directory = File(instancesRoot, id)
        require(directory.canonicalFile.parentFile == instancesRoot && !directory.exists()) {
            "An instance with this name already exists or its path is invalid."
        }
        require(directory.mkdir()) { "Could not create the instance directory." }
        try {
            val game = gameRootFor(base, directory, sharedGameFolder)
            if (sharedGameFolder) {
                if (!base.exists() && !base.mkdirs()) throw IllegalStateException("Could not create the shared game directory.")
            } else if (!game.exists() && !game.mkdir()) {
                throw IllegalStateException("Could not create the instance game directory.")
            }
            require(!Files.isSymbolicLink(game.toPath()) &&
                (sharedGameFolder || game.canonicalFile.parentFile == directory.canonicalFile)) {
                "The new instance game directory is unsafe."
            }
            val metadata = File(directory, "instance.properties")
            val temporary = File(directory, "instance.properties.tmp")
            temporary.writeText("name=$clean\nsharedGameFolder=$sharedGameFolder\n")
            try {
                Files.move(temporary.toPath(), metadata.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), metadata.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return OEngineInstance(id, clean, game)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }
}
