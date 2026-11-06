package com.alauncher.olauncher

import java.io.File

/** Result of a JVM linker or creation smoke test executed in the isolated game process. */
data class OEngineJvmLibraryProbe(
    val successful: Boolean,
    val status: String,
    val detail: String
)

/**
 * Native bridge for the selected Android JRE's Invocation API.
 * It validates and retains one libjvm, then can create one experimental HotSpot VM per game process.
 */
object OEngineJvmNativeBridge {
    private val nativeLibraryLoaded: Boolean by lazy {
        System.loadLibrary("oengine_native")
        true
    }

    private external fun nativeProbeJvmLibrary(absolutePath: String): String

    private external fun nativeCreateJvm(vmOptions: Array<String>, javaMajor: Int): String

    private external fun nativeInvokeMain(mainClass: String, arguments: Array<String>): String

    private external fun nativeLoadRenderer(rendererPath: String): Long

    private external fun nativeRendererLoadError(): String

    private external fun nativeInstallLwjglHook(targetClassName: String, selectedAlias: String, rendererHandle: Long): Boolean

    fun probe(plan: OEngineJvmBootstrapPlan): OEngineJvmLibraryProbe {
        require(plan.structurallyReady) {
            "Resolve the JVM bootstrap plan blockers before probing libjvm.so."
        }
        val library = requireNotNull(plan.jvmLibrary) {
            "The prepared JVM plan has no libjvm.so."
        }
        return probeLibrary(library.absolutePath, plan.runtime.home)
    }

    internal fun createJvm(vmOptions: List<String>, javaMajor: Int): OEngineJvmLibraryProbe {
        require(javaMajor in setOf(8, 17, 21)) { "Java $javaMajor has no verified native-library hook profile." }
        require(vmOptions.isNotEmpty()) { "The JVM bootstrap option list is empty." }
        require(vmOptions.size <= 512 && vmOptions.all { it.isNotBlank() && it.length <= 16_384 }) {
            "The JVM bootstrap options exceed the native safety limits."
        }
        check(nativeLibraryLoaded)
        val result = nativeCreateJvm(vmOptions.toTypedArray(), javaMajor)
        val status = result.substringBefore(':')
        val detail = result.substringAfter(':', "")
        return OEngineJvmLibraryProbe(
            successful = status.startsWith("vm_created"),
            status = status,
            detail = detail
        )
    }

    internal fun invokeMain(mainClass: String, arguments: List<String>): OEngineJvmLibraryProbe {
        check(nativeLibraryLoaded) { "O Engine native bridge is not loaded." }
        require(mainClass.isNotBlank() && mainClass.length <= 512) { "Minecraft main class is invalid." }
        require(arguments.size <= 16_384) { "Minecraft argument count exceeds the safety limit." }
        val response = nativeInvokeMain(mainClass, arguments.toTypedArray())
        val status = response.substringBefore(':')
        return OEngineJvmLibraryProbe(
            successful = status == "main_returned",
            status = status,
            detail = response.substringAfter(':', "")
        )
    }

    /**
     * Installs the LWJGL 3 loader hook against a class resolved inside embedded HotSpot.
     * The renderer handle must already be loaded and remain valid for the game session.
     */
    /**
     * Loads a renderer from O Engine's private renderer directory and installs
     * the LWJGL hook. The native handle is retained until the game process exits.
     */
    internal fun loadRendererAndInstallLwjglHook(
        rendererFile: File,
        rendererRoot: File,
        targetClassName: String,
        selectedAlias: String
    ): Boolean {
        check(nativeLibraryLoaded) { "O Engine native bridge is not loaded." }
        val root = rendererRoot.canonicalFile
        val library = rendererFile.canonicalFile
        require(library.toPath().startsWith(root.toPath()) && library.isFile && library.length() > 0L) {
            "Renderer library must be a non-empty file inside O Engine's private renderer directory."
        }
        val handle = nativeLoadRenderer(library.absolutePath)
        check(handle != 0L) {
            val detail = runCatching { nativeRendererLoadError() }.getOrDefault("")
            if (detail.isBlank()) "Android's native linker could not load the renderer in O Engine's process namespace."
            else detail
        }
        return installLwjglHook(targetClassName, selectedAlias, handle)
    }

    internal fun installLwjglHook(
        targetClassName: String,
        selectedAlias: String,
        rendererHandle: Long
    ): Boolean {
        check(nativeLibraryLoaded) { "O Engine native bridge is not loaded." }
        require(targetClassName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) {
            "LWJGL target class name must be a dotted Java class name."
        }
        require(selectedAlias.startsWith("lib") && selectedAlias.endsWith(".so") && '/' !in selectedAlias) {
            "Renderer alias must be a shared-library filename such as libGL.so."
        }
        require(rendererHandle != 0L) { "Renderer handle is not loaded." }
        return nativeInstallLwjglHook(targetClassName, selectedAlias, rendererHandle)
    }

    internal fun probeLibrary(absolutePath: String, runtimeHome: File): OEngineJvmLibraryProbe {
        val library = File(absolutePath).canonicalFile
        val canonicalHome = runtimeHome.canonicalFile
        require(library.name == "libjvm.so" && library.toPath().startsWith(canonicalHome.toPath())) {
            "The JVM library must be libjvm.so inside the selected private runtime directory."
        }
        require(library.isFile && library.length() > 0L) { "The selected libjvm.so is missing or empty." }
        check(nativeLibraryLoaded)
        val result = nativeProbeJvmLibrary(library.absolutePath)
        val status = result.substringBefore(':')
        val detail = result.substringAfter(':', "")
        return OEngineJvmLibraryProbe(
            successful = status == "symbols_ok",
            status = status,
            detail = detail
        )
    }
}
