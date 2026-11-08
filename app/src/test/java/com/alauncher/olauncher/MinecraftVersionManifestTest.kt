package com.alauncher.olauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class MinecraftVersionManifestTest {
    @Test fun keepsKnownAndFutureManifestChannelsWithoutATypeWhitelist() {
        val manifest = JSONObject(
            """{
              "latest":{"release":"26.1","snapshot":"26.2-snapshot-7"},
              "versions":[
                {"id":"26.1","type":"release","url":"https://example.test/26.1.json"},
                {"id":"26.2-snapshot-1","type":"snapshot","url":"https://example.test/s1.json"},
                {"id":"26.2-snapshot-7","type":"snapshot","url":"https://example.test/s7.json"},
                {"id":"26.3-experimental-1","type":"experimental","url":"https://example.test/e1.json"},
                {"id":"b1.7.3","type":"old_beta","url":"https://example.test/beta.json"},
                {"id":"a1.2.6","type":"old_alpha","url":"https://example.test/alpha.json"},
                {"id":"broken","type":"snapshot","url":"http://insecure.test/broken.json"},
                {"id":"","type":"snapshot","url":"https://example.test/empty.json"}
              ]
            }"""
        )
        val (versions, latest) = MinecraftVersionInstaller.parseVersionManifest(manifest)
        assertEquals("26.1", latest)
        assertEquals(
            listOf("26.1", "26.2-snapshot-1", "26.2-snapshot-7", "26.3-experimental-1", "b1.7.3", "a1.2.6"),
            versions.map { it.id }
        )
    }
    @Test fun loaderJavaRequirementOverridesBaseMinecraftMetadata() {
        val base = JSONObject("""{"javaVersion":{"majorVersion":21}}""")
        val loader = JSONObject("""{"javaVersion":{"majorVersion":25}}""")
        assertEquals(25, MinecraftVersionInstaller.resolveJavaCompatibility("1.21.1", base, loader).runtimeJavaMajor)
        assertEquals(21, MinecraftVersionInstaller.resolveJavaCompatibility("1.21.1", base, null).runtimeJavaMajor)
    }

    @Test fun acceptsLegacyAndSnapshotIdsButRejectsPathTraversal() {
        listOf("rd-132211", "b1.7.3", "26.4-snapshot-7", "1.21.11-rc1").forEach {
            assertEquals(true, MinecraftVersionInstaller.isSafeVersionId(it))
        }
        listOf("../escape", "/absolute", "bad\\\\name", "").forEach {
            assertEquals(false, MinecraftVersionInstaller.isSafeVersionId(it))
        }
    }

}
