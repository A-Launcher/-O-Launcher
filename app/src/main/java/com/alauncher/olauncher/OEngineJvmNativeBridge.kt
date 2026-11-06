package com.alauncher.olauncher

import java.io.File

/** Result of an Android linker probe; it retains libjvm in-process but does not create or start a VM. */
data class OEngineJvmLibraryProbe(
    val successful: Boolean,
    val status: String,
    val detail: String
)

/**
 * Small native bridge for the selected Android JRE's Invocation API.
 * The diagnostic maps and retains one validated libjvm for process lifetime. It never creates a VM.
 */
object OEngineJvmNativeBridge {
    private val nativeLibraryLoaded: Boolean by lazy {
        System.loadLibrary("oengine_native")
        true
    }

    private external fun nativeProbeJvmLibrary(absolutePath: String): String

    private external fun nativeCreateJvm(vmOptions: Array<String>): String

    private external fun nativeInstallLwjglHook(targetClass: Class<*>, selectedAlias: String, rendererHandle: Long): Boolean

    fun probe(plan: OEngineJvmBootstrapPlan): OEngineJvmLibraryProbe {
        require(plan.structurallyReady) {
            "Resolve the JVM bootstrap plan blockers before probing libjvm.so."
        }
        val library = requireNotNull(plan.jvmLibrary) {
            "The prepared JVM plan has no libjvm.so."
        }
        return probeLibrary(library.absolutePath, plan.runtime.home)
    }

    internal fun createJvm(vmOptions: List<String>): OEngineJvmLibraryProbe {
        require(vmOptions.isNotEmpty()) { "The JVM bootstrap option list is empty." }
        require(vmOptions.size <= 512 && vmOptions.all { it.isNotBlank() && it.length <= 16_384 }) {
            "The JVM bootstrap options exceed the native safety limits."
        }
        check(nativeLibraryLoaded)
        val result = nativeCreateJvm(vmOptions.toTypedArray())
        val status = result.substringBefore(':')
        val detail = result.substringAfter(':', "")
        return OEngineJvmLibraryProbe(
            successful = status == "vm_created",
            status = status,
            detail = detail
        )
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
