package com.alauncher.olauncher

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile

/**
 * Installs the pinned Mojo Caciocavallo runtime jars into the selected instance.
 * The Android CI packaging step places the upstream jars under assets/components.
 */
object OEngineMojoAwtRuntimeProvisioner {
    private const val MAX_JAR_BYTES = 128L * 1024L * 1024L

    data class Result(val javaMajor: Int, val directory: File, val jars: List<File>)

    fun prepare(context: Context, paths: OEngineMojoAwtRuntimeLayout.Paths, javaMajor: Int): Result {
        require(javaMajor >= 8) { "Unsupported Java runtime for Mojo AWT." }
        val sourceDirectory = if (javaMajor == 8) {
            "components/caciocavallo"
        } else {
            "components/caciocavallo17"
        }
        val destination = if (javaMajor == 8) paths.cacioJava8 else paths.cacioJava17
        require(destination.isDirectory || destination.mkdirs()) {
            "Could not create the Mojo Caciocavallo directory."
        }

        val expected = if (javaMajor == 8) {
            listOf("cacio-shared.jar", "cacio-androidnw.jar")
        } else {
            listOf("cacio-tta.jar")
        }
        val installed = expected.map { name ->
            val target = File(destination, name)
            val temporary = File(destination, "$name.installing")
            context.assets.open("$sourceDirectory/$name").use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_JAR_BYTES) { "Mojo Caciocavallo component is too large." }
                        output.write(buffer, 0, count)
                    }
                    require(total > 0L) { "Mojo Caciocavallo component is empty." }
                }
            }
            try {
                JarFile(temporary).use { jar ->
                    require(jar.entries().hasMoreElements()) { "Mojo Caciocavallo component is invalid." }
                }
                if (target.exists() && !target.delete()) {
                    error("Could not replace the previous Caciocavallo component.")
                }
                check(temporary.renameTo(target)) { "Could not install Caciocavallo component." }
            } catch (failure: Exception) {
                temporary.delete()
                throw failure
            }
            target
        }
        OEngineMojoAwtRuntimeLayout.requireCacio(paths, javaMajor)
        return Result(javaMajor, destination, installed)
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
