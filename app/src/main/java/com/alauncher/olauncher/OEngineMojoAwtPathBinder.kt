package com.alauncher.olauncher

import java.io.File
import java.lang.reflect.Modifier

/**
 * Binds the pinned Mojo Tools static path fields to one O Launcher instance.
 *
 * This must be called from inside Mojo's :gui_installer process, after MojoApplication
 * initializes its storage fields and before MultiRTUtils is first initialized. Calling
 * it from O Launcher's main process cannot affect Mojo's isolated process.
 *
 * The class name and field names intentionally match the pinned MojoLauncher revision.
 */
object OEngineMojoAwtPathBinder {
    private const val TOOLS_CLASS = "git.artdeell.mojo.Tools"

    fun bind(instanceDirectory: File, classLoader: ClassLoader, nativeLibraryDirectory: File): OEngineMojoAwtRuntimeLayout.Paths {
        val paths = OEngineMojoAwtRuntimeLayout.paths(instanceDirectory)
        listOf(
            paths.accountHome, paths.data, paths.cache, paths.runtimes,
            paths.nativeLibraries, paths.cacioJava8, paths.cacioJava17,
            File(paths.gameHome, "versions"), File(paths.gameHome, "libraries"),
            File(paths.gameHome, "assets"), File(paths.gameHome, "crash-reports"),
            File(paths.gameHome, "resources"), File(paths.gameHome, "controlmap")
        ).forEach { directory ->
            require(directory.isDirectory || directory.mkdirs()) {
                "Could not create Mojo runtime directory: ${directory.name}"
            }
        }

        val nativeRoot = nativeLibraryDirectory.canonicalFile
        require(nativeRoot.isDirectory) { "Android native library directory is unavailable." }
        val tools = Class.forName(TOOLS_CLASS, false, classLoader)
        bindToolsPaths(paths, tools, nativeRoot)
        return paths
    }

    internal fun bindToolsPaths(paths: OEngineMojoAwtRuntimeLayout.Paths, tools: Class<*>, nativeLibraryDirectory: File) {
        setString(tools, "DIR_GAME_HOME", paths.gameHome.absolutePath)
        setString(tools, "DIR_GAME_NEW", paths.gameNew.absolutePath)
        setString(tools, "DIR_ACCOUNT_NEW", paths.accountHome.absolutePath)
        setString(tools, "DIR_DATA", paths.data.absolutePath)
        setFile(tools, "DIR_CACHE", paths.cache)
        setString(tools, "MULTIRT_HOME", paths.runtimes.absolutePath)
        setString(tools, "NATIVE_LIB_DIR", nativeLibraryDirectory.absolutePath)
        setString(tools, "DIR_HOME_VERSION", File(paths.gameHome, "versions").absolutePath)
        setString(tools, "DIR_HOME_LIBRARY", File(paths.gameHome, "libraries").absolutePath)
        setString(tools, "DIR_HOME_CRASH", File(paths.gameHome, "crash-reports").absolutePath)
        setString(tools, "ASSETS_PATH", File(paths.gameHome, "assets").absolutePath)
        setString(tools, "OBSOLETE_RESOURCES_PATH", File(paths.gameHome, "resources").absolutePath)
        setString(tools, "CTRLMAP_PATH", File(paths.gameHome, "controlmap").absolutePath)
        setString(tools, "CTRLDEF_FILE", File(paths.gameHome, "controlmap/default.json").absolutePath)
    }

    private fun setString(owner: Class<*>, name: String, value: String) {
        val field = owner.getField(name)
        require(field.type == String::class.java && Modifier.isStatic(field.modifiers)) {
            "Pinned Mojo Tools field $name has an unexpected type."
        }
        field.set(null, value)
    }

    private fun setFile(owner: Class<*>, name: String, value: File) {
        val field = owner.getField(name)
        require(field.type == File::class.java && Modifier.isStatic(field.modifiers)) {
            "Pinned Mojo Tools field $name has an unexpected type."
        }
        field.set(null, value)
    }
}
