package com.alauncher.olauncher

/**
 * Owns the single LTW-tracked EGL context used by Minecraft's LWJGL renderer.
 * Every operation is confined to the isolated game-process worker thread.
 */
internal class OEngineEglBootstrap private constructor(
    private val nativeSession: Long,
    val eglVersion: String,
    val glVersion: String,
    val glRenderer: String,
    val glesMajor: Int,
    private val ownerThreadId: Long
) {
    @Volatile private var closed = false
    // Serializes Binder-thread queue requests against session destruction. GLFW drains
    // the native queue itself, so Kotlin deliberately does not mirror its attachment state.
    private val surfaceQueueLock = Any()

    fun attachWindow(nativeWindowHandle: Long, width: Int, height: Int) {
        checkOwner()
        check(!closed) { "LTW EGL session has already been closed." }
        require(nativeWindowHandle != 0L && width > 0 && height > 0) {
            "LTW window attachment requires a native window and positive dimensions."
        }
        OEngineJvmNativeBridge.attachLtwWindow(nativeSession, nativeWindowHandle, width, height)
    }

    fun detachWindow() {
        checkOwner()
        if (closed) return
        OEngineJvmNativeBridge.detachLtwWindow(nativeSession)
    }

    /** Present one completed frame on the attached Android window surface. */
    fun swapBuffers() {
        checkOwner()
        check(!closed) { "LTW EGL session has already been closed." }
        // Native EGL state is authoritative because GLFW may drain Surface events directly.
        OEngineJvmNativeBridge.swapLtwBuffers(nativeSession)
    }

    /** Queue a replacement Surface from a Binder thread; EGL is changed on the GLFW game thread. */
    fun queueWindow(nativeWindowHandle: Long, width: Int, height: Int) {
        require(nativeWindowHandle != 0L && width > 0 && height > 0)
        synchronized(surfaceQueueLock) {
            check(!closed) { "LTW EGL session has already been closed." }
            OEngineJvmNativeBridge.queueLtwWindow(nativeSession, nativeWindowHandle, width, height)
        }
    }

    /** Queue a detach without making EGL calls on the Android Surface callback thread. */
    fun queueDetachWindow() {
        synchronized(surfaceQueueLock) {
            check(!closed) { "LTW EGL session has already been closed." }
            OEngineJvmNativeBridge.queueLtwWindow(nativeSession, 0L, 0, 0)
        }
    }

    /** Drain one pending Surface transition on the creating game/GLFW thread. */
    fun processPendingWindowChange() {
        checkOwner()
        OEngineJvmNativeBridge.processPendingLtwWindow()
    }

    fun close() {
        checkOwner()
        if (closed) return
        synchronized(surfaceQueueLock) {
            if (closed) return
            closed = true
            // Native teardown is idempotent and owns the actual EGL attachment state.
            OEngineJvmNativeBridge.destroyLtwEglSession(nativeSession)
        }
    }

    private fun checkOwner() {
        check(Thread.currentThread().id == ownerThreadId) {
            "LTW EGL operations must run on the creating game-process thread."
        }
    }

    companion object {
        internal fun parseGlesMajor(version: String): Int? =
            Regex("OpenGL ES ([0-9]+)").find(version)?.groupValues?.get(1)?.toIntOrNull()

        fun create(rendererHandle: Long, minimumGlesMajor: Int = 3): OEngineEglBootstrap {
            require(minimumGlesMajor == 3) { "The pinned LTW renderer requires OpenGL ES 3." }
            val ownerThreadId = Thread.currentThread().id
            val nativeSession = OEngineJvmNativeBridge.createLtwEglSession(rendererHandle, minimumGlesMajor)
            val info = OEngineJvmNativeBridge.ltwEglInfo(nativeSession)
            val version = info.substringBefore(" • ").trim()
            val renderer = info.substringAfter(" • ", "").trim()
            val major = parseGlesMajor(version)
            if (major == null || major < minimumGlesMajor) {
                OEngineJvmNativeBridge.destroyLtwEglSession(nativeSession)
                error(
                    if (major == null) "Could not verify the LTW OpenGL ES version: $version"
                    else "LTW created GLES $major; GLES $minimumGlesMajor is required."
                )
            }
            return OEngineEglBootstrap(
                nativeSession = nativeSession,
                eglVersion = "Android EGL via LTW",
                glVersion = version,
                glRenderer = renderer,
                glesMajor = major,
                ownerThreadId = ownerThreadId
            )
        }
    }
}
