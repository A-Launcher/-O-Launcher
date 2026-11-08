package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OEngineMojoAwtRuntimeLayoutTest {
    @Test fun mapsAllMojoPathsInsideSelectedInstance() {
        val root = Files.createTempDirectory("oengine-instance").toFile()
        try {
            val paths = OEngineMojoAwtRuntimeLayout.paths(root)
            assertEquals(root.canonicalFile, paths.gameHome)
            assertEquals(root.canonicalFile, paths.gameNew)
            assertEquals(File(root, "runtimes").canonicalFile, paths.runtimes)
            listOf(paths.accountHome, paths.data, paths.cache, paths.runtimes, paths.nativeLibraries,
                paths.cacioJava8, paths.cacioJava17).forEach {
                assertTrue(it.toPath().startsWith(root.canonicalFile.toPath()))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsInvalidRuntimeNames() {
        val root = Files.createTempDirectory("oengine-instance").toFile()
        try {
            val paths = OEngineMojoAwtRuntimeLayout.paths(root)
            assertThrows(IllegalArgumentException::class.java) {
                OEngineMojoAwtRuntimeLayout.inspectRuntime(paths, "../outside")
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun validatesRuntimeReleaseAndFindsJvm() {
        val root = Files.createTempDirectory("oengine-instance").toFile()
        try {
            val paths = OEngineMojoAwtRuntimeLayout.paths(root)
            val runtime = File(paths.runtimes, "java17")
            val vm = File(runtime, "lib/aarch64/server/libjvm.so")
            vm.parentFile.mkdirs()
            vm.writeBytes(byteArrayOf(1))
            File(runtime, "release").writeText("JAVA_VERSION=\"17.0.12\"\nOS_ARCH=\"aarch64\"\n")
            val inspected = OEngineMojoAwtRuntimeLayout.inspectRuntime(paths, "java17")
            assertEquals(17, inspected.javaMajor)
            assertEquals(vm.canonicalFile, inspected.vmLibrary.canonicalFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsRuntimeWithoutVmLibrary() {
        val root = Files.createTempDirectory("oengine-instance").toFile()
        try {
            val paths = OEngineMojoAwtRuntimeLayout.paths(root)
            val runtime = File(paths.runtimes, "java17")
            runtime.mkdirs()
            File(runtime, "release").writeText("JAVA_VERSION=\"17\"\nOS_ARCH=\"aarch64\"\n")
            assertThrows(IllegalStateException::class.java) {
                OEngineMojoAwtRuntimeLayout.inspectRuntime(paths, "java17")
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
