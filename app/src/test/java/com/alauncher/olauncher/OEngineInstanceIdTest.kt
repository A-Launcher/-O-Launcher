package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class OEngineInstanceIdTest {
    @Test fun sanitizesInstanceNamesIntoStableFolderIds() {
        fun id(name: String) = name.trim().replace(Regex("[^A-Za-z0-9 _.-]"), "")
            .replace(Regex("\\s+"), " ").take(40).lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "instance" }
        assertEquals("my-survival-world", id("My Survival World"))
        assertEquals("modpack-1-20-1", id("Modpack 1.20.1"))
    }
}
