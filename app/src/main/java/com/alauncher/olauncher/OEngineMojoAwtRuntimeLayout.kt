package com.alauncher.olauncher

import java.io.File
import java.nio.file.Files

/**
 * Defines the per-instance filesystem contract required by the pinned Mojo AWT installer.
 *
 * Mojo's JavaRunner and MultiRTUtils use global Tools paths. The eventual AWT process
 * adapter must point those globals at this layout before AWTActivity is created; keeping
 * this mapping in one place prevents accidentally installing into Mojo's default folder.
 */
object OEngineMojoAwtRuntimeLayout {
    data class Paths(
        val instanceRoot: File,
        val gameHome: File,
        val gameNew: File,
        val accountHome: File,
        val data: File,
        val cache: File,
        val runtimes: File,
        val nativeLibraries: File,
        val cacioJava8: File,
        val cacioJava17: File
    )

    data class RuntimeInfo(
        val name: String,
        val javaMajor: Int,
        val home: File,
        val vmLibrary: File
    )

    fun paths(instanceDirectory: File): Paths {
        val root = instanceDirectory.canonicalFile
        require(root.exists() && root.isDirectory) { "Minecraft instance directory is missing." }
        fun child(relative: String): File {
            val result = File(root, relative).canonicalFile
            require(result.toPath().startsWith(root.toPath())) { "Mojo runtime path escapes the selected instance." }
            return result
        }
        return Paths(
            instanceRoot = root,
            gameHome = root,
            gameNew = root,
            accountHome = child("oengine/mojo/accounts"),
            data = child("oengine/mojo/data"),
            cache = child("oengine/mojo/cache"),
            runtimes = child("runtimes"),
            nativeLibraries = child("oengine/mojo/native"),
            cacioJava8 = child("caciocavallo"),
            cacioJava17 = child("caciocavallo17")
        )
    }

    /**
     * Mirrors MultiRTUtils' release-file selection and JavaRunner's libjvm lookup.
     * Runtime directories are accepted only when fully contained in the configured runtime root.
     */
    fun inspectRuntime(paths: Paths, runtimeName: String): RuntimeInfo {
        require(runtimeName.matches(Regex("[A-Za-z0-9._+-]{1,80}"))) { "Invalid Java runtime name." }
        val rawHome = File(paths.runtimes, runtimeName)
        require(!Files.isSymbolicLink(rawHome.toPath())) {
            "Java runtime directory must not be a symbolic link."
        }
        val home = rawHome.canonicalFile
        require(home.toPath().startsWith(paths.runtimes.canonicalFile.toPath())) {
            "Java runtime path escapes the selected instance."
        }
        val release = File(home, "release")
        require(release.isFile && !Files.isSymbolicLink(release.toPath())) {
            "Java runtime is missing its release metadata."
        }
        val metadata = release.readText()
        val version = Regex("""JAVA_VERSION="(?:1\.)?(\d+)""").find(metadata)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("Could not read Java runtime version.")
        val arch = Regex("""OS_ARCH="([^"]+)""").find(metadata)?.groupValues?.get(1)
            ?: error("Could not read Java runtime architecture.")
        val candidates = when (arch) {
            "aarch64", "arm64" -> listOf("lib/aarch64/server/libjvm.so", "lib/server/libjvm.so")
            "arm", "armv7l" -> listOf("lib/arm/server/libjvm.so", "lib/server/libjvm.so", "lib/client/libjvm.so")
            "i386", "i486", "i586", "i686" -> listOf("lib/i386/server/libjvm.so", "lib/server/libjvm.so", "lib/client/libjvm.so")
            "x86_64", "amd64" -> listOf("lib/amd64/server/libjvm.so", "lib/server/libjvm.so")
            else -> listOf("lib/server/libjvm.so", "lib/client/libjvm.so")
        }
        val vm = candidates.asSequence().map { File(home, it) }
            .mapNotNull { candidate ->
                if (!candidate.isFile || Files.isSymbolicLink(candidate.toPath())) return@mapNotNull null
                val canonical = candidate.canonicalFile
                canonical.takeIf { it.toPath().startsWith(home.toPath()) }
            }
            .firstOrNull()
            ?: error("Java $version runtime does not contain a supported libjvm.so.")
        return RuntimeInfo(runtimeName, version, home, vm)
    }

    fun requireCacio(paths: Paths, javaMajor: Int) {
        val directory = if (javaMajor == 8) paths.cacioJava8 else paths.cacioJava17
        require(directory.isDirectory) { "Mojo Caciocavallo components are missing for Java $javaMajor." }
        require(directory.listFiles()?.any { it.isFile && it.extension.equals("jar", ignoreCase = true) } == true) {
            "Mojo Caciocavallo JARs are missing for Java $javaMajor."
        }
    }
}
