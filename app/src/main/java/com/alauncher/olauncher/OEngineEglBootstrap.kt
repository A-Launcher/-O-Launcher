package com.alauncher.olauncher

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface

/**
 * Keeps an Android EGL/OpenGL ES context alive on the isolated game-process worker thread.
 *
 * This is the native EGL bootstrap only: it is not a Minecraft window and does not
 * translate desktop OpenGL calls. The same thread must own EGL setup and teardown.
 */
internal class OEngineEglBootstrap private constructor(
    private val display: EGLDisplay,
    private val context: EGLContext,
    private val config: EGLConfig,
    private val pbuffer: EGLSurface,
    val eglVersion: String,
    val glVersion: String,
    val glRenderer: String,
    val glesMajor: Int,
    private val ownerThreadId: Long
) {
    private var closed = false
    private var windowSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var window: Surface? = null
    private var windowWidth = 0
    private var windowHeight = 0

    fun attachWindow(surface: Surface, width: Int, height: Int) {
        check(Thread.currentThread().id == ownerThreadId) {
            "EGL window surface must be attached on its creating game-process thread."
        }
        check(!closed) { "EGL bootstrap has already been closed." }
        require(width > 0 && height > 0) { "Android game Surface dimensions must be positive." }
        require(surface.isValid) { "Android game Surface is not valid." }
        detachWindow()
        val created = EGL14.eglCreateWindowSurface(
            display, config, surface,
            intArrayOf(EGL14.EGL_NONE), 0
        )
        check(created != EGL14.EGL_NO_SURFACE) {
            "eglCreateWindowSurface failed: " + error()
        }
        if (!EGL14.eglMakeCurrent(display, created, created, context)) {
            EGL14.eglDestroySurface(display, created)
            throw IllegalStateException("eglMakeCurrent(window) failed: " + error())
        }
        windowSurface = created
        window = surface
        windowWidth = width
        windowHeight = height
        GLES20.glViewport(0, 0, windowWidth, windowHeight)
    }

    fun detachWindow() {
        check(Thread.currentThread().id == ownerThreadId) {
            "EGL window surface must be detached on its creating game-process thread."
        }
        if (windowSurface == EGL14.EGL_NO_SURFACE) {
            window?.release()
            window = null
            windowWidth = 0
            windowHeight = 0
            return
        }
        EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)
        EGL14.eglDestroySurface(display, windowSurface)
        windowSurface = EGL14.EGL_NO_SURFACE
        GLES20.glViewport(0, 0, 1, 1)
        window?.release()
        window = null
        windowWidth = 0
        windowHeight = 0
    }

    fun close() {
        check(Thread.currentThread().id == ownerThreadId) {
            "EGL bootstrap must be released on its creating game-process thread."
        }
        if (closed) return
        detachWindow()
        closed = true
        EGL14.eglMakeCurrent(
            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroySurface(display, pbuffer)
        EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
        EGL14.eglReleaseThread()
    }

    companion object {
        private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

        fun create(minimumGlesMajor: Int = 3): OEngineEglBootstrap {
            require(minimumGlesMajor in 2..3) { "Unsupported O Engine GLES bootstrap version." }
            val ownerThreadId = Thread.currentThread().id
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed: " + error() }

            val versions = IntArray(2)
            check(EGL14.eglInitialize(display, versions, 0, versions, 1)) {
                "eglInitialize failed: " + error()
            }

            var context = EGL14.EGL_NO_CONTEXT
            var surface = EGL14.EGL_NO_SURFACE
            try {
                check(EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)) {
                    "eglBindAPI(OpenGL ES) failed: " + error()
                }
                val renderableBit = if (minimumGlesMajor >= 3) {
                    EGL_OPENGL_ES3_BIT_KHR
                } else {
                    EGL14.EGL_OPENGL_ES2_BIT
                }
                val attributes = intArrayOf(
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, renderableBit,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) &&
                    count[0] > 0 && configs[0] != null) {
                    "No EGL pbuffer config supports OpenGL ES $minimumGlesMajor: " + error()
                }
                val config = configs[0]!!
                surface = EGL14.eglCreatePbufferSurface(
                    display, config,
                    intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
                )
                check(surface != EGL14.EGL_NO_SURFACE) {
                    "eglCreatePbufferSurface failed: " + error()
                }
                context = EGL14.eglCreateContext(
                    display, config, EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL_CONTEXT_CLIENT_VERSION, minimumGlesMajor, EGL14.EGL_NONE), 0
                )
                check(context != EGL14.EGL_NO_CONTEXT) {
                    "eglCreateContext(OpenGL ES $minimumGlesMajor) failed: " + error()
                }
                check(EGL14.eglMakeCurrent(display, surface, surface, context)) {
                    "eglMakeCurrent failed: " + error()
                }
                val glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
                val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
                check(glVersion.isNotBlank() && renderer.isNotBlank()) {
                    "EGL context is current but the GLES driver returned no version or renderer."
                }
                val actualMajor = Regex("OpenGL ES (\\d+)").find(glVersion)
                    ?.groupValues?.get(1)?.toIntOrNull() ?: minimumGlesMajor
                check(actualMajor >= minimumGlesMajor) {
                    "Driver created GLES $actualMajor; GLES $minimumGlesMajor is required."
                }
                return OEngineEglBootstrap(
                    display = display,
                    context = context,
                    config = config,
                    pbuffer = surface,
                    eglVersion = EGL14.eglQueryString(display, EGL14.EGL_VERSION).orEmpty(),
                    glVersion = glVersion,
                    glRenderer = renderer,
                    glesMajor = actualMajor,
                    ownerThreadId = ownerThreadId
                )
            } catch (error: Throwable) {
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                EGL14.eglReleaseThread()
                throw error
            }
        }

        private fun error(): String = "EGL error 0x" + EGL14.eglGetError().toString(16)
    }
}
