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
        val nativeRoot = File(context.applicationInfo.nativeLibraryDir)
        val packagedAndAbi = Build.SUPPORTED_ABIS
            .mapNotNull { abi ->
                File(nativeRoot, OEngineGraphicsStack.GL4ES_LIBRARY)
                    .takeIf { it.isFile && it.length() >= 100_000L }
                    ?.let { abi to it }
            }
            .firstOrNull()
            ?: throw IllegalStateException(
                "The APK does not contain the pinned GL4ES library for any reported device ABI."
            )
        val (abi, packaged) = packagedAndAbi

        val root = File(context.filesDir, "oengine/renderers").canonicalFile
        val directory = File(root, "gl4es/$abi").apply {
            if (!exists() && !mkdirs()) {
                throw IllegalStateException("Could not create the private GL4ES directory.")
            }
        }.canonicalFile
        require(directory.toPath().startsWith(root.toPath())) {
            "GL4ES install path escaped O Engine's private renderer directory."
        }

        val target = File(directory, OEngineGraphicsStack.GL4ES_LIBRARY)
        fun digest(file: File): ByteArray {
            val sha256 = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    sha256.update(buffer, 0, count)
                }
            }
            return sha256.digest()
        }

        if (!target.isFile || target.length() != packaged.length() ||
            !digest(target).contentEquals(digest(packaged))
        ) {
            val temporary = File(directory, target.name + ".part")
            try {
                packaged.inputStream().buffered().use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                require(temporary.length() == packaged.length() &&
                    digest(temporary).contentEquals(digest(packaged))) {
                    "Copied GL4ES library does not match the APK payload."
                }
                if (target.exists() && !target.delete()) {
                    throw IllegalStateException("Could not replace the previous GL4ES library.")
                }
                if (!temporary.renameTo(target)) {
                    throw IllegalStateException("Could not atomically install the GL4ES library.")
                }
            } finally {
                if (temporary.exists()) temporary.delete()
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
