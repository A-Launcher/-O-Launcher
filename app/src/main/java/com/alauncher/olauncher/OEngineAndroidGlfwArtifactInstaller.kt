package com.alauncher.olauncher

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class OEngineAndroidGlfwInstallResult(
    val abi: String,
    val directory: File,
    val libraries: List<File>,
    val sha256ByLibrary: Map<String, String>,
    val manifest: File
)

/**
 * Stages the separately-built Android GLFW platform backend in private game storage.
 *
 * This is the C-ABI GLFW library consumed by LWJGL 3's generated function table.
 * Mojo's ART-side Android JNI binding is a separate Java API and is adapted by O Engine.
 */
object OEngineAndroidGlfwArtifactInstaller {
    private val supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    fun install(sourceDirectory: File, destinationRoot: File, abi: String): OEngineAndroidGlfwInstallResult {
        require(abi in supportedAbis) { "Unsupported Android GLFW ABI: $abi" }
        val source = sourceDirectory.canonicalFile
        require(source.isDirectory) { "Android app native-library directory is missing." }
        val root = destinationRoot.canonicalFile
        val destination = File(root, abi).canonicalFile
        require(destination.toPath().startsWith(root.toPath())) { "Android GLFW destination escapes its private root." }

        val names = buildList {
            add("libglfw.so")
            add("libmojoexec.so")
            add("liblinkerhook.so")
            if (abi == "arm64-v8a") add("libfopenhook.so")
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw IllegalStateException("Could not create the private Android GLFW directory.")
        }

        val installed = mutableListOf<File>()
        val hashes = linkedMapOf<String, String>()
        for (name in names) {
            val input = File(source, name).canonicalFile
            require(input.toPath().startsWith(source.toPath()) && input.isFile && input.length() > 0L) {
                "Packaged Android GLFW dependency is missing or empty: $name"
            }
            val inspection = OEngineElfCompatibilityInspector.inspect(input, abi)
            require(inspection.compatibility == OEngineElfCompatibility.ANDROID_COMPATIBILITY_UNVERIFIED) {
                "Android GLFW dependency $name failed ELF/ABI checks: ${inspection.explanation}"
            }
            val digest = sha256(input)
            val output = File(destination, name)
            if (!output.isFile || output.length() != input.length() || sha256(output) != digest) {
                val temporary = File(destination, "$name.part")
                if (temporary.exists()) temporary.delete()
                input.copyTo(temporary, overwrite = true)
                require(sha256(temporary) == digest) { "Copied Android GLFW dependency failed SHA-256 verification: $name" }
                if (output.exists() && !output.delete()) {
                    temporary.delete()
                    throw IllegalStateException("Could not replace staged Android GLFW dependency: $name")
                }
                if (!temporary.renameTo(output)) {
                    temporary.copyTo(output, overwrite = true)
                    temporary.delete()
                }
            }
            installed += output
            hashes[name] = digest
        }

        val manifest = File(destination, "oengine-android-glfw-manifest.json")
        val json = JSONObject().apply {
            put("schemaVersion", 1)
            put("abi", abi)
            put("backend", "MojoLauncher Android GLFW platform port")
            put("notice", "Platform backend staged only; LWJGL org.lwjgl.glfw JNI/runtime adapter is not yet connected.")
            put("libraries", JSONArray().apply {
                installed.forEach { file ->
                    put(JSONObject().apply {
                        put("name", file.name)
                        put("size", file.length())
                        put("sha256", hashes.getValue(file.name))
                        put("elfCompatibility", "ANDROID_COMPATIBILITY_UNVERIFIED")
                    })
                }
            })
        }
        val temporaryManifest = File(destination, manifest.name + ".part")
        temporaryManifest.writeText(json.toString(2))
        if (manifest.exists() && !manifest.delete()) {
            temporaryManifest.delete()
            throw IllegalStateException("Could not replace Android GLFW manifest.")
        }
        if (!temporaryManifest.renameTo(manifest)) {
            temporaryManifest.copyTo(manifest, overwrite = true)
            temporaryManifest.delete()
        }
        return OEngineAndroidGlfwInstallResult(abi, destination, installed, hashes, manifest)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
