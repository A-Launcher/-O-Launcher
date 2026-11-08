package com.alauncher.olauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidRuntimeInstallerTest {
    @Test fun pinsJavaEightSeventeenAndTwentyOneForBothArmAbis() {
        listOf(8, 17, 21).forEach { major ->
            assertNotNull("Java $major arm64", AndroidRuntimeInstaller.expectedArchiveSha256(major, "arm64"))
            assertNotNull("Java $major arm32", AndroidRuntimeInstaller.expectedArchiveSha256(major, "arm"))
        }
    }

    @Test fun runtimeDigestsAreSha256HexValues() {
        listOf(8, 17, 21, 25).forEach { major ->
            listOf("arm", "arm64", "x86", "x86_64").forEach { architecture ->
                val digest = AndroidRuntimeInstaller.expectedArchiveSha256(major, architecture)
                if (digest != null) {
                    assertEquals("Java $major $architecture digest length", 64, digest.length)
                    assertEquals("Java $major $architecture digest format", digest.lowercase(), digest)
                    assertEquals(0, digest.count { it !in "0123456789abcdef" })
                }
            }
        }
    }

    @Test fun choosesNearestAvailableRuntimeThatMeetsTheMinecraftMinimum() {
        assertEquals(25, AndroidRuntimeInstaller.selectCompatibleRuntimeMajor(22, listOf(8, 17, 21, 25)))
        assertEquals(26, AndroidRuntimeInstaller.selectCompatibleRuntimeMajor(26, listOf(8, 17, 21, 25, 26, 27)))
        assertEquals(27, AndroidRuntimeInstaller.selectCompatibleRuntimeMajor(26, listOf(8, 17, 21, 25, 27)))
        assertNull(AndroidRuntimeInstaller.selectCompatibleRuntimeMajor(28, listOf(8, 17, 21, 25, 27)))
    }

    @Test fun doesNotAdvertiseUnpublishedJavaTwentyFiveX86Runtime() {
        assertNull(AndroidRuntimeInstaller.expectedArchiveSha256(25, "x86"))
    }
}
