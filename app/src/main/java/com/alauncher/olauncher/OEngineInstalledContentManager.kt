package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files

/** Safe inventory and removal for content installed into one isolated game instance. */
object OEngineInstalledContentManager {
    enum class Kind(val directory: String, val extensions: Set<String>) {
        MOD("mods", setOf("jar")),
        RESOURCE_PACK("resourcepacks", setOf("zip", "mcpack")),
        SHADER("shaderpacks", setOf("zip")),
        DATA_PACK("datapacks", setOf("zip"))
    }

    data class Entry(
        val name: String,
        val kind: Kind,
        val file: File,
        val sizeBytes: Long,
        val modifiedAt: Long,
        val worldName: String? = null
    )

    fun worldNames(gameRoot: File): List<String> {
        val root = gameRoot.canonicalFile
        val saves = File(root, "saves")
        if (Files.isSymbolicLink(saves.toPath()) || !saves.isDirectory || saves.canonicalFile.parentFile != root) return emptyList()
        return saves.listFiles().orEmpty().filter { world ->
            !Files.isSymbolicLink(world.toPath()) && world.isDirectory &&
                world.canonicalFile.parentFile == saves.canonicalFile && File(world, "level.dat").isFile
        }.map { it.name }.sortedBy { it.lowercase() }
    }

    fun list(gameRoot: File): List<Entry> {
        val root = gameRoot.canonicalFile
        val standardEntries = Kind.values()
            .filter { it != Kind.DATA_PACK }
            .flatMap { kind -> listDirectory(root, File(root, kind.directory), kind) }
        val saves = File(root, "saves")
        val worldEntries = if (
            !Files.isSymbolicLink(saves.toPath()) &&
            saves.isDirectory &&
            saves.canonicalFile.parentFile == root
        ) {
            saves.listFiles().orEmpty().filter { world ->
                !Files.isSymbolicLink(world.toPath()) &&
                    world.isDirectory &&
                    world.canonicalFile.parentFile == saves.canonicalFile &&
                    File(world, "level.dat").isFile
            }.flatMap { world ->
                listDirectory(
                    root,
                    File(world, Kind.DATA_PACK.directory),
                    Kind.DATA_PACK,
                    world.name
                )
            }
        } else emptyList()

        return (standardEntries + worldEntries)
            .sortedWith(compareBy<Entry> { it.kind.ordinal }.thenBy { it.worldName.orEmpty().lowercase() }.thenBy { it.name.lowercase() })
    }

    private fun listDirectory(
        root: File,
        candidate: File,
        kind: Kind,
        worldName: String? = null
    ): List<Entry> {
        val directory = runCatching { candidate.canonicalFile }.getOrNull() ?: return emptyList()
        if (
            Files.isSymbolicLink(candidate.toPath()) ||
            !directory.toPath().startsWith(root.toPath()) ||
            !directory.isDirectory
        ) return emptyList()

        return directory.listFiles().orEmpty()
            .filter { file ->
                file.isFile &&
                    !Files.isSymbolicLink(file.toPath()) &&
                    file.canonicalFile.parentFile == directory &&
                    !file.name.endsWith(".oengine-part") &&
                    file.extension.lowercase() in kind.extensions
            }
            .map { file ->
                Entry(file.name, kind, file, file.length(), file.lastModified(), worldName)
            }
    }

    fun remove(gameRoot: File, entry: Entry): Boolean {
        val root = gameRoot.canonicalFile
        val expectedDirectory = when (entry.kind) {
            Kind.DATA_PACK -> {
                val worldName = requireNotNull(entry.worldName) { "A world must be selected for this data pack." }
                require(worldName.isNotBlank() && worldName != "." && worldName != ".." &&
                    !worldName.contains("/") && !worldName.contains("\\"))
                val saves = File(root, "saves")
                val world = File(saves, worldName)
                val dataPacks = File(world, "datapacks")
                require(
                    !Files.isSymbolicLink(saves.toPath()) &&
                        saves.isDirectory &&
                        saves.canonicalFile.parentFile == root &&
                        !Files.isSymbolicLink(world.toPath()) &&
                        world.isDirectory &&
                        world.canonicalFile.parentFile == saves.canonicalFile &&
                        !Files.isSymbolicLink(dataPacks.toPath())
                ) { "The selected world folder is no longer safe." }
                dataPacks.canonicalFile
            }
            else -> File(root, entry.kind.directory).canonicalFile
        }
        val target = entry.file.canonicalFile
        require(expectedDirectory.toPath().startsWith(root.toPath()))
        require(
            !Files.isSymbolicLink(entry.file.toPath()) &&
                target.parentFile == expectedDirectory &&
                target.isFile
        ) {
            "The selected content file is no longer inside this instance."
        }
        return target.delete()
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
