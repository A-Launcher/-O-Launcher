package com.alauncher.olauncher

import org.junit.Assert.*
import org.junit.Test

class OEngineLoaderInstallerTest {
    @Test fun fabricAndQuiltUseMojoCompatibleProfileEndpoints() {
        assertEquals(
            "https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.16.9/profile/json",
            OEngineLoaderInstaller.profileUrl(OEngineModLoader.FABRIC, "1.20.1", "0.16.9")
        )
        assertEquals(
            "https://meta.quiltmc.org/v3/versions/loader/1.20.1/0.27.1/profile/json",
            OEngineLoaderInstaller.profileUrl(OEngineModLoader.QUILT, "1.20.1", "0.27.1")
        )
    }

    @Test fun mavenCoordinatesResolveJarAndClassifierPaths() {
        assertEquals(
            "net/fabricmc/fabric-loader/0.16.9/fabric-loader-0.16.9.jar",
            OEngineLoaderInstaller.mavenArtifactPath("net.fabricmc:fabric-loader:0.16.9")
        )
        assertEquals(
            "org/example/native-lib/2.0/native-lib-2.0-natives-linux.jar",
            OEngineLoaderInstaller.mavenArtifactPath("org.example:native-lib:2.0:natives-linux")
        )
        assertEquals(
            "org/example/metadata/2.0/metadata-2.0.zip",
            OEngineLoaderInstaller.mavenArtifactPath("org.example:metadata:2.0@zip")
        )
    }

    @Test fun unsafeOrUnsupportedMavenCoordinatesAreRejected() {
        assertNull(OEngineLoaderInstaller.mavenArtifactPath("org.example:lib"))
        assertNull(OEngineLoaderInstaller.mavenArtifactPath("../escape:lib:1.0"))
        assertNull(OEngineLoaderInstaller.mavenArtifactPath("org.example:../escape:1.0"))
        assertNull(OEngineLoaderInstaller.mavenArtifactPath("org.example:lib:1.0:bad/classifier"))
        assertNull(OEngineLoaderInstaller.mavenArtifactPath("org.example:lib:1.0@../../zip"))
    }

    @Test fun forgeInstallerUrlsMustBeHttpsAndCredentialFree() {
        assertEquals("https", OEngineInstallerJarStager.validateHttpsUrl("https://maven.minecraftforge.net/installer.jar").protocol)
        listOf(
            "http://maven.minecraftforge.net/installer.jar",
            "https://user:password@maven.minecraftforge.net/installer.jar",
            "https:///installer.jar"
        ).forEach { address ->
            try {
                OEngineInstallerJarStager.validateHttpsUrl(address)
                fail("Accepted unsafe installer URL: $address")
            } catch (_: Exception) {
                // expected
            }
        }
    }
    @Test fun forgeDoesNotAccidentallyEnterFabriclikeHeadlessFlow() {
        try {
            OEngineLoaderInstaller.profileUrl(OEngineModLoader.FORGE, "1.20.1", "47.2.0")
            fail("Forge uses the installer-agent workflow.")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun profileMetadataPathsStayInsideVersionsAndRejectSymlinks() {
        val root = createTempDir(prefix = "loader-profile-")
        try {
            val versions = java.io.File(root, "versions")
            assertTrue(versions.mkdirs())
            val (metadata, temporary) = OEngineLoaderInstaller.prepareProfileMetadataPaths(root, "fabric-test")
            assertEquals(java.io.File(versions, "fabric-test/fabric-test.json").canonicalFile, metadata)
            assertEquals(java.io.File(versions, "fabric-test/fabric-test.json.part").canonicalFile, temporary)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsSymlinkedLoaderProfileMetadataFile() {
        val root = createTempDir(prefix = "loader-profile-")
        val outside = createTempDir(prefix = "loader-profile-outside-")
        try {
            val directory = java.io.File(root, "versions/fabric-test")
            assertTrue(directory.mkdirs())
            val external = java.io.File(outside, "external.json")
            external.writeText("do not overwrite")
            java.nio.file.Files.createSymbolicLink(
                java.io.File(directory, "fabric-test.json").toPath(),
                external.toPath()
            )
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                OEngineLoaderInstaller.prepareProfileMetadataPaths(root, "fabric-test")
            }
            assertEquals("do not overwrite", external.readText())
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun rejectsSymlinkedLoaderProfileDirectoryAndVersionsRoot() {
        val root = createTempDir(prefix = "loader-profile-")
        val outside = createTempDir(prefix = "loader-profile-outside-")
        try {
            val versions = java.io.File(root, "versions")
            assertTrue(versions.mkdirs())
            java.nio.file.Files.createSymbolicLink(
                java.io.File(versions, "fabric-test").toPath(),
                outside.toPath()
            )
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                OEngineLoaderInstaller.prepareProfileMetadataPaths(root, "fabric-test")
            }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }

        val secondRoot = createTempDir(prefix = "loader-profile-")
        val externalVersions = createTempDir(prefix = "loader-profile-external-")
        try {
            java.nio.file.Files.createSymbolicLink(
                java.io.File(secondRoot, "versions").toPath(),
                externalVersions.toPath()
            )
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                OEngineLoaderInstaller.prepareProfileMetadataPaths(secondRoot, "fabric-test")
            }
        } finally {
            secondRoot.deleteRecursively()
            externalVersions.deleteRecursively()
        }
    }

    @Test fun libraryRootIsCreatedInsideInstanceAndRejectsSymlinks() {
        val root = createTempDir(prefix = "loader-library-")
        val outside = createTempDir(prefix = "loader-library-outside-")
        try {
            val libraryRoot = OEngineLoaderInstaller.prepareLibraryRoot(root)
            assertEquals(java.io.File(root, "libraries").canonicalFile, libraryRoot)
            assertTrue(libraryRoot.isDirectory)
        } finally {
            root.deleteRecursively()
        }

        val linkedRoot = createTempDir(prefix = "loader-library-")
        try {
            java.nio.file.Files.createSymbolicLink(
                java.io.File(linkedRoot, "libraries").toPath(),
                outside.toPath()
            )
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                OEngineLoaderInstaller.prepareLibraryRoot(linkedRoot)
            }
        } finally {
            linkedRoot.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
