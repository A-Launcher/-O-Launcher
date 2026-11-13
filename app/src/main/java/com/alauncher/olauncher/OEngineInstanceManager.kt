package com.alauncher.olauncher

import android.content.Context
import java.io.File

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

    fun list(context: Context): List<OEngineInstance> {
        val base = File(context.filesDir, "minecraft")
        val result = mutableListOf(OEngineInstance(DEFAULT_ID, "Default", base))
        File(base, "instances").listFiles()?.filter { it.isDirectory }?.forEach { directory ->
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

    fun rename(context: Context, id: String, displayName: String): OEngineInstance {
        require(id != DEFAULT_ID) { "The Default instance cannot be renamed." }
        val clean = sanitizeDisplayName(displayName)
        require(clean.isNotBlank()) { "Enter a valid instance name." }
        val current = list(context).firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown O Launcher instance.")
        val parent = File(context.filesDir, "minecraft/instances").canonicalFile
        val directory = File(parent, id)
        require(directory.isDirectory && directory.canonicalFile.parentFile == parent) {
            "The instance directory is invalid."
        }
        val metadata = File(directory, "instance.properties")
        val sharesGameFolder = current.root.canonicalFile ==
            File(context.filesDir, "minecraft").canonicalFile
        metadata.writeText("name=$clean\nsharedGameFolder=$sharesGameFolder\n")
        // Keep the stable directory ID: profiles, installer handoffs and saved settings
        // are keyed by it and must survive a display-name change.
        return list(context).first { it.id == id }
    }

    fun delete(context: Context, id: String) {
        require(id != DEFAULT_ID) { "The Default instance cannot be deleted." }
        val directory = File(File(context.filesDir, "minecraft/instances"), id)
        require(directory.isDirectory && directory.canonicalFile.parentFile ==
            File(context.filesDir, "minecraft/instances").canonicalFile) {
            "The instance directory is invalid."
        }
        if (activeId(context) == id) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ACTIVE_KEY, DEFAULT_ID).commit()
        }
        if (!directory.deleteRecursively()) {
            throw IllegalStateException("Could not completely delete the instance directory.")
        }
    }

    fun create(context: Context, displayName: String, sharedGameFolder: Boolean = false): OEngineInstance {
        val clean = sanitizeDisplayName(displayName)
        require(clean.isNotBlank()) { "Enter a valid instance name." }
        val id = instanceIdFor(clean)
        val parent = File(File(context.filesDir, "minecraft/instances"), id)
        require(!parent.exists()) { "An instance with this name already exists." }
        if (!parent.mkdirs()) throw IllegalStateException("Could not create the instance directory.")
        val game = if (sharedGameFolder) File(context.filesDir, "minecraft") else File(parent, "game")
        if (!game.exists() && !game.mkdirs()) throw IllegalStateException("Could not create the instance game directory.")
        File(parent, "instance.properties").writeText("name=$clean\nsharedGameFolder=$sharedGameFolder\n")
        return OEngineInstance(id, clean, game)
    }
}
