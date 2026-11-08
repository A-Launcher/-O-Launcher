package com.alauncher.olauncher

import java.time.Instant

/**
 * GL4ES compatibility profile for old Minecraft clients.
 *
 * MojoLauncher selects the OpenGL 1.x profile for versions predating 2011-06-08
 * and the OpenGL 2.x profile for newer versions. GL4ES exposes these through
 * LIBGL_GL=15 and LIBGL_GL=21 respectively when its GLES2 backend is used.
 */
object OEngineLegacyOpenGlPolicy {
    private val legacyCutoff = Instant.parse("2011-06-08T00:00:00Z")

    data class Profile(
        val exposedOpenGlMajor: Int,
        val gl4esVersion: String,
        val reason: String
    )

    fun resolve(releaseTime: String?): Profile {
        val releasedAt = releaseTime?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return if (releasedAt != null && releasedAt.isBefore(legacyCutoff)) {
            Profile(
                exposedOpenGlMajor = 1,
                gl4esVersion = "15",
                reason = "Minecraft predates 2011-06-08; expose the legacy OpenGL 1.5 compatibility profile."
            )
        } else {
            Profile(
                exposedOpenGlMajor = 2,
                gl4esVersion = "21",
                reason = if (releasedAt == null) {
                    "Release time is unavailable or unparseable; use GL4ES OpenGL 2.1 defaults."
                } else {
                    "Minecraft is from 2011-06-08 or later; expose the OpenGL 2.1 compatibility profile."
                }
            )
        }
    }
}
