package com.alauncher.olauncher

import android.content.Context
import java.io.File

data class OEngineInstance(val id: String, val name: String, val root: File)

/** Isolated per-instance directories; Default preserves the original game folder. */
object OEngineInstanceManager {
    private const val PREFS = "oengine_instances"
    private const val ACTIVE_KEY = "active_instance"
    private const val DEFAULT_ID = "default"

    fun list(context: Context): List<OEngineInstance> {
        val base = File(context.filesDir, "minecraft")
        val result = mutableListOf(OEngineInstance(DEFAULT_ID, "Default", base))
        File(base, "instances").listFiles()?.filter { it.isDirectory }?.forEach { directory ->
            val metadata = File(directory, "instance.properties")
            val name = if (metadata.isFile) metadata.readLines().firstOrNull { it.startsWith("name=") }
                ?.substringAfter("name=")?.take(48) else null
            result.add(OEngineInstance(directory.name, name?.ifBlank { null } ?: directory.name, File(directory, "game")))
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

    fun create(context: Context, displayName: String): OEngineInstance {
        val clean = displayName.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "")
            .replace(Regex("\\s+"), " ").take(40)
        require(clean.isNotBlank()) { "Enter a valid instance name." }
        val id = clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "instance" }
        val parent = File(File(context.filesDir, "minecraft/instances"), id)
        require(!parent.exists()) { "An instance with this name already exists." }
        val game = File(parent, "game")
        if (!game.mkdirs()) throw IllegalStateException("Could not create the new instance.")
        File(parent, "instance.properties").writeText("name=$clean\n")
        return OEngineInstance(id, clean, game)
    }
}
