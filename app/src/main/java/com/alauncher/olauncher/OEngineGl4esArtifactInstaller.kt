package com.alauncher.olauncher

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Stages the pinned Android GL4ES shared object from the APK into O Engine's
 * private renderer directory. ELF validation is necessary but not sufficient:
 * this does not claim EGL initialization or Minecraft rendering is verified.
 */
object OEngineGl4esArtifactInstaller {
    fun install(context: Context): OEngineNativeRendererArtifact {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
            ?: throw IllegalStateException("Android did not report a supported native ABI.")
        val packaged = File(context.applicationInfo.nativeLibraryDir, OEngineGraphicsStack.GL4ES_LIBRARY)
        require(packaged.isFile && packaged.length() >= 100_000L) {
            "The APK does not contain the pinned GL4ES library for $abi."
        }

        val root = File(context.filesDir, "oengine/renderers").canonicalFile
        val directory = File(root, "gl4es/$abi").apply {
            if (!exists() && !mkdirs()) throw IllegalStateException("Could not create the private GL4ES directory.")
        }.canonicalFile
        require(directory.toPath().startsWith(root.toPath())) {
            "GL4ES install path escaped O Engine's private renderer directory."
        }

        val target = File(directory, OEngineGraphicsStack.GL4ES_LIBRARY)
        fun digest(file: File) = MessageDigest.getInstance("SHA-256")
            .digest(file.inputStream().use { it.readBytes() })

        if (!target.isFile || target.length() != packaged.length() ||
            !digest(target).contentEquals(digest(packaged))
        ) {
            val temporary = File(directory, target.name + ".part")
            packaged.inputStream().buffered().use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            require(temporary.length() == packaged.length()) {
                "Copied GL4ES library size does not match the APK payload."
            }
            if (target.exists() && !target.delete()) {
                temporary.delete()
                throw IllegalStateException("Could not replace the previous GL4ES library.")
            }
            if (!temporary.renameTo(target)) {
                temporary.delete()
                throw IllegalStateException("Could not atomically install the GL4ES library.")
            }
        }

        val artifact = OEngineNativeRendererArtifact(
            provider = OEngineGraphicsStack.RendererProvider.GL4ES,
            version = "pinned-${OEngineGraphicsStack.GL4ES_UPSTREAM_COMMIT.take(12)}",
            abi = abi,
            libraryFile = target,
            license = OEngineGraphicsStack.GL4ES_LICENSE,
            upstreamRevision = OEngineGraphicsStack.GL4ES_UPSTREAM_COMMIT
        )
        val inspection = OEngineRendererArtifactGate.inspect(artifact, root, listOf(abi))
        require(inspection.readyForAdapter) {
            "GL4ES artifact validation failed: " + inspection.blockers.joinToString(" • ")
        }
        return artifact
    }
}
