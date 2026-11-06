package com.alauncher.olauncher

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface

/**
 * A thread-confined EGL window session owned by O Engine.
 *
 * The caller must create, use, swap, and close this session on the same dedicated
 * render thread. It provides an Android OpenGL ES surface; it is NOT a desktop
 * OpenGL, GLFW, or LWJGL compatibility layer.
 */
class OEngineEglSession private constructor(
    private val display: EGLDisplay,
    private val eglSurface: EGLSurface,
    private val context: EGLContext,
    val eglVersion: String,
    val glVersion: String,
    val glVendor: String,
    val glRenderer: String,
    val openGlesMajor: Int,
    private val ownerThreadId: Long
) : OEngineGraphicsBackend {
    override val capabilities = OEngineGraphicsCapabilities(
        eglVersion = eglVersion,
        apiVersion = glVersion,
        vendor = glVendor,
        renderer = glRenderer,
        apiMajor = openGlesMajor,
        extensions = emptySet(),
        isEmbeddedProfile = true
    )
    private var closed = false

    override fun makeCurrent() {
        checkOwner()
        check(!closed) { "O Engine EGL session is closed." }
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            eglFailure("Could not make the O Engine EGL context current")
        }
    }

    override fun setSwapInterval(interval: Int) {
        checkOwner()
        check(!closed) { "O Engine EGL session is closed." }
        require(interval >= 0) { "Swap interval cannot be negative." }
        check(EGL14.eglSwapInterval(display, interval)) {
            eglFailure("Could not set the EGL swap interval")
        }
    }

    override fun swapBuffers(): Boolean {
        checkOwner()
        check(!closed) { "O Engine EGL session is closed." }
        return EGL14.eglSwapBuffers(display, eglSurface)
    }

    override fun close() {
        checkOwner()
        if (closed) return
        closed = true
        EGL14.eglMakeCurrent(
            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
        )
        EGL14.eglDestroyContext(display, context)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglTerminate(display)
        EGL14.eglReleaseThread()
    }

    private fun checkOwner() {
        check(Thread.currentThread().id == ownerThreadId) {
            "O Engine EGL sessions are confined to the thread that created them."
        }
    }

    companion object {
        private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

        /** Must be called on the dedicated render thread with a valid Android Surface. */
        fun create(surface: Surface, preferredMajor: Int = 3): OEngineEglSession {
            require(surface.isValid) { "The Android rendering surface is not valid." }
            require(preferredMajor == 2 || preferredMajor == 3) {
                "O Engine currently supports OpenGL ES 2 or 3 contexts."
            }

            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { eglFailure("Could not acquire EGL display") }
            val versions = if (preferredMajor == 3) listOf(3, 2) else listOf(2)
            val versionParts = IntArray(2)
            check(EGL14.eglInitialize(display, versionParts, 0, versionParts, 1)) {
                eglFailure("Could not initialize EGL")
            }

            var createdSurface = EGL14.EGL_NO_SURFACE
            var createdContext = EGL14.EGL_NO_CONTEXT
            try {
                check(EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)) {
                    eglFailure("Could not bind OpenGL ES")
                }

                var lastFailure = "No compatible EGL window configuration was found."
                for (major in versions) {
                    val renderableBit = if (major == 3) {
                        EGL_OPENGL_ES3_BIT_KHR
                    } else {
                        EGL14.EGL_OPENGL_ES2_BIT
                    }
                    val attributes = intArrayOf(
                        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                        EGL14.EGL_RENDERABLE_TYPE, renderableBit,
                        EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_DEPTH_SIZE, 16,
                        EGL14.EGL_STENCIL_SIZE, 8,
                        EGL14.EGL_NONE
                    )
                    val configs = arrayOfNulls<EGLConfig>(1)
                    val count = IntArray(1)
                    if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) ||
                        count[0] == 0 || configs[0] == null
                    ) {
                        lastFailure = "No EGL window config for OpenGL ES $major: " + eglError()
                        continue
                    }

                    createdSurface = EGL14.eglCreateWindowSurface(
                        display, configs[0], surface, null, 0
                    )
                    if (createdSurface == EGL14.EGL_NO_SURFACE) {
                        lastFailure = "Could not create ES $major window surface: " + eglError()
                        continue
                    }

                    createdContext = EGL14.eglCreateContext(
                        display,
                        configs[0],
                        EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL_CONTEXT_CLIENT_VERSION, major, EGL14.EGL_NONE),
                        0
                    )
                    if (createdContext == EGL14.EGL_NO_CONTEXT) {
                        lastFailure = "Could not create ES $major context: " + eglError()
                        EGL14.eglDestroySurface(display, createdSurface)
                        createdSurface = EGL14.EGL_NO_SURFACE
                        continue
                    }

                    if (!EGL14.eglMakeCurrent(display, createdSurface, createdSurface, createdContext)) {
                        lastFailure = "Could not activate ES $major context: " + eglError()
                        EGL14.eglDestroyContext(display, createdContext)
                        EGL14.eglDestroySurface(display, createdSurface)
                        createdContext = EGL14.EGL_NO_CONTEXT
                        createdSurface = EGL14.EGL_NO_SURFACE
                        continue
                    }

                    val glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
                    val vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
                    val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
                    if (glVersion.isBlank() || renderer.isBlank()) {
                        lastFailure = "ES $major context returned incomplete driver information."
                        EGL14.eglMakeCurrent(
                            display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                        )
                        EGL14.eglDestroyContext(display, createdContext)
                        EGL14.eglDestroySurface(display, createdSurface)
                        createdContext = EGL14.EGL_NO_CONTEXT
                        createdSurface = EGL14.EGL_NO_SURFACE
                        continue
                    }

                    return OEngineEglSession(
                        display = display,
                        eglSurface = createdSurface,
                        context = createdContext,
                        eglVersion = EGL14.eglQueryString(display, EGL14.EGL_VERSION).orEmpty(),
                        glVersion = glVersion,
                        glVendor = vendor,
                        glRenderer = renderer,
                        openGlesMajor = major,
                        ownerThreadId = Thread.currentThread().id
                    )
                }
                throw IllegalStateException(lastFailure)
            } catch (failure: Throwable) {
                if (createdContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, createdContext)
                if (createdSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, createdSurface)
                EGL14.eglTerminate(display)
                EGL14.eglReleaseThread()
                throw failure
            }
        }

        private fun eglError(): String = "EGL error 0x" + EGL14.eglGetError().toString(16)
        private fun eglFailure(message: String): String = message + " (" + eglError() + ")"
    }
}
