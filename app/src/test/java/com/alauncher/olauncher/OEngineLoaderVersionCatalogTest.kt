package com.alauncher.olauncher

import org.junit.Assert.*
import org.junit.Test

class OEngineLoaderVersionCatalogTest {
    @Test fun loaderVersionsUseNumericOrderingInsteadOfLexicographicOrdering() {
        val forge = OEngineLoaderVersionCatalog.parseForgeMaven(
            "1.20.1",
            "<metadata><versioning><versions>" +
                "<version>1.20.1-47.9.0</version>" +
                "<version>1.20.1-47.10.0</version>" +
                "<version>1.20.1-47.2.0</version>" +
                "</versions></versioning></metadata>",
            """{"promos":{"1.20.1-recommended":"47.2.0"}}"""
        )
        assertEquals(listOf("47.2.0", "47.10.0", "47.9.0"), forge.map { it.version })
    }

    @Test fun fabricCatalogPlacesStableVersionsBeforeNewerUnstableVersions() {
        val versions = OEngineLoaderVersionCatalog.parseFabric(
            "1.21.1",
            """[{"loader":{"version":"0.16.0","stable":false}},{"loader":{"version":"0.15.11","stable":true}},{"loader":{"version":"0.15.9","stable":true}}]"""
        )
        assertEquals(listOf("0.15.11", "0.15.9", "0.16.0"), versions.map { it.version })
    }

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
    @Test fun forgeMavenCatalogIncludesAllMinecraftScopedVersionsAndMarksRecommended() {
        val versions = OEngineLoaderVersionCatalog.parseForgeMaven(
            "1.20.1",
            "<metadata><versioning><versions>" +
                "<version>1.20.1-47.1.0</version>" +
                "<version>1.20.1-47.2.0</version>" +
                "<version>1.20.2-48.0.0</version>" +
                "</versions></versioning></metadata>",
            """{"promos":{"1.20.1-recommended":"47.1.0"}}"""
        )
        assertEquals(listOf("47.1.0", "47.2.0"), versions.map { it.version }.sorted())
        assertEquals("47.1.0", versions.single { it.stable }.version)
        assertTrue(versions.all { it.loader == OEngineModLoader.FORGE })
        assertTrue(versions.all { it.installerUrl!!.contains("/1.20.1-") })
    }

}

