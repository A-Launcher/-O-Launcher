package com.alauncher.olauncher

import java.io.File

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
        val modifiedAt: Long
    )

    fun list(gameRoot: File): List<Entry> {
        val root = gameRoot.canonicalFile
        return Kind.values().flatMap { kind ->
            val directory = File(root, kind.directory).canonicalFile
            if (!directory.toPath().startsWith(root.toPath()) || !directory.isDirectory) {
                emptyList()
            } else {
                directory.listFiles()
                    .orEmpty()
                    .filter { file ->
                        file.isFile &&
                            !java.nio.file.Files.isSymbolicLink(file.toPath()) &&
                            file.canonicalFile.parentFile == directory &&
                            !file.name.endsWith(".oengine-part") &&
                            file.extension.lowercase() in kind.extensions
                    }
                    .map { file ->
                        Entry(file.name, kind, file, file.length(), file.lastModified())
                    }
            }
        }.sortedWith(compareBy<Entry> { it.kind.ordinal }.thenBy { it.name.lowercase() })
    }

    fun remove(gameRoot: File, entry: Entry): Boolean {
        val root = gameRoot.canonicalFile
        val expectedDirectory = File(root, entry.kind.directory).canonicalFile
        val target = entry.file.canonicalFile
        require(expectedDirectory.toPath().startsWith(root.toPath()))
        require(target.parentFile == expectedDirectory && target.isFile) {
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
