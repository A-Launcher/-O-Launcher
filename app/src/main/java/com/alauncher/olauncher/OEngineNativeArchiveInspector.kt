package com.alauncher.olauncher

import java.io.File
import java.util.zip.ZipInputStream

/**
 * Safely extracts Mojang Linux shared objects into a diagnostic-only directory.
 * Matching ELF architecture does not make a glibc-linked Linux library Android/Bionic compatible.
 */
internal object OEngineNativeArchiveInspector {
    private const val MAX_SHARED_OBJECT_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_EXTRACTED_BYTES = 256L * 1024L * 1024L
    private const val MAX_SHARED_OBJECTS = 256
    fun extractLinuxNativeArchive(archive: File, destinationRoot: File): Int {
        val root = destinationRoot.canonicalFile
        if (!root.exists() && !root.mkdirs()) {
            throw IllegalStateException("Could not create Linux native inspection directory.")
        }
        var extracted = 0
        var totalBytes = 0L
        val seenTargets = mutableSetOf<String>()
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val normalized = entry.name.replace('\\', '/')
                if (normalized.startsWith("/") || normalized.split('/').any { it == ".." }) {
                    throw IllegalStateException("Unsafe path in native archive: " + entry.name)
                }
                val name = normalized.substringAfterLast('/')
                val isSharedObject = name.endsWith(".so") || Regex("""\.so\.[0-9.]+$""").containsMatchIn(name)
                if (!isSharedObject) {
                    zip.closeEntry()
                    continue
                }
                val target = File(root, normalized).canonicalFile
                if (!target.toPath().startsWith(root.toPath())) {
                    throw IllegalStateException("Native archive entry escaped its inspection directory.")
                }
                val relativeTarget = target.relativeTo(root).invariantSeparatorsPath
                if (!seenTargets.add(relativeTarget)) throw IllegalStateException("Duplicate shared object path in native archive: " + entry.name)
                if (extracted >= MAX_SHARED_OBJECTS) throw IllegalStateException("Native archive contains too many shared objects.")
                val parent = target.parentFile ?: throw IllegalStateException("Invalid native archive entry.")
                if (!parent.exists() && !parent.mkdirs()) {
                    throw IllegalStateException("Could not create native archive subdirectory.")
                }
                val temporary = File(parent, target.name + ".part")
                if (temporary.exists()) temporary.delete()
                var entryBytes = 0L
                try {
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            totalBytes += count
                            if (entryBytes > MAX_SHARED_OBJECT_BYTES || totalBytes > MAX_TOTAL_EXTRACTED_BYTES) {
                                throw IllegalStateException("Native archive exceeds extraction size limits.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                } catch (error: Exception) {
                    temporary.delete()
                    throw error
                }
                if (temporary.length() == 0L) {
                    temporary.delete()
                    throw IllegalStateException("Empty shared object in native archive: " + entry.name)
                }
                if (target.exists() && !target.delete()) {
                    temporary.delete()
                    throw IllegalStateException("Could not replace inspected native file: " + entry.name)
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                extracted++
                zip.closeEntry()
            }
        }
        return extracted
    }
}
