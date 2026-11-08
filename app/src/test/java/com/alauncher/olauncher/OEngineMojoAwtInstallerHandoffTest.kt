package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONArray
import java.io.File
import java.nio.file.Files

class OEngineMojoAwtInstallerHandoffTest {
    @Test fun acceptsMojoAgentJvmArgument() {
        val path = "/instance/oengine/installers/agent/forge_installer.jar"
        val args = OEngineMojoAwtInstallerHandoff.parseJavaArgs(
            JSONArray().put("-javaagent:$path").toString(), path
        )
        assertEquals(listOf("-javaagent:$path"), args)
    }

    @Test fun rejectsArgumentsThatDoNotPointAtStagedAgent() {
        assertThrows(IllegalArgumentException::class.java) {
            OEngineMojoAwtInstallerHandoff.parseJavaArgs(
                JSONArray().put("-javaagent:/tmp/other.jar").toString(),
                "/instance/oengine/installers/agent/forge_installer.jar"
            )
        }
    }

    @Test fun rejectsNullAndOversizedArgumentLists() {
        assertThrows(IllegalArgumentException::class.java) {
            OEngineMojoAwtInstallerHandoff.parseJavaArgs(
                JSONArray().put(org.json.JSONObject.NULL).toString(), "/agent.jar"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OEngineMojoAwtInstallerHandoff.parseJavaArgs(
                JSONArray().put("x".repeat(5000)).put("-javaagent:/agent.jar").toString(),
                "/agent.jar"
            )
        }
    }
    @Test fun migratesLegacyRuntimeFoldersAndArchivesBeforeDiscovery() {
        val root = Files.createTempDirectory("oengine-mojo-runtime-migration").toFile()
        try {
            val runtimeRoot = File(root, "runtimes")
            val interrupted = File(runtimeRoot, "Java-8-arm64.installing")
            assertEquals(true, interrupted.mkdirs())
            File(interrupted, "partial.bin").writeBytes(byteArrayOf(1))

            val legacyJava17 = File(runtimeRoot, "installed/Java-17-arm64")
            assertEquals(true, legacyJava17.mkdirs())
            File(legacyJava17, "release").writeText("JAVA_VERSION=\"17\"\nOS_ARCH=\"aarch64\"\n")
            val java17Lib = File(legacyJava17, "lib/server/libjvm.so")
            assertEquals(true, java17Lib.parentFile.mkdirs())
            java17Lib.writeBytes(byteArrayOf(1))

            val legacyJava21 = File(runtimeRoot, "installed/Java-21-arm64")
            assertEquals(true, legacyJava21.mkdirs())
            File(legacyJava21, "release").writeText("JAVA_VERSION=\"21\"\n")

            val existingJava21 = File(runtimeRoot, "Java-21-arm64")
            assertEquals(true, existingJava21.mkdirs())
            File(existingJava21, "release").writeText("JAVA_VERSION=\"21\"\nOS_ARCH=\"aarch64\"\n")
            val java21Lib = File(existingJava21, "lib/server/libjvm.so")
            assertEquals(true, java21Lib.parentFile.mkdirs())
            java21Lib.writeBytes(byteArrayOf(1))

            val invalidRuntime = File(runtimeRoot, "Java-8-arm64")
            assertEquals(true, invalidRuntime.mkdirs())
            File(invalidRuntime, "release").writeText("JAVA_VERSION=\"8\"\n")

            val oldArchive = File(runtimeRoot, "archives/jre17-android-arm64.tar.xz")
            assertEquals(true, oldArchive.parentFile.mkdirs())
            oldArchive.writeBytes(byteArrayOf(1, 2, 3))

            OEngineMojoAwtInstallerHandoff.migrateLegacyRuntimeLayout(
                OEngineMojoAwtRuntimeLayout.paths(root)
            )

            assertEquals(true, File(runtimeRoot, "Java-17-arm64/release").isFile)
            assertEquals(true, File(runtimeRoot, "Java-21-arm64/release").isFile)
            assertEquals(true, File(root, "oengine/mojo/legacy-runtimes/staging/Java-8-arm64.installing/partial.bin").isFile)
            assertEquals(true, File(root, "oengine/mojo/legacy-runtimes/invalid/Java-8-arm64/release").isFile)
            assertEquals(true, File(root, "oengine/runtime-archives/jre17-android-arm64.tar.xz").isFile)
            assertEquals(
                true,
                File(root, "oengine/mojo/legacy-runtimes/installed/Java-21-arm64/release").isFile
            )
            assertEquals(false, File(runtimeRoot, "installed").exists())
            assertEquals(false, File(runtimeRoot, "archives").exists())
            assertEquals(
                "JAVA_VERSION=\"21\"\nOS_ARCH=\"aarch64\"\n",
                File(existingJava21, "release").readText()
            )
        } finally {
            root.deleteRecursively()
        }
    }

}
