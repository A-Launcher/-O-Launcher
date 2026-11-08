package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OEngineMojoAwtPathBinderTest {
    class FakeTools {
        companion object {
            @JvmField var DIR_GAME_HOME: String = ""
            @JvmField var DIR_GAME_NEW: String = ""
            @JvmField var DIR_ACCOUNT_NEW: String = ""
            @JvmField var DIR_DATA: String = ""
            @JvmField var DIR_CACHE: File = File(".")
            @JvmField var MULTIRT_HOME: String = ""
            @JvmField var NATIVE_LIB_DIR: String = ""
            @JvmField var DIR_HOME_VERSION: String = ""
            @JvmField var DIR_HOME_LIBRARY: String = ""
            @JvmField var DIR_HOME_CRASH: String = ""
            @JvmField var ASSETS_PATH: String = ""
            @JvmField var OBSOLETE_RESOURCES_PATH: String = ""
            @JvmField var CTRLMAP_PATH: String = ""
            @JvmField var CTRLDEF_FILE: String = ""
        }
    }

    @Test fun bindsAllPinnedMojoToolsPathFields() {
        val root = Files.createTempDirectory("oengine-mojo-binding").toFile()
        try {
            val paths = OEngineMojoAwtRuntimeLayout.paths(root)
            val nativeRoot = Files.createTempDirectory("oengine-mojo-native").toFile()
            OEngineMojoAwtPathBinder.bindToolsPaths(paths, FakeTools::class.java, nativeRoot)
            assertEquals(root.canonicalPath, FakeTools.DIR_GAME_HOME)
            assertEquals(root.canonicalPath, FakeTools.DIR_GAME_NEW)
            assertEquals(paths.accountHome.absolutePath, FakeTools.DIR_ACCOUNT_NEW)
            assertEquals(paths.data.absolutePath, FakeTools.DIR_DATA)
            assertEquals(paths.cache, FakeTools.DIR_CACHE)
            assertEquals(paths.runtimes.absolutePath, FakeTools.MULTIRT_HOME)
            assertEquals(nativeRoot.canonicalPath, FakeTools.NATIVE_LIB_DIR)
            nativeRoot.deleteRecursively()
            assertEquals(File(root, "versions").absolutePath, FakeTools.DIR_HOME_VERSION)
            assertEquals(File(root, "libraries").absolutePath, FakeTools.DIR_HOME_LIBRARY)
            assertEquals(File(root, "assets").absolutePath, FakeTools.ASSETS_PATH)
            assertEquals(File(root, "controlmap/default.json").absolutePath, FakeTools.CTRLDEF_FILE)
        } finally {
            root.deleteRecursively()
        }
    }
}
