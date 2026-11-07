package com.alauncher.olauncher

import android.view.Surface
import git.artdeell.dnbootstrap.glfw.GLFW
import java.io.File

/**
 * Initializes Mojo's Android platform JNI on ART, where Android Surface objects live.
 * The LWJGL GLFW API itself is resolved separately by embedded HotSpot from the same
 * staged libglfw.so through org.lwjgl.glfw.libname.
 */
internal object OEngineAndroidGlfwBootstrap {
    @Volatile private var loadedPath: String? = null

    @Synchronized
    fun initialize(library: File) {
        val canonical = library.canonicalFile
        require(canonical.name == "libglfw.so" && canonical.isFile && canonical.length() > 0L) {
            "Mojo Android GLFW library is missing or empty."
        }
        val path = canonical.absolutePath
        if (loadedPath == path) return
        check(loadedPath == null) { "A different Android GLFW library is already loaded in this process." }
        System.load(path)
        GLFW.initializeOnce()
        loadedPath = path
    }

    fun surfaceCreated(surface: Surface) {
        check(loadedPath != null) { "Android GLFW JNI must be initialized before attaching a Surface." }
        require(surface.isValid) { "Cannot pass an invalid Android Surface to GLFW." }
        GLFW.nativeSurfaceCreated(surface)
        GLFW.nativeSurfaceUpdated()
    }

    fun surfaceUpdated() {
        check(loadedPath != null) { "Android GLFW JNI is not initialized." }
        GLFW.nativeSurfaceUpdated()
    }

    fun surfaceDestroyed() {
        if (loadedPath != null) GLFW.nativeSurfaceDestroyed()
    }
}
