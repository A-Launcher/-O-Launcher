package com.alauncher.olauncher

import android.os.Build
import java.io.File
import java.io.RandomAccessFile

/**
 * Verified native artifact metadata supplied by a renderer integration.
 *
 * This class deliberately does not download, extract, or load provider code. A
 * renderer adapter must first establish an upstream-supported loading contract,
 * then provide an artifact rooted inside O Engine's private renderer directory.
 */
data class OEngineNativeRendererArtifact(
    val provider: OEngineGraphicsStack.RendererProvider,
    val version: String,
    val abi: String,
    val libraryFile: File,
    val license: String,
    val upstreamRevision: String
)

data class OEngineRendererArtifactInspection(
    val provider: OEngineGraphicsStack.RendererProvider,
    val readyForAdapter: Boolean,
    val detectedAbi: String?,
    val libraryPath: String?,
    val blockers: List<String>
)

/**
 * Validates the provenance metadata, private-directory boundary, and ELF ABI
 * before a future provider adapter is allowed to consider loading a library.
 * It is not a native loader and never calls System.load/dlopen.
 */
object OEngineRendererArtifactGate {
    private data class ElfIdentity(val bits: Int, val machine: Int)

    fun inspect(
        artifact: OEngineNativeRendererArtifact,
        rendererRoot: File,
        supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()
    ): OEngineRendererArtifactInspection {
        val blockers = mutableListOf<String>()
        if (artifact.version.isBlank()) blockers += "Renderer artifact version is missing."
        if (artifact.license.isBlank()) blockers += "Renderer license metadata is missing."
        if (artifact.upstreamRevision.isBlank()) blockers += "Renderer upstream revision is missing."
        if (artifact.abi !in supportedAbis) {
            blockers += "Renderer ABI ${artifact.abi} is not supported by this device."
        }

        val root = runCatching { rendererRoot.canonicalFile }.getOrNull()
        val library = runCatching { artifact.libraryFile.canonicalFile }.getOrNull()
        if (root == null || library == null) {
            blockers += "Renderer paths could not be canonicalized."
        } else {
            val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
            if (!library.path.startsWith(rootPath)) {
                blockers += "Renderer library must be stored inside O Engine's private renderer directory."
            }
            if (!library.isFile || library.length() < 64L) {
                blockers += "Renderer native library is missing or too small."
            }
        }

        val elf = if (library?.isFile == true && library.length() >= 64L) {
            readElfIdentity(library)
        } else null
        if (elf == null) {
            blockers += "Renderer file is not a readable ELF shared-library image."
        } else {
            val expected = expectedElfIdentity(artifact.abi)
            if (expected == null) {
                blockers += "Renderer ABI is not recognized by O Engine's ELF validator."
            } else if (elf != expected) {
                blockers += "Renderer ELF machine/class does not match declared ABI ${artifact.abi}."
            }
        }

        return OEngineRendererArtifactInspection(
            provider = artifact.provider,
            readyForAdapter = blockers.isEmpty(),
            detectedAbi = elf?.let { artifact.abi },
            libraryPath = library?.path,
            blockers = blockers.distinct()
        )
    }

    private fun readElfIdentity(file: File): ElfIdentity? = runCatching {
        RandomAccessFile(file, "r").use { input ->
            if (input.readUnsignedByte() != 0x7f ||
                input.readUnsignedByte() != 0x45 ||
                input.readUnsignedByte() != 0x4c ||
                input.readUnsignedByte() != 0x46
            ) return null
            val elfClass = input.readUnsignedByte()
            val dataEncoding = input.readUnsignedByte()
            if (dataEncoding != 1) return null // Android supported ABIs use little-endian ELF.
            input.seek(18L)
            val machine = input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
            val bits = when (elfClass) {
                1 -> 32
                2 -> 64
                else -> return null
            }
            ElfIdentity(bits, machine)
        }
    }.getOrNull()

    private fun expectedElfIdentity(abi: String): ElfIdentity? = when (abi) {
        "armeabi-v7a" -> ElfIdentity(32, 40) // EM_ARM
        "arm64-v8a" -> ElfIdentity(64, 183) // EM_AARCH64
        "x86" -> ElfIdentity(32, 3) // EM_386
        "x86_64" -> ElfIdentity(64, 62) // EM_X86_64
        else -> null
    }
}
