package com.alauncher.olauncher

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Installs the LTW native object packaged from the pinned upstream AAR into O Engine's
 * private renderer directory. The packaged APK copy is only the source; dlopen always
 * uses the verified private copy.
 */
object OEngineLtwArtifactInstaller {
    fun install(context: Context): OEngineNativeRendererArtifact {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()
            ?: throw IllegalStateException("Android did not report a supported native ABI.")
        val packaged = File(context.applicationInfo.nativeLibraryDir, "libltw.so")
        require(packaged.isFile && packaged.length() >= 64L) {
            "The APK does not contain libltw.so for $abi. Rebuild with the pinned LTW AAR."
        }

        val root = File(context.filesDir, "oengine/renderers").canonicalFile
        val targetDirectory = File(root, "ltw/$abi").apply {
            if (!exists() && !mkdirs()) throw IllegalStateException("Could not create the private LTW directory.")
        }.canonicalFile
        require(targetDirectory.toPath().startsWith(root.toPath())) {
            "LTW install path escaped O Engine's private renderer directory."
        }
        val target = File(targetDirectory, "libltw.so")
        fun sha256(file: File): ByteArray = MessageDigest.getInstance("SHA-256").digest(file.inputStream().use { it.readBytes() })

        if (!target.isFile || target.length() != packaged.length() || !sha256(target).contentEquals(sha256(packaged))) {
            val temporary = File(targetDirectory, "libltw.so.part")
            packaged.inputStream().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            require(temporary.length() == packaged.length()) { "Copied LTW library size does not match the APK payload." }
            if (target.exists() && !target.delete()) {
                temporary.delete()
                throw IllegalStateException("Could not replace the previous LTW library.")
            }
            if (!temporary.renameTo(target)) {
                temporary.delete()
                throw IllegalStateException("Could not atomically install the LTW library.")
            }
        }

        val artifact = OEngineNativeRendererArtifact(
            provider = OEngineGraphicsStack.RendererProvider.LTW,
            version = "pinned-${OEngineGraphicsStack.LTW_TARGET_COMMIT.take(12)}",
            abi = abi,
            libraryFile = target,
            license = OEngineGraphicsStack.LTW_LICENSE,
            upstreamRevision = OEngineGraphicsStack.LTW_TARGET_COMMIT
        )
        val inspection = OEngineRendererArtifactGate.inspect(artifact, root, listOf(abi))
        require(inspection.readyForAdapter) {
            "LTW artifact validation failed: " + inspection.blockers.joinToString(" • ")
        }
        return artifact
    }
}
