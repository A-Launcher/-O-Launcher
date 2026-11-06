package com.alauncher.olauncher

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
        System.loadLibrary("oengine_bootstrap")
        true
    }

    private external fun nativeProbeJvmLibrary(absolutePath: String): String

    fun probe(plan: OEngineJvmBootstrapPlan): OEngineJvmLibraryProbe {
        require(plan.structurallyReady) {
            "Resolve the JVM bootstrap plan blockers before probing libjvm.so."
        }
        val library = requireNotNull(plan.jvmLibrary) {
            "The prepared JVM plan has no libjvm.so."
        }.canonicalFile
        val runtimeHome = plan.runtime.home.canonicalFile
        require(library.name == "libjvm.so" && library.toPath().startsWith(runtimeHome.toPath())) {
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
