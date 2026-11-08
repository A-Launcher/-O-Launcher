package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class OEngineInstallerProfileWatcherTest {
    private fun profile(root: File, id: String, parent: String, library: String) {
        val directory = File(root, "versions/$id")
        if (!directory.exists()) assert(directory.mkdirs())
        File(directory, "$id.json").writeText(
            """{"id":"$id","inheritsFrom":"$parent","libraries":[{"name":"$library"}]}"""
        )
        val coordinate = library.split(':')
        require(coordinate.size >= 3)
        val artifact = coordinate[1]
        val version = coordinate[2]
        val artifactFile = File(
            root,
            "libraries/${coordinate[0].replace('.', '/')}/$artifact/$version/" +
                "$artifact-$version" + (coordinate.getOrNull(3)?.let { "-$it" } ?: "") + ".jar"
        )
        artifactFile.parentFile?.mkdirs()
        artifactFile.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
    }

    @Test fun resolvesOnlyNewMatchingForgeProfile() {
        val root = createTempDir(prefix = "installer-profile-")
        try {
            profile(root, "old-forge", "1.20.1", "net.minecraftforge:forge:1.20.1-47.2.0")
            val before = OEngineInstallerProfileWatcher.snapshot(root)
            profile(root, "1.20.1-forge-47.3.0", "1.20.1", "net.minecraftforge:forge:1.20.1-47.3.0")
            val result = OEngineInstallerProfileWatcher.resolve(
                root, before, "1.20.1", OEngineInstallerProfileWatcher.Loader.FORGE
            )
            assertEquals("1.20.1-forge-47.3.0", result.versionId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun resolvesClassifiedLoaderArtifact() {
        val root = createTempDir(prefix = "installer-profile-")
        try {
            val before = OEngineInstallerProfileWatcher.snapshot(root)
            profile(root, "forge-classified", "1.20.1", "net.minecraftforge:forge:1.20.1-47.3.0:client")
            val result = OEngineInstallerProfileWatcher.resolve(
                root, before, "1.20.1", OEngineInstallerProfileWatcher.Loader.FORGE
            )
            assertEquals("forge-classified", result.versionId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsWrongParentAndWrongLoader() {
        val root = createTempDir(prefix = "installer-profile-")
        try {
            val before = OEngineInstallerProfileWatcher.snapshot(root)
            profile(root, "wrong-parent", "1.19.4", "net.minecraftforge:forge:1.20.1-47.3.0")
            profile(root, "wrong-loader", "1.20.1", "net.neoforged:neoforge:21.0.0")
            assertThrows(IllegalArgumentException::class.java) {
                OEngineInstallerProfileWatcher.resolve(
                    root, before, "1.20.1", OEngineInstallerProfileWatcher.Loader.FORGE
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsAmbiguousNewProfiles() {
        val root = createTempDir(prefix = "installer-profile-")
        try {
            val before = OEngineInstallerProfileWatcher.snapshot(root)
            profile(root, "forge-a", "1.20.1", "net.minecraftforge:forge:1.20.1-47.3.0")
            profile(root, "forge-b", "1.20.1", "net.minecraftforge:forge:1.20.1-47.3.1")
            assertThrows(IllegalArgumentException::class.java) {
                OEngineInstallerProfileWatcher.resolve(
                    root, before, "1.20.1", OEngineInstallerProfileWatcher.Loader.FORGE
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun resolvesUpdatedExistingProfileId() {
        val root = createTempDir(prefix = "installer-profile-")
        try {
            profile(root, "shared-forge-id", "1.20.1", "net.minecraftforge:forge:1.20.1-47.2.0")
            val before = OEngineInstallerProfileWatcher.snapshot(root)
            profile(root, "shared-forge-id", "1.20.1", "net.minecraftforge:forge:1.20.1-47.3.0")
            val result = OEngineInstallerProfileWatcher.resolve(
                root, before, "1.20.1", OEngineInstallerProfileWatcher.Loader.FORGE
            )
            assertEquals("shared-forge-id", result.versionId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun unchangedExistingProfileIsNotAcceptedAsInstallResult() {
        val root = createTempDir(prefix = "installer-profile-")
        try {
            profile(root, "old-forge", "1.20.1", "net.minecraftforge:forge:1.20.1-47.2.0")
            val before = OEngineInstallerProfileWatcher.snapshot(root)
            assertThrows(IllegalArgumentException::class.java) {
                OEngineInstallerProfileWatcher.resolve(
                    root, before, "1.20.1", OEngineInstallerProfileWatcher.Loader.FORGE
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }


    @Test fun persistedSnapshotCanBeRestored() {
        val root = createTempDir(prefix = "installer-profile-")
        try {
            profile(root, "old-forge", "1.20.1", "net.minecraftforge:forge:1.20.1-47.2.0")
            val before = OEngineInstallerProfileWatcher.snapshot(root)
            val encoded = org.json.JSONObject().apply {
                before.fingerprints.forEach { (id, fingerprint) -> put(id, fingerprint) }
            }.toString()
            val restored = OEngineInstallerProfileWatcher.restoreSnapshot(encoded)
            profile(root, "old-forge", "1.20.1", "net.minecraftforge:forge:1.20.1-47.3.0")
            val result = OEngineInstallerProfileWatcher.resolve(
                root, restored, "1.20.1", OEngineInstallerProfileWatcher.Loader.FORGE
            )
            assertEquals("old-forge", result.versionId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsMalformedPersistedSnapshot() {
        assertThrows(IllegalArgumentException::class.java) {
            OEngineInstallerProfileWatcher.restoreSnapshot("""{"../escape":"bad"}""")
        }
    }

    @Test fun rejectsSymlinkedVersionsDirectory() {
        val root = createTempDir(prefix = "installer-profile-")
        val outside = createTempDir(prefix = "installer-profile-outside-")
        try {
            val versionsLink = File(root, "versions")
            java.nio.file.Files.createSymbolicLink(versionsLink.toPath(), outside.toPath())
            assertThrows(IllegalArgumentException::class.java) {
                OEngineInstallerProfileWatcher.snapshot(root)
            }
            assertThrows(IllegalArgumentException::class.java) {
                OEngineInstallerProfileWatcher.resolve(
                    root,
                    OEngineInstallerProfileWatcher.Snapshot(emptyMap()),
                    "1.20.1",
                    OEngineInstallerProfileWatcher.Loader.FORGE
                )
            }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

}
