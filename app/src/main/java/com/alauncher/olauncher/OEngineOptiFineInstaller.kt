package com.alauncher.olauncher

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.jar.JarFile

/** Imports an official OptiFine installer selected through Android's document picker. */
object OEngineOptiFineInstaller {
    private const val MAX_INSTALLER_BYTES = 128L * 1024L * 1024L

    fun stage(context: Context, source: Uri, gameDirectory: File): File {
        val root = gameDirectory.canonicalFile
        val cache = File(root, "oengine/installers/optifine").canonicalFile
        require(cache.toPath().startsWith(root.toPath())) { "Unsafe OptiFine installer cache path." }
        if (!cache.exists() && !cache.mkdirs()) error("Could not create the OptiFine installer cache.")
        val target = File(cache, "OptiFine-installer.jar")
        val temporary = File(cache, "OptiFine-installer.jar.part")
        temporary.delete()
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: error("Android could not open the selected OptiFine installer.")
            input.use { stream ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_INSTALLER_BYTES) { "OptiFine installer exceeds the 128 MB safety limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(isInstallerJar(temporary)) {
                "The selected file is not a valid OptiFine installer JAR. Download the installer from optifine.net."
            }
            require(cache.canonicalFile.toPath().startsWith(root.toPath())) {
                "OptiFine installer cache path changed during import."
            }
            if (target.exists() && !target.delete()) error("Could not replace the cached OptiFine installer.")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            return target
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }
    }

    private fun isInstallerJar(file: File): Boolean {
        if (!file.isFile || file.length() !in 1..MAX_INSTALLER_BYTES) return false
        return runCatching {
            JarFile(file).use { jar ->
                val mainClass = jar.manifest?.mainAttributes?.getValue("Main-Class")
                !mainClass.isNullOrBlank() &&
                    jar.getJarEntry(mainClass.replace('.', '/') + ".class") != null
            }
        }.getOrDefault(false)
    }
}
