package com.alauncher.olauncher

import android.os.Bundle
import android.widget.Toast
import git.artdeell.mojo.ExitActivity
import git.artdeell.mojo.awt.AWTActivity

/**
 * Process boundary adapter: bind the selected instance before Mojo's AWT activity
 * executes BaseActivity.onCreate(), Tools initialization, or MultiRTUtils discovery.
 */
class OEngineMojoAwtActivity : AWTActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            val profileKey = intent.getStringExtra("oengine.profileKey")
                ?: error("Installer request is missing its profile key.")
            val preferences = getSharedPreferences("o_launcher_profiles", MODE_PRIVATE)
            val request = OEngineMojoAwtInstallerHandoff.readPending(preferences, profileKey)
            require(intent.getStringExtra("oengine.instanceRoot") == request.instanceRoot.canonicalPath) {
                "Installer request instance does not match the saved instance."
            }
            require(intent.getStringExtra("oengine.loader") == request.loader &&
                intent.getStringExtra("oengine.minecraftVersion") == request.minecraftVersion) {
                "Installer request metadata does not match the saved request."
            }
            ExitActivity.setPendingProfileKey(profileKey)
            OEngineMojoAwtInstallerHandoff.bindRuntimePaths(request, this)
        } catch (failure: Throwable) {
            Toast.makeText(
                this,
                failure.message ?: "Could not initialize the Forge/NeoForge installer runtime.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }
}
