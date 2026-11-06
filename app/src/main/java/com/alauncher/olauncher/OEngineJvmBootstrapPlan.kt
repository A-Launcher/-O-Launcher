package com.alauncher.olauncher

import java.io.File

/** A prepared in-process JVM invocation; preparation never loads libjvm or starts a VM. */
data class OEngineJvmBootstrapPlan(
    val runtime: OEngineRuntime,
    val jvmLibrary: File?,
    val classpath: List<File>,
    val nativeLibraryDirectory: File,
    val workingDirectory: File,
    val mainClass: String,
    val vmOptions: List<String>,
    val applicationArguments: List<String>,
    val blockers: List<String>,
    val warnings: List<String>
) {
    val structurallyReady: Boolean get() = blockers.isEmpty()
}

/**
 * Builds a deterministic JNI_CreateJavaVM input plan for the selected Android JRE.
 *
 * The native bridge must still validate JNI_CreateJavaVM exports, dependency resolution,
 * linker namespace visibility and the target JRE's Android compatibility at load time.
 */
object OEngineJvmBootstrapPlanner {
    private val jvmLibraryRelativePaths = listOf(
        "lib/server/libjvm.so",
        "lib/libjvm.so",
        "lib/aarch64/server/libjvm.so",
        "lib/arm/server/libjvm.so",
        "lib/x86_64/server/libjvm.so",
        "lib/x86/server/libjvm.so",
        "jre/lib/server/libjvm.so"
    )

    private val launcherOwnedProperties = setOf(
        "-Djava.home",
        "-Djava.library.path",
        "-Djava.class.path",
        "-Duser.home",
        "-Djava.io.tmpdir",
        "-Djna.boot.library.path",
        "-Dos.name",
        "-Dos.version",
        "-Duser.language",
        "-Duser.country",
        "-Djdk.lang.Process.launchMechanism",
        "-Dlog4j2.formatMsgNoLookups",
        "-Dfml.earlyprogresswindow",
        "-Dloader.disable_forked_guis",
        "-Dorg.lwjgl.system.allocator",
        "-Duser.timezone",
        "-Dorg.lwjgl.vulkan.libname",
        "-Dorg.lwjgl.spvc.libname",
        "-XX:ActiveProcessorCount"
    )

    fun prepare(
        runtime: OEngineRuntime,
        classpath: List<File>,
        nativeLibraryDirectory: File,
        workingDirectory: File,
        mainClass: String,
        requestedVmOptions: List<String>,
        applicationArguments: List<String>,
        androidRelease: String = "unknown",
        timezoneId: String = "UTC",
        availableProcessors: Int = 1
    ): OEngineJvmBootstrapPlan {
        val blockers = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val home = runtime.home.canonicalFile
        val java = runtime.executable.canonicalFile
        if (!java.isFile) blockers += "Selected Android Java executable is missing."
        val releaseFile = File(home, "release")
        if (!releaseFile.isFile) {
            blockers += "Selected runtime release metadata is missing."
        } else {
            val releaseVersion = Regex("""JAVA_VERSION="(?:1\.)?(\d+)""").find(releaseFile.readText())
                ?.groupValues?.get(1)?.toIntOrNull()
            if (releaseVersion != runtime.javaMajor) {
                blockers += "Selected runtime release version does not match Java " + runtime.javaMajor + "."
            }
        }

        val resolvedClasspath = classpath.map { it.canonicalFile }
        if (resolvedClasspath.isEmpty()) blockers += "Minecraft classpath is empty."
        if (resolvedClasspath.any { !it.isFile || it.length() <= 0L }) {
            blockers += "One or more Minecraft classpath entries are missing or empty."
        }

        val nativeDir = nativeLibraryDirectory.canonicalFile
        if (!nativeDir.isDirectory) blockers += "Android native-library directory is missing."

        val workDir = workingDirectory.canonicalFile
        if (!workDir.isDirectory) blockers += "Minecraft working directory is missing."
        if (!mainClass.matches(Regex("""[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*"""))) {
            blockers += "Minecraft main class must be a dotted Java class name."
        }

        val jvmLibrary = jvmLibraryRelativePaths
            .asSequence()
            .map { File(home, it).canonicalFile }
            .firstOrNull { it.isFile && it.length() > 0L }
        if (jvmLibrary == null) {
            blockers += "Runtime does not contain libjvm.so in a recognized Android JRE location."
        } else if (!jvmLibrary.toPath().startsWith(home.toPath())) {
            blockers += "Resolved libjvm.so escapes the selected runtime directory."
        }

        val vmOptions = normalizeVmOptions(
            requestedVmOptions,
            mapOf(
                "-Djava.home" to home.absolutePath,
                "-Djava.library.path" to nativeDir.absolutePath,
                "-Djava.class.path" to resolvedClasspath.joinToString(File.pathSeparator) { it.absolutePath },
                "-Duser.home" to workDir.absolutePath,
                "-Djava.io.tmpdir" to File(workDir, "tmp").absolutePath,
                "-Djna.boot.library.path" to nativeDir.absolutePath,
                "-Dos.name" to "Linux",
                "-Dos.version" to "Android-" + androidRelease,
                "-Duser.language" to "en",
                "-Duser.country" to "US",
                "-Djdk.lang.Process.launchMechanism" to "FORK",
                "-Dlog4j2.formatMsgNoLookups" to "true",
                "-Dfml.earlyprogresswindow" to "false",
                "-Dloader.disable_forked_guis" to "true",
                "-Dorg.lwjgl.system.allocator" to "system",
                "-Duser.timezone" to timezoneId,
                "-Dorg.lwjgl.vulkan.libname" to "libvulkan.so",
                "-Dorg.lwjgl.spvc.libname" to "spirv-cross-c-shared",
                "-XX:ActiveProcessorCount" to availableProcessors.coerceAtLeast(1).toString()
            )
        )

        warnings += "JNI_CreateJavaVM export, native dependencies and Android linker visibility must be checked by the native bootstrap before VM creation."
        warnings += "A successful plan is not proof that the selected OpenJDK runtime can coexist with Android Runtime in this process."

        return OEngineJvmBootstrapPlan(
            runtime = runtime,
            jvmLibrary = jvmLibrary,
            classpath = resolvedClasspath,
            nativeLibraryDirectory = nativeDir,
            workingDirectory = workDir,
            mainClass = mainClass,
            vmOptions = vmOptions,
            applicationArguments = applicationArguments.toList(),
            blockers = blockers.distinct(),
            warnings = warnings.distinct()
        )
    }

    private fun normalizeVmOptions(
        requested: List<String>,
        ownedValues: Map<String, String>
    ): List<String> {
        val preserved = requested.filterNot { option ->
            val key = option.substringBefore('=')
            key in launcherOwnedProperties
        }
        return preserved + ownedValues.map { (key, value) -> "$key=$value" }
    }
}
