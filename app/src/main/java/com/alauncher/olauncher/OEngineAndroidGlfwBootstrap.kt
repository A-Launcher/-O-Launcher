package com.alauncher.olauncher

import android.content.Context
import android.view.Surface
import git.artdeell.dnbootstrap.glfw.GLFW
import git.artdeell.mojoexec.MojoExec
import java.io.File

/**
 * Initializes Mojo's Android platform JNI on ART, where Android Surface objects live.
 * The LWJGL GLFW API itself is resolved separately by embedded HotSpot from the same
 * staged libglfw.so through org.lwjgl.glfw.libname.
 */
internal object OEngineAndroidGlfwBootstrap {
    @Volatile private var loadedPath: String? = null
    @Volatile private var surfaceIsAttached: Boolean = false

    @Synchronized
    fun initialize(context: Context, library: File) {
        val canonical = library.canonicalFile
        require(canonical.name == "libglfw.so" && canonical.isFile && canonical.length() > 0L) {
            "Mojo Android GLFW library is missing or empty."
        }
        val path = canonical.absolutePath
        if (loadedPath == path) return
        check(loadedPath == null) { "A different Android GLFW library is already loaded in this process." }
        System.load(path)
        val abi = OEngineJvmNativeBridge.verifyAndroidGlfwLibrary(path)
        check(abi.successful) {
            "Android GLFW cannot satisfy the selected LWJGL native ABI: " +
                abi.status + ": " + abi.detail
        }
        // Mojo's Android GLFW backend calls _glfwLoadEglAndroid() through
        // MojoExec. Loading libglfw alone is insufficient: prepareEgl stores the
        // exact EGL handle and enables its Android GLES context configuration.
        MojoExec.setNativeLibraryDir(context.applicationInfo.nativeLibraryDir)
        check(MojoExec.prepareEgl("libEGL.so", false, true, OEngineGraphicsStack.LTW_MIN_GLES_MAJOR)) {
            "MojoExec could not preload Android libEGL.so for the GLFW platform backend."
        }
        GLFW.initializeOnce(context)
        loadedPath = path
    }

    fun surfaceCreated(surface: Surface) {
        check(loadedPath != null) { "Android GLFW JNI must be initialized before attaching a Surface." }
        require(surface.isValid) { "Cannot pass an invalid Android Surface to GLFW." }
        if (!surfaceIsAttached) {
            GLFW.nativeSurfaceCreated(surface)
            surfaceIsAttached = true
        }
        GLFW.nativeSurfaceUpdated()
    }

    fun surfaceUpdated() {
        check(loadedPath != null) { "Android GLFW JNI is not initialized." }
        GLFW.nativeSurfaceUpdated()
    }

    @Synchronized
    fun surfaceDestroyed() {
        if (loadedPath != null && surfaceIsAttached) {
            GLFW.nativeSurfaceDestroyed()
            surfaceIsAttached = false
        }
    }
}
