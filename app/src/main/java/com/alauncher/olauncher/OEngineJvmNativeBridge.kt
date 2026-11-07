package com.alauncher.olauncher

import java.io.File
import android.view.Surface

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

    private external fun nativeRendererHookError(): String

    private external fun nativeAcquireWindow(surface: Surface, width: Int, height: Int): Long

    private external fun nativeReleaseWindow(handle: Long)

    private external fun nativeEnqueueInputBatch(
        devices: IntArray,
        actions: IntArray,
        codes: IntArray,
        scanCodes: IntArray,
        xs: FloatArray,
        ys: FloatArray,
        values: FloatArray,
        timestamps: LongArray,
        modifiers: IntArray,
        codepoints: IntArray
    ): Int

    private external fun nativeInputResetPending(): Boolean

    /** Sends a validated Android input batch into Rust's queue shared with the native GLFW backend. */
    internal fun enqueueInputEvents(events: List<OEngineInputEvent>): Int {
        require(events.isNotEmpty() && events.size <= 128) { "Native input batches must contain 1–128 events." }
        require(events.none { it.action == OEngineInputAction.TEXT }) {
            "Bulk text input is not supported by the native GLFW event bridge yet."
        }
        check(nativeLibraryLoaded) { "O Engine native bridge is not loaded." }
        return nativeEnqueueInputBatch(
            events.map { it.device.ordinal }.toIntArray(),
            events.map { it.action.ordinal }.toIntArray(),
            events.map { it.code }.toIntArray(),
            events.map { it.scanCode }.toIntArray(),
            events.map { it.x }.toFloatArray(),
            events.map { it.y }.toFloatArray(),
            events.map { it.value }.toFloatArray(),
            events.map { it.timestampNanos }.toLongArray(),
            events.map { it.modifiers }.toIntArray(),
            events.map { it.codepoint }.toIntArray()
        )
    }

    internal fun inputResetPending(): Boolean {
        check(nativeLibraryLoaded) { "O Engine native bridge is not loaded." }
        return nativeInputResetPending()
    }


    private external fun nativeCreateLtwEglSession(rendererHandle: Long, minimumGlesMajor: Int): Long
    private external fun nativeAttachLtwWindow(session: Long, window: Long, width: Int, height: Int): Boolean
    private external fun nativeDetachLtwWindow(session: Long): Boolean
    private external fun nativeSwapLtwBuffers(session: Long): Boolean
    private external fun nativeLtwEglInfo(session: Long): String
    private external fun nativeLtwEglError(): String
    private external fun nativeDestroyLtwEglSession(session: Long)

    internal fun createLtwEglSession(rendererHandle: Long, minimumGlesMajor: Int): Long {
        check(nativeLibraryLoaded) { "O Engine native bridge is not loaded." }
        require(rendererHandle != 0L && minimumGlesMajor == 3) {
            "LTW requires its retained native library and OpenGL ES 3."
        }
        return nativeCreateLtwEglSession(rendererHandle, minimumGlesMajor).also {
            check(it != 0L) {
                nativeLtwEglError().ifBlank { "Could not create the LTW-owned EGL session." }
            }
        }
    }

    internal fun attachLtwWindow(session: Long, window: Long, width: Int, height: Int) {
        check(nativeAttachLtwWindow(session, window, width, height)) {
            nativeLtwEglError().ifBlank { "LTW could not attach the Android game window." }
        }
    }

    internal fun detachLtwWindow(session: Long) {
        check(nativeDetachLtwWindow(session)) {
            nativeLtwEglError().ifBlank { "LTW could not detach the Android game window." }
        }
    }

    internal fun swapLtwBuffers(session: Long) {
        check(nativeSwapLtwBuffers(session)) {
            nativeLtwEglError().ifBlank { "LTW could not present the current game frame." }
        }
    }

    internal fun ltwEglInfo(session: Long): String = nativeLtwEglInfo(session)

    internal fun destroyLtwEglSession(session: Long) {
        if (session != 0L) nativeDestroyLtwEglSession(session)
    }

    /** Acquire a native Android window reference for the future GLFW/EGL bridge. */
    internal fun acquireNativeWindow(surface: Surface, width: Int, height: Int): Long {
        require(surface.isValid && width > 0 && height > 0) { "Native game window requires a valid Surface and positive dimensions." }
        check(nativeLibraryLoaded) { "O Engine native bridge is not loaded." }
        return nativeAcquireWindow(surface, width, height).also {
            check(it != 0L) { "ANativeWindow_fromSurface or native buffer geometry setup failed." }
        }
    }

    /** Release exactly one reference returned by acquireNativeWindow. */
    internal fun releaseNativeWindow(handle: Long) {
        if (handle == 0L) return
        check(nativeLibraryLoaded) { "O Engine native bridge is not loaded." }
        nativeReleaseWindow(handle)
    }

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
    /** Load and retain the renderer before JNI_CreateJavaVM, matching Mojo's startup order. */
    internal fun preloadRenderer(rendererFile: File, rendererRoot: File): Long {
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
        return handle
    }

    internal fun loadRendererAndInstallLwjglHook(
        rendererFile: File,
        rendererRoot: File,
        targetClassName: String,
        selectedAlias: String
    ): Boolean = installLwjglHook(targetClassName, selectedAlias, preloadRenderer(rendererFile, rendererRoot))

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
        val installed = nativeInstallLwjglHook(targetClassName, selectedAlias, rendererHandle)
        if (!installed) {
            val detail = runCatching { nativeRendererHookError() }.getOrDefault("")
            error(detail.ifBlank { "HotSpot rejected the LWJGL native-loader hook without a diagnostic." })
        }
        return true
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
