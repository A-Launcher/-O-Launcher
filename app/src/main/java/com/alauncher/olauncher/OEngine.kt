package com.alauncher.olauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * O Engine is the launcher's own Android-side runtime and process orchestration layer.
 * It deliberately owns paths, runtime discovery and process startup instead of delegating
 * those responsibilities to another launcher implementation.
 */
data class OEnginePaths(val root: File) {
    val versions = File(root, "versions")
    val libraries = File(root, "libraries")
    val assets = File(root, "assets")
    val runtimes = File(root, "runtimes/installed")
    val logs = File(root, "logs")

    fun prepare() {
        listOf(root, versions, libraries, assets, runtimes, logs).forEach {
            if (!it.exists() && !it.mkdirs()) throw IllegalStateException("O Engine could not create " + it.name)
        }
    }
}

data class OEngineRuntime(
    val name: String,
    val javaMajor: Int,
    val architecture: String,
    val home: File,
    val executable: File
)

data class OEngineProbeResult(
    val runtime: OEngineRuntime,
    val exitCode: Int,
    val output: String
) {
    val successful: Boolean get() = exitCode == 0 && output.contains("version")
}

object OLauncherEngine {
    fun paths(gameRoot: File): OEnginePaths = OEnginePaths(gameRoot.canonicalFile)

    fun findRuntime(gameRoot: File, javaMajor: Int): OEngineRuntime? {
        val architecture = AndroidRuntimeInstaller.deviceArchitecture()
        val name = "Java-" + javaMajor + "-" + architecture
        val home = File(paths(gameRoot).runtimes, name)
        val executable = File(home, "bin/java")
        val release = File(home, "release")
        if (!executable.isFile || !release.isFile) return null
        val text = release.readText()
        val version = Regex("""JAVA_VERSION="(?:1\.)?(\d+)""").find(text)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (version != javaMajor) return null
        return OEngineRuntime(name, version, architecture, home, executable)
    }

    suspend fun probeRuntime(gameRoot: File, javaMajor: Int): OEngineProbeResult = withContext(Dispatchers.IO) {
        val runtime = findRuntime(gameRoot, javaMajor)
            ?: throw IllegalStateException("O Engine cannot find a valid installed Java " + javaMajor + " runtime.")
        val enginePaths = paths(gameRoot)
        enginePaths.prepare()
        val log = File(enginePaths.logs, "runtime-" + javaMajor + "-probe.txt")
        val libraryPaths = listOf(
            File(runtime.home, "lib"),
            File(runtime.home, "lib/server"),
            File(runtime.home, "lib/jli")
        ).filter { it.isDirectory }.joinToString(File.pathSeparator) { it.absolutePath }
        val builder = ProcessBuilder(runtime.executable.absolutePath, "-version")
            .directory(enginePaths.root)
            .redirectErrorStream(true)
            .redirectOutput(log)
        builder.environment().apply {
            put("JAVA_HOME", runtime.home.absolutePath)
            put("HOME", enginePaths.root.absolutePath)
            put("TMPDIR", File(enginePaths.root, "tmp").apply { mkdirs() }.absolutePath)
            if (libraryPaths.isNotBlank()) {
                val existing = get("LD_LIBRARY_PATH").orEmpty()
                put("LD_LIBRARY_PATH", listOf(libraryPaths, existing).filter { it.isNotBlank() }.joinToString(File.pathSeparator))
            }
        }
        val process = builder.start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Java runtime test timed out after 30 seconds.")
        }
        val output = if (log.isFile) log.readText().takeLast(12000) else ""
        OEngineProbeResult(runtime, process.exitValue(), output)
    }
}
