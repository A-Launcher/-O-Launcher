package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftLaunchMemoryTest {
    @Test fun memorySettingOverridesMetadataHeapArguments() {
        val arguments = mutableListOf("-Xms256M", "-Xmx4G", "-Dfile.encoding=UTF-8")
        MinecraftVersionInstaller.applyMemoryArguments(arguments, 3072)
        assertEquals(listOf("-Dfile.encoding=UTF-8", "-Xms512M", "-Xmx3072M"), arguments)
    }

    @Test fun memorySettingRejectsValuesOutsideSupportedRange() {
        listOf(511, 8193).forEach { value ->
            try {
                MinecraftVersionInstaller.applyMemoryArguments(mutableListOf(), value)
                throw AssertionError("Expected invalid memory value to be rejected.")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test fun memoryArgumentsDoNotRemoveOtherJvmOptions() {
        val arguments = mutableListOf("-Djava.library.path=/data/lib", "-XX:+UseG1GC")
        MinecraftVersionInstaller.applyMemoryArguments(arguments, 2048)
        assertTrue(arguments.contains("-Djava.library.path=/data/lib"))
        assertTrue(arguments.contains("-XX:+UseG1GC"))
        assertFalse(arguments.any { it.startsWith("-Xmx") && it != "-Xmx2048M" })
    }
}
