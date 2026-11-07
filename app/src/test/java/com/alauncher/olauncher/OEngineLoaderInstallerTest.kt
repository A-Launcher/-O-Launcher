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

    @Test fun forgeDoesNotAccidentallyEnterFabriclikeHeadlessFlow() {
        try {
            OEngineLoaderInstaller.profileUrl(OEngineModLoader.FORGE, "1.20.1", "47.2.0")
            fail("Forge uses the installer-agent workflow.")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
