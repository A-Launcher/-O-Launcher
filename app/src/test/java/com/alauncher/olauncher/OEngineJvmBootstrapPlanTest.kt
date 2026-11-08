package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineJvmBootstrapPlanTest {
    @Test
    fun preparesJvmLibraryAndReplacesLauncherOwnedProperties() {
        val root = Files.createTempDirectory("oengine-jvm-plan").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            File(runtimeHome, "release").writeText("JAVA_VERSION=\"17\"\n")
            File(runtimeHome, "bin/java").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            val jvm = File(runtimeHome, "lib/server/libjvm.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val game = File(root, "game").apply { mkdirs() }
            val nativeDir = File(game, "natives").apply { mkdirs() }
            val client = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }
            val runtime = OEngineRuntime("Java-17-arm64", 17, "arm64", runtimeHome, File(runtimeHome, "bin/java"))

            val plan = OEngineJvmBootstrapPlanner.prepare(
                runtime = runtime,
                classpath = listOf(client),
                nativeLibraryDirectory = nativeDir,
                workingDirectory = game,
                mainClass = "net.minecraft.client.main.Main",
                requestedVmOptions = listOf("-Xmx2G", "-Djava.home=/old/path", "-Duser.home=/old/home"),
                applicationArguments = listOf("--username", "Player"),
                androidRelease = "15",
                timezoneId = "Africa/Cairo",
                availableProcessors = 8,
                rendererLibraryAlias = OEngineGraphicsStack.LTW_LWJGL_LIBRARY_ALIAS
            )

            assertTrue(plan.blockers.joinToString(), plan.structurallyReady)
            assertEquals(jvm.canonicalFile, plan.jvmLibrary)
            assertTrue(plan.vmOptions.contains("-Xmx2G"))
            assertTrue(plan.vmOptions.any { it == "-Djava.home=" + runtimeHome.canonicalPath })
            assertFalse(plan.vmOptions.any { it == "-Djava.home=/old/path" })
            assertTrue(plan.vmOptions.any { it == "-Duser.home=" + game.canonicalPath })
            assertTrue(plan.vmOptions.contains("-Djdk.lang.Process.launchMechanism=FORK"))
            assertTrue(plan.vmOptions.contains("-Dos.name=Linux"))
            assertTrue(plan.vmOptions.contains("-Dos.version=Android-15"))
            assertTrue(plan.vmOptions.contains("-Duser.language=en"))
            assertTrue(plan.vmOptions.contains("-Duser.country=US"))
            assertTrue(plan.vmOptions.contains("-Dlog4j2.formatMsgNoLookups=true"))
            assertTrue(plan.vmOptions.contains("-Duser.timezone=Africa/Cairo"))
            assertTrue(plan.vmOptions.contains("-Dorg.lwjgl.vulkan.libname=libvulkan.so"))
            assertTrue(plan.vmOptions.contains("-Dorg.lwjgl.spvc.libname=spirv-cross-c-shared"))
            assertTrue(plan.vmOptions.contains("-Dorg.lwjgl.opengl.libname=libGLMojo.so"))
            assertTrue(plan.vmOptions.contains("-XX:ActiveProcessorCount=8"))
            assertTrue(plan.vmOptions.any { it == "-Djna.boot.library.path=" + nativeDir.canonicalPath })
            assertEquals(listOf("--username", "Player"), plan.applicationArguments)
        } finally {
            root.deleteRecursively()
        }
    }


    @Test
    fun configuresStagedAndroidGlfwAbsolutePathForLwjgl() {
        val root = Files.createTempDirectory("oengine-jvm-glfw-plan").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            File(runtimeHome, "release").writeText("JAVA_VERSION=\"17\"\n")
            val java = File(runtimeHome, "bin/java").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            File(runtimeHome, "lib/server/libjvm.so").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            val game = File(root, "game").apply { mkdirs() }
            val nativeDir = File(game, "natives").apply { mkdirs() }
            val client = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }
            val glfw = File(root, "oengine/android-glfw/arm64-v8a/libglfw.so").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }

            val plan = OEngineJvmBootstrapPlanner.prepare(
                OEngineRuntime("Java-17-arm64", 17, "arm64", runtimeHome, java),
                listOf(client), nativeDir, game, "net.minecraft.client.main.Main",
                listOf("-Dorg.lwjgl.glfw.libname=/wrong/path"),
                emptyList(),
                androidGlfwLibrary = glfw,
                additionalNativeLibraryDirectories = listOf(nativeDir)
            )

            assertTrue(plan.blockers.joinToString(), plan.structurallyReady)
            assertTrue(plan.vmOptions.contains("-Dorg.lwjgl.glfw.libname=" + glfw.canonicalPath))
            for (property in listOf("-Djava.library.path=", "-Dorg.lwjgl.librarypath=", "-Djna.boot.library.path=")) {
                val configured = plan.vmOptions.firstOrNull { it.startsWith(property) }
                assertTrue("Missing " + property, configured != null)
                val actualDirectories = configured!!.removePrefix(property)
                    .split(File.pathSeparator).toSet()
                assertTrue("Missing GLFW directory in " + property, actualDirectories.contains(glfw.parentFile!!.canonicalPath))
                assertTrue("Missing game natives directory in " + property, actualDirectories.contains(nativeDir.canonicalPath))
            }
            assertFalse(plan.vmOptions.contains("-Dorg.lwjgl.glfw.libname=/wrong/path"))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun omitsRendererAliasWhenNoRendererProviderWasSelected() {
        val root = Files.createTempDirectory("oengine-jvm-no-renderer").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            File(runtimeHome, "release").writeText("JAVA_VERSION=\"17\"\n")
            val java = File(runtimeHome, "bin/java").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            File(runtimeHome, "lib/server/libjvm.so").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            val game = File(root, "game").apply { mkdirs() }
            val nativeDir = File(game, "natives").apply { mkdirs() }
            val client = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }
            val plan = OEngineJvmBootstrapPlanner.prepare(
                OEngineRuntime("Java-17-arm64", 17, "arm64", runtimeHome, java),
                listOf(client), nativeDir, game, "net.minecraft.client.main.Main",
                emptyList(), emptyList()
            )
            assertTrue(plan.blockers.joinToString(), plan.structurallyReady)
            assertFalse(plan.vmOptions.any { it.startsWith("-Dorg.lwjgl.opengl.libname=") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeRendererLibraryAlias() {
        val root = Files.createTempDirectory("oengine-jvm-bad-alias").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            File(runtimeHome, "release").writeText("JAVA_VERSION=\"17\"\n")
            val java = File(runtimeHome, "bin/java").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            File(runtimeHome, "lib/server/libjvm.so").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            val game = File(root, "game").apply { mkdirs() }
            val nativeDir = File(game, "natives").apply { mkdirs() }
            val client = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }
            OEngineJvmBootstrapPlanner.prepare(
                OEngineRuntime("Java-17-arm64", 17, "arm64", runtimeHome, java),
                listOf(client), nativeDir, game, "net.minecraft.client.main.Main",
                emptyList(), emptyList(), rendererLibraryAlias = "../../libevil.so"
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun doesNotPassPostJava8HotspotFlagsToJava8Runtime() {
        val root = Files.createTempDirectory("oengine-jvm-java8").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            File(runtimeHome, "release").writeText("JAVA_VERSION=\"1.8.0_402\"\n")
            val java = File(runtimeHome, "bin/java").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            File(runtimeHome, "lib/arm/server/libjvm.so").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            val game = File(root, "game").apply { mkdirs() }
            val nativeDir = File(game, "natives").apply { mkdirs() }
            val client = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }

            val plan = OEngineJvmBootstrapPlanner.prepare(
                OEngineRuntime("Java-8-arm", 8, "arm", runtimeHome, java),
                listOf(client), nativeDir, game, "net.minecraft.client.main.Main",
                emptyList(), emptyList(), availableProcessors = 8
            )

            assertTrue(plan.blockers.joinToString(), plan.structurallyReady)
            assertFalse(plan.vmOptions.any { it.startsWith("-XX:ActiveProcessorCount=") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingJvmLibraryAndClasspath() {
        val root = Files.createTempDirectory("oengine-jvm-missing").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            File(runtimeHome, "release").writeText("JAVA_VERSION=\"21\"\n")
            val java = File(runtimeHome, "bin/java").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            val game = File(root, "game").apply { mkdirs() }
            val runtime = OEngineRuntime("Java-21-arm64", 21, "arm64", runtimeHome, java)

            val plan = OEngineJvmBootstrapPlanner.prepare(
                runtime, emptyList(), File(game, "natives"), game,
                "net.minecraft.client.main.Main", emptyList(), emptyList()
            )

            assertFalse(plan.structurallyReady)
            assertTrue(plan.blockers.any { it.contains("libjvm.so") })
            assertTrue(plan.blockers.any { it.contains("classpath is empty") })
            assertTrue(plan.blockers.any { it.contains("native-library directory") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsRuntimeReleaseVersionThatDoesNotMatchSelectedJavaMajor() {
        val root = Files.createTempDirectory("oengine-jvm-version").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            File(runtimeHome, "release").writeText("JAVA_VERSION=\"17\"\n")
            val java = File(runtimeHome, "bin/java").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            File(runtimeHome, "lib/server/libjvm.so").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            val game = File(root, "game").apply { mkdirs() }
            val nativeDir = File(game, "natives").apply { mkdirs() }
            val client = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }

            val plan = OEngineJvmBootstrapPlanner.prepare(
                OEngineRuntime("Java-21-arm64", 21, "arm64", runtimeHome, java),
                listOf(client), nativeDir, game, "net.minecraft.Main", emptyList(), emptyList()
            )

            assertFalse(plan.structurallyReady)
            assertTrue(plan.blockers.any { it.contains("release version does not match") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMainClassWithPathSeparators() {
        val root = Files.createTempDirectory("oengine-jvm-main").toFile()
        try {
            val runtimeHome = File(root, "runtime").apply { mkdirs() }
            File(runtimeHome, "release").writeText("JAVA_VERSION=\"17\"\n")
            val java = File(runtimeHome, "bin/java").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            File(runtimeHome, "lib/server/libjvm.so").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
            val game = File(root, "game").apply { mkdirs() }
            val nativeDir = File(game, "natives").apply { mkdirs() }
            val client = File(game, "client.jar").apply { writeBytes(byteArrayOf(1)) }

            val plan = OEngineJvmBootstrapPlanner.prepare(
                OEngineRuntime("Java-17-arm64", 17, "arm64", runtimeHome, java),
                listOf(client), nativeDir, game, "net/minecraft/Main", emptyList(), emptyList()
            )

            assertFalse(plan.structurallyReady)
            assertTrue(plan.blockers.any { it.contains("dotted Java class name") })
        } finally {
            root.deleteRecursively()
        }
    }
}
