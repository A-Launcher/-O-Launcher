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
    val runtimes = File(root, "runtimes")
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


/** A shell-free command description. It is inspectable but never executed by the preview API. */
data class OEngineCommandPreview(
    val runtime: OEngineRuntime?,
    val workingDirectory: File,
    val nativeDirectory: File,
    val arguments: List<String>,
    val blockers: List<String>,
    val jvmBootstrapPlan: OEngineJvmBootstrapPlan? = null
) {
    val launchable: Boolean get() = blockers.isEmpty()
}

object OLauncherEngine {
    fun paths(gameRoot: File): OEnginePaths = OEnginePaths(gameRoot.canonicalFile)

    fun findRuntime(gameRoot: File, javaMajor: Int): OEngineRuntime? {
        val root = gameRoot.canonicalFile
        require(root.isDirectory || root.mkdirs()) { "O Engine could not create the game directory." }
        val runtimeLayout = OEngineMojoAwtRuntimeLayout.paths(root)
        OEngineMojoAwtInstallerHandoff.migrateLegacyRuntimeLayout(runtimeLayout)
        val architecture = AndroidRuntimeInstaller.deviceArchitecture()
        val name = "Java-" + javaMajor + "-" + architecture
        val home = File(runtimeLayout.runtimes, name)
        val executable = File(home, "bin/java")
        val release = File(home, "release")
        if (!executable.isFile || !release.isFile) return null
        val text = release.readText()
        val version = Regex("""JAVA_VERSION="(?:1\.)?(\d+)""").find(text)
            ?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (version != javaMajor) return null
        return OEngineRuntime(name, version, architecture, home, executable)
    }

    fun prepareCommandPreview(
        gameRoot: File,
        plan: MinecraftVersionInstaller.LaunchPlan,
        rendererLibraryAlias: String? = null,
        androidGlfwLibrary: File? = null
    ): OEngineCommandPreview {
        val enginePaths = paths(gameRoot)
        val runtime = findRuntime(gameRoot, plan.javaMajorVersion)
        val versionDirectory = File(enginePaths.versions, plan.version)
        val nativeDirectory = File(versionDirectory, "natives")
        val blockers = mutableListOf<String>()
        if (runtime == null) blockers.add("Java runtime is not installed and validated.")
        if (!versionDirectory.isDirectory) blockers.add("Minecraft version directory is missing.")
        if (plan.classpath.isEmpty() || plan.classpath.any { !File(it).isFile || File(it).length() == 0L }) {
            blockers.add("One or more classpath artifacts are missing or empty.")
        }
        if (!nativeDirectory.isDirectory || nativeDirectory.walkTopDown().none { it.isFile }) {
            blockers.add("Android-compatible native libraries have not been installed.")
        }
        if (plan.mainClass.isBlank()) blockers.add("Minecraft main class is missing.")
        val jvmBootstrapPlan = runtime?.let { selectedRuntime ->
            OEngineJvmBootstrapPlanner.prepare(
                runtime = selectedRuntime,
                classpath = plan.classpath.map(::File),
                // Java's System.loadLibrary searches java.library.path, not merely
                // LD_LIBRARY_PATH. Point it at the private ABI-matched Android native
                // set staged alongside libglfw.so (LWJGL core/OpenGL/OpenAL included).
                nativeLibraryDirectory = androidGlfwLibrary?.canonicalFile?.parentFile ?: nativeDirectory,
                workingDirectory = enginePaths.root,
                mainClass = plan.mainClass,
                requestedVmOptions = plan.jvmArguments,
                applicationArguments = plan.gameArguments,
                androidRelease = android.os.Build.VERSION.RELEASE,
                timezoneId = java.util.TimeZone.getDefault().id,
                availableProcessors = Runtime.getRuntime().availableProcessors(),
                rendererLibraryAlias = rendererLibraryAlias,
                androidGlfwLibrary = androidGlfwLibrary,
                additionalNativeLibraryDirectories = listOfNotNull(
                    nativeDirectory.takeIf { androidGlfwLibrary != null }
                )
            )
        }
        jvmBootstrapPlan?.blockers?.let(blockers::addAll)
        val lwjglResolution = OEngineLwjglNativeResolver.resolve(plan.classpath.map(::File), androidGlfwLibrary)
        blockers.addAll(lwjglResolution.blockers)

        val classpath = plan.classpath.joinToString(File.pathSeparator)
        val substitutions = mapOf(
            "classpath" to classpath,
            "natives_directory" to nativeDirectory.absolutePath,
            "library_directory" to enginePaths.libraries.absolutePath,
            "launcher_name" to "O-Launcher",
            "launcher_version" to "0.1.0"
        )
        val args = plan.jvmArguments.map { original ->
            substitutions.entries.fold(original) { value, entry ->
                value.replace("$" + "{" + entry.key + "}", entry.value)
            }
        }.toMutableList()
        val hasClasspathFlag = args.any { it == "-cp" || it == "-classpath" || it == "--class-path" }
        if (!hasClasspathFlag) {
            args.add("-cp")
            args.add(classpath)
        }
        args.add(plan.mainClass)
        args.addAll(plan.gameArguments)
        return OEngineCommandPreview(
            runtime = runtime,
            workingDirectory = enginePaths.root,
            nativeDirectory = nativeDirectory,
            arguments = args,
            blockers = blockers.distinct(),
            jvmBootstrapPlan = jvmBootstrapPlan
        )
    }
    suspend fun probeJvmInvocationApi(plan: OEngineJvmBootstrapPlan): OEngineJvmLibraryProbe =
        withContext(Dispatchers.IO) {
            OEngineJvmNativeBridge.probe(plan)
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

