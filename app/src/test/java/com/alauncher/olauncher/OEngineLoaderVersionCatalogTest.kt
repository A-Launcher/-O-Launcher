package com.alauncher.olauncher

import org.junit.Assert.*
import org.junit.Test

class OEngineLoaderVersionCatalogTest {
    @Test fun fabricAndQuiltMetadataAreParsedWithoutMixingLoaderTypes() {
        val fabric = OEngineLoaderVersionCatalog.parseFabric(
            "1.20.1",
            """[{"loader":{"version":"0.16.9","stable":true}},{"loader":{"version":"0.17.0","stable":false}}]"""
        )
        assertEquals(listOf("0.16.9", "0.17.0"), fabric.map { it.version })
        assertTrue(fabric.all { it.loader == OEngineModLoader.FABRIC })
        val quilt = OEngineLoaderVersionCatalog.parseQuilt(
            "1.20.1",
            """[{"loader":{"version":"0.27.1","stable":true}}]"""
        )
        assertEquals("0.27.1", quilt.single().version)
        assertEquals(OEngineModLoader.QUILT, quilt.single().loader)
    }

    @Test fun forgePromotionsAreScopedToExactMinecraftVersion() {
        val versions = OEngineLoaderVersionCatalog.parseForge(
            "1.20.1",
            """{"promos":{"1.20.1-recommended":"47.3.0","1.20.1-latest":"47.4.0","1.20.2-recommended":"48.1.0"}}"""
        )
        assertEquals(2, versions.size)
        assertEquals("47.3.0", versions.first().version)
        assertTrue(versions.first().stable)
        assertFalse(versions.last().stable)
        assertTrue(versions.all { it.minecraftVersion == "1.20.1" })
    }

    @Test fun neoForgeMavenVersionsAreFilteredByMinecraftMajorMinor() {
        val versions = OEngineLoaderVersionCatalog.parseNeoForge(
            "1.21.1",
            "<metadata><versioning><versions><version>21.1.10</version><version>21.1.20</version><version>21.2.1</version></versions></versioning></metadata>"
        )
        assertEquals(listOf("21.1.20", "21.1.10"), versions.map { it.version })
        assertTrue(versions.all { it.loader == OEngineModLoader.NEOFORGE })
    }

    @Test fun optifineIsNotPresentedAsAnAutomaticallyResolvedInstaller() {
        assertNotNull(OEngineLoaderVersionCatalog.manualInstallerSource(OEngineModLoader.OPTIFINE))
        assertNull(OEngineLoaderVersionCatalog.manualInstallerSource(OEngineModLoader.FABRIC))
    }
}
