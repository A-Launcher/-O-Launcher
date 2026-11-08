package com.alauncher.olauncher

import android.os.Bundle
import android.widget.Toast
import git.artdeell.mojo.ExitActivity
import git.artdeell.mojo.awt.AWTActivity
import git.artdeell.mojo.utils.lifecycle.ContextExecutor

/**
 * Process boundary adapter: bind the selected instance before Mojo's AWT activity
 * executes BaseActivity.onCreate(), Tools initialization, or MultiRTUtils discovery.
 */
class OEngineMojoAwtActivity : AWTActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        var profileKey: String? = null
        try {
            profileKey = intent.getStringExtra("oengine.profileKey")
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
            ExitActivity.setPendingFailureMessage(null)
            OEngineMojoAwtInstallerHandoff.bindRuntimePaths(request, this)
        } catch (failure: Throwable) {
            val message = failure.message ?: "Could not initialize the Forge/NeoForge installer runtime."
            if (profileKey != null) {
                ExitActivity.setPendingProfileKey(profileKey)
                ExitActivity.setPendingFailureMessage(message)
                ExitActivity.showExitMessage(this, -1, false)
            } else {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                finish()
            }
            return
        }
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        ContextExecutor.setActivity(this)
    }

    override fun onPause() {
        ContextExecutor.clearActivity()
        super.onPause()
    }
}
