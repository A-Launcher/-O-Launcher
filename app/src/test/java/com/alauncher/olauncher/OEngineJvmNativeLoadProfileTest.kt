package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OEngineJvmNativeLoadProfileTest {
    @Test
    fun java8UsesClassLoaderNativeLibraryHook() {
        val profile = OEngineJvmNativeLoadProfile.forJavaMajor(8)
        assertEquals("java/lang/ClassLoader\$NativeLibrary", profile?.methods?.single()?.ownerClass)
        assertEquals("(Ljava/lang/String;Z)V", profile?.methods?.single()?.descriptor)
    }

    @Test
    fun java17TriesBothKnownInternalSignatures() {
        val profile = OEngineJvmNativeLoadProfile.forJavaMajor(17)
        assertEquals(2, profile?.methods?.size)
        assertTrue(profile!!.methods.all { it.ownerClass == "jdk/internal/loader/NativeLibraries" })
        assertTrue(profile.methods[0].descriptor.endsWith("ZZZ)Z"))
        assertTrue(profile.methods[1].descriptor.endsWith("ZZ)Z"))
    }

    @Test
    fun java21UsesCurrentNativeLibrariesSignature() {
        val profile = OEngineJvmNativeLoadProfile.forJavaMajor(21)
        assertEquals(1, profile?.methods?.size)
        assertTrue(profile!!.methods.single().descriptor.endsWith("ZZ)Z"))
    }

    @Test
    fun unknownJavaMajorFailsClosed() {
        assertNull(OEngineJvmNativeLoadProfile.forJavaMajor(25))
    }
}
