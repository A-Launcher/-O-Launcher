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
    private var closed = false
    private var windowAttached = false

    fun attachWindow(nativeWindowHandle: Long, width: Int, height: Int) {
        checkOwner()
        check(!closed) { "LTW EGL session has already been closed." }
        require(nativeWindowHandle != 0L && width > 0 && height > 0) {
            "LTW window attachment requires a native window and positive dimensions."
        }
        OEngineJvmNativeBridge.attachLtwWindow(nativeSession, nativeWindowHandle, width, height)
        windowAttached = true
    }

    fun detachWindow() {
        checkOwner()
        if (closed || !windowAttached) return
        OEngineJvmNativeBridge.detachLtwWindow(nativeSession)
        windowAttached = false
    }

    /** Present one completed frame on the attached Android window surface. */
    fun swapBuffers() {
        checkOwner()
        check(!closed && windowAttached) { "No active LTW game window is available for presentation." }
        OEngineJvmNativeBridge.swapLtwBuffers(nativeSession)
    }

    fun close() {
        checkOwner()
        if (closed) return
        if (windowAttached) detachWindow()
        closed = true
        OEngineJvmNativeBridge.destroyLtwEglSession(nativeSession)
    }

    private fun checkOwner() {
        check(Thread.currentThread().id == ownerThreadId) {
            "LTW EGL operations must run on the creating game-process thread."
        }
    }

    companion object {
        fun create(rendererHandle: Long, minimumGlesMajor: Int = 3): OEngineEglBootstrap {
            require(minimumGlesMajor == 3) { "The pinned LTW renderer requires OpenGL ES 3." }
            val ownerThreadId = Thread.currentThread().id
            val nativeSession = OEngineJvmNativeBridge.createLtwEglSession(rendererHandle, minimumGlesMajor)
            val info = OEngineJvmNativeBridge.ltwEglInfo(nativeSession)
            val version = info.substringBefore(" • ").trim()
            val renderer = info.substringAfter(" • ", "").trim()
            val major = Regex("OpenGL ES ([0-9]+)").find(version)
                ?.groupValues?.get(1)?.toIntOrNull() ?: minimumGlesMajor
            if (major < minimumGlesMajor) {
                OEngineJvmNativeBridge.destroyLtwEglSession(nativeSession)
                error("LTW created GLES $major; GLES $minimumGlesMajor is required.")
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
