package com.alauncher.olauncher

import android.content.Context
import java.io.File
import java.nio.file.Files

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

    internal fun isManagedInstanceDirectory(instancesRoot: File, candidate: File): Boolean {
        val root = runCatching { instancesRoot.canonicalFile }.getOrNull() ?: return false
        if (Files.isSymbolicLink(candidate.toPath())) return false
        val directory = runCatching { candidate.canonicalFile }.getOrNull() ?: return false
        return directory.parentFile == root && directory.isDirectory
    }

    fun list(context: Context): List<OEngineInstance> {
        val base = File(context.filesDir, "minecraft")
        val result = mutableListOf(OEngineInstance(DEFAULT_ID, "Default", base))
        val instancesRoot = File(base, "instances")
        instancesRoot.listFiles()?.filter { isManagedInstanceDirectory(instancesRoot, it) }?.forEach { directory ->
            val metadata = File(directory, "instance.properties")
            val lines = if (metadata.isFile) runCatching { metadata.readLines() }.getOrDefault(emptyList()) else emptyList()
            val name = lines.firstOrNull { it.startsWith("name=") }?.substringAfter("name=")?.take(48)
            val sharesGameFolder = lines.firstOrNull { it.startsWith("sharedGameFolder=") }
                ?.substringAfter("=")?.toBooleanStrictOrNull() == true
            val gameRoot = gameRootFor(base, directory, sharesGameFolder)
            result.add(OEngineInstance(directory.name, name?.ifBlank { null } ?: directory.name, gameRoot))
        }
        return result.distinctBy { it.id }.sortedBy { if (it.id == DEFAULT_ID) 0 else 1 }
    }

    fun activeId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ACTIVE_KEY, DEFAULT_ID) ?: DEFAULT_ID

    fun active(context: Context): OEngineInstance =
        list(context).firstOrNull { it.id == activeId(context) } ?: list(context).first()

    fun select(context: Context, id: String): OEngineInstance {
        val instance = list(context).firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown O Launcher instance.")
        if (!instance.root.exists() && !instance.root.mkdirs()) {
            throw IllegalStateException("Could not create instance game directory.")
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ACTIVE_KEY, id).apply()
        return instance
    }


    /** Rename a managed instance without touching its game files or metadata-owned contents. */
    fun rename(context: Context, id: String, displayName: String): OEngineInstance {
        require(id != DEFAULT_ID) { "The Default instance cannot be renamed." }
        val clean = sanitizeDisplayName(displayName)
        require(clean.isNotBlank()) { "Enter a valid instance name." }
        require(list(context).any { it.id == id }) { "Unknown O Launcher instance." }
        val instancesRoot = File(File(context.filesDir, "minecraft"), "instances").canonicalFile
        val source = File(instancesRoot, id).canonicalFile
        require(isManagedInstanceDirectory(instancesRoot, source)) { "Instance directory is invalid." }
        val newId = instanceIdFor(clean)
        val destination = File(instancesRoot, newId)
        require(newId == id || !destination.exists()) { "An instance with this name already exists." }
        val metadata = File(source, "instance.properties")
        val existing = if (metadata.isFile) metadata.readLines().filterNot { it.startsWith("name=") } else emptyList()
        val temp = File(source, "instance.properties.tmp")
        temp.writeText((listOf("name=$clean") + existing).joinToString("\n", postfix="\n"))
        if (!temp.renameTo(metadata)) {
            temp.delete()
            throw IllegalStateException("Could not update instance metadata.")
        }
        val renamed = if (newId == id) source else {
            if (!source.renameTo(destination)) throw IllegalStateException("Could not rename instance directory.")
            destination
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (activeId(context) == id) prefs.edit().putString(ACTIVE_KEY, newId).apply()
        val updated = list(context).firstOrNull { it.id == newId }
            ?: throw IllegalStateException("Renamed instance could not be reloaded.")
        return updated
    }

    /** Delete only a managed instance directory. Default is deliberately protected. */
    fun delete(context: Context, id: String) {
        require(id != DEFAULT_ID) { "The Default instance cannot be deleted." }
        val instancesRoot = File(File(context.filesDir, "minecraft"), "instances").canonicalFile
        val target = File(instancesRoot, id).canonicalFile
        require(isManagedInstanceDirectory(instancesRoot, target)) { "Instance directory is invalid." }
        val known = list(context).any { it.id == id }
        require(known) { "Unknown O Launcher instance." }
        if (!target.deleteRecursively()) throw IllegalStateException("Could not completely delete the instance.")
        if (activeId(context) == id) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ACTIVE_KEY, DEFAULT_ID).apply()
        }
    }

    fun create(context: Context, displayName: String, sharedGameFolder: Boolean = false): OEngineInstance {
        val clean = sanitizeDisplayName(displayName)
        require(clean.isNotBlank()) { "Enter a valid instance name." }
        val id = instanceIdFor(clean)
        val parent = File(File(context.filesDir, "minecraft/instances"), id)
        require(!parent.exists()) { "An instance with this name already exists." }
        if (!parent.mkdirs()) throw IllegalStateException("Could not create the instance directory.")
        try {
            val game = if (sharedGameFolder) File(context.filesDir, "minecraft") else File(parent, "game")
            if (!game.exists() && !game.mkdirs()) throw IllegalStateException("Could not create the instance game directory.")
            File(parent, "instance.properties").writeText("name=$clean\nsharedGameFolder=$sharedGameFolder\n")
            return OEngineInstance(id, clean, game)
        } catch (failure: Exception) {
            parent.deleteRecursively()
            throw failure
        }
    }
}
