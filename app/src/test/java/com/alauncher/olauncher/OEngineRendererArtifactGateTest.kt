package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineRendererArtifactGateTest {
    @Test
    fun acceptsMatchingElfInsidePrivateRendererRoot() {
        val root = Files.createTempDirectory("oengine-renderer").toFile()
        try {
            val library = File(root, "arm64-v8a/libltw.so")
            library.parentFile?.mkdirs()
            writeElf(library, elfClass = 2, machine = 183)

            val result = OEngineRendererArtifactGate.inspect(
                artifact(library, abi = "arm64-v8a"),
                rendererRoot = root,
                supportedAbis = listOf("arm64-v8a")
            )

            assertTrue(result.readyForAdapter)
            assertTrue(result.blockers.isEmpty())
            assertTrue(result.libraryPath == library.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsLibraryOutsidePrivateRendererRoot() {
        val root = Files.createTempDirectory("oengine-renderer-root").toFile()
        val outside = Files.createTempDirectory("oengine-renderer-outside").toFile()
        try {
            val library = File(outside, "libltw.so")
            writeElf(library, elfClass = 2, machine = 183)

            val result = OEngineRendererArtifactGate.inspect(
                artifact(library, abi = "arm64-v8a"),
                rendererRoot = root,
                supportedAbis = listOf("arm64-v8a")
            )

            assertFalse(result.readyForAdapter)
            assertTrue(result.blockers.any { it.contains("private renderer directory") })
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun rejectsElfMachineThatDoesNotMatchDeclaredAbi() {
        val root = Files.createTempDirectory("oengine-renderer-abi").toFile()
        try {
            val library = File(root, "libltw.so")
            writeElf(library, elfClass = 2, machine = 62) // x86_64, not AArch64

            val result = OEngineRendererArtifactGate.inspect(
                artifact(library, abi = "arm64-v8a"),
                rendererRoot = root,
                supportedAbis = listOf("arm64-v8a")
            )

            assertFalse(result.readyForAdapter)
            assertTrue(result.blockers.any { it.contains("does not match declared ABI") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonElfPayload() {
        val root = Files.createTempDirectory("oengine-renderer-invalid").toFile()
        try {
            val library = File(root, "libltw.so")
            library.writeBytes(ByteArray(128) { 0x41 })

            val result = OEngineRendererArtifactGate.inspect(
                artifact(library, abi = "arm64-v8a"),
                rendererRoot = root,
                supportedAbis = listOf("arm64-v8a")
            )

            assertFalse(result.readyForAdapter)
            assertTrue(result.blockers.any { it.contains("ELF shared-library") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun detectsAbiFromElfPayloadInsteadOfSupportedAbiOrder() {
        val root = Files.createTempDirectory("oengine-renderer-detect-abi").toFile()
        try {
            val library = File(root, "libGL4ES.so")
            writeElf(library, elfClass = 2, machine = 183)

            assertTrue(
                OEngineRendererArtifactGate.detectAbi(
                    library,
                    supportedAbis = listOf("x86_64", "arm64-v8a")
                ) == "arm64-v8a"
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsUnknownElfWhenDetectingAbi() {
        val root = Files.createTempDirectory("oengine-renderer-detect-unknown").toFile()
        try {
            val library = File(root, "libGL4ES.so")
            writeElf(library, elfClass = 2, machine = 999)

            assertTrue(
                OEngineRendererArtifactGate.detectAbi(
                    library,
                    supportedAbis = listOf("arm64-v8a", "x86_64")
                ) == null
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun artifact(file: File, abi: String) = OEngineNativeRendererArtifact(
        provider = OEngineGraphicsStack.RendererProvider.LTW,
        version = "test",
        abi = abi,
        libraryFile = file,
        license = OEngineGraphicsStack.LTW_LICENSE,
        upstreamRevision = OEngineGraphicsStack.LTW_TARGET_COMMIT
    )

    private fun writeElf(file: File, elfClass: Int, machine: Int) {
        val header = ByteArray(128)
        header[0] = 0x7f
        header[1] = 0x45
        header[2] = 0x4c
        header[3] = 0x46
        header[4] = elfClass.toByte()
        header[5] = 1 // little endian
        header[18] = (machine and 0xff).toByte()
        header[19] = ((machine ushr 8) and 0xff).toByte()
        file.writeBytes(header)
    }
}
