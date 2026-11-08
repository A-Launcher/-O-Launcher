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

    @Test fun bindsEveryMojoPathToTheSelectedInstance() {
        val root = Files.createTempDirectory("oengine-mojo-binding").toFile()
        try {
            val paths = OEngineMojoAwtRuntimeLayout.paths(root)
            // Exercise the same reflective field contract without loading Mojo classes into
            // the unit-test JVM. Production passes the pinned Mojo Tools class loader.
            val tools = FakeTools::class.java
            fun string(name: String, value: String) {
                tools.getField(name).set(null, value)
            }
            string("DIR_GAME_HOME", paths.gameHome.absolutePath)
            string("DIR_GAME_NEW", paths.gameNew.absolutePath)
            string("DIR_ACCOUNT_NEW", paths.accountHome.absolutePath)
            string("DIR_DATA", paths.data.absolutePath)
            string("MULTIRT_HOME", paths.runtimes.absolutePath)
            string("NATIVE_LIB_DIR", paths.nativeLibraries.absolutePath)
            assertEquals(root.canonicalPath, FakeTools.DIR_GAME_HOME)
            assertEquals(root.canonicalPath, FakeTools.DIR_GAME_NEW)
            assertEquals(paths.runtimes.absolutePath, FakeTools.MULTIRT_HOME)
            assertEquals(paths.nativeLibraries.absolutePath, FakeTools.NATIVE_LIB_DIR)
        } finally {
            root.deleteRecursively()
        }
    }
}
