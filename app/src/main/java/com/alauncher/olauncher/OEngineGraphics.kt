package com.alauncher.olauncher

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OEngineGraphicsProbe(
    val eglVersion: String,
    val glVersion: String,
    val glVendor: String,
    val glRenderer: String,
    val glExtensions: Set<String>,
    val openGlesMajor: Int
)

/**
 * Owns the first Android-native graphics capability check for O Engine.
 * It creates a short-lived off-screen EGL context and releases every EGL object.
 * This is a capability probe, not a desktop OpenGL/LWJGL compatibility implementation.
 */
object OEngineGraphics {
    private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040
    private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

    suspend fun probe(): OEngineGraphicsProbe = withContext(Dispatchers.Default) {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { eglFailure("Could not acquire the default EGL display") }

        val majorMinor = IntArray(2)
        check(EGL14.eglInitialize(display, majorMinor, 0, majorMinor, 1)) {
            eglFailure("Could not initialize EGL")
        }

        try {
            check(EGL14.eglBindAPI(EGL14.EGL_OPENGL_ES_API)) {
                eglFailure("Could not bind the OpenGL ES API")
            }
            var lastFailure = "No compatible EGL configuration was found."
            for (version in listOf(3, 2)) {
                val renderableBit = if (version == 3) EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT
                val configAttributes = intArrayOf(
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RENDERABLE_TYPE, renderableBit,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val count = IntArray(1)
                if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, count, 0) ||
                    count[0] == 0 || configs[0] == null
                ) {
                    lastFailure = "OpenGL ES $version EGL configuration unavailable: " + eglError()
                    continue
                }

                val config = configs[0]!!
                val surface = EGL14.eglCreatePbufferSurface(
                    display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
                )
                if (surface == EGL14.EGL_NO_SURFACE) {
                    lastFailure = "Could not create the EGL pbuffer for ES $version: " + eglError()
                    continue
                }
                var context: EGLContext = EGL14.EGL_NO_CONTEXT
                try {
                    context = EGL14.eglCreateContext(
                        display,
                        config,
                        EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL_CONTEXT_CLIENT_VERSION, version, EGL14.EGL_NONE),
                        0
                    )
                    if (context == EGL14.EGL_NO_CONTEXT) {
                        lastFailure = "Could not create OpenGL ES $version context: " + eglError()
                        continue
                    }
                    if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                        lastFailure = "Could not activate OpenGL ES $version context: " + eglError()
                        continue
                    }
                    val glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
                    val vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
                    val renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
                    val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
                        .orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
                    check(glVersion.isNotBlank() && renderer.isNotBlank()) {
                        "The OpenGL ES context was created but returned incomplete driver information."
                    }
                    return@withContext OEngineGraphicsProbe(
                        eglVersion = EGL14.eglQueryString(display, EGL14.EGL_VERSION).orEmpty(),
                        glVersion = glVersion,
                        glVendor = vendor,
                        glRenderer = renderer,
                        glExtensions = extensions,
                        openGlesMajor = version
                    )
                } finally {
                    EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                    if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglDestroySurface(display, surface)
                }
            }
            throw IllegalStateException(lastFailure)
        } finally {
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
        }
    }

    private fun eglError(): String = "EGL error 0x" + EGL14.eglGetError().toString(16)

    private fun eglFailure(message: String): String = message + " (" + eglError() + ")"
}
