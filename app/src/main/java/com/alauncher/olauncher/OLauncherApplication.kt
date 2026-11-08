package com.alauncher.olauncher

import android.app.Application

/**
 * Keeps Mojo's normal launcher startup out of O Launcher's other processes.
 * The dedicated AWT activity initializes Mojo explicitly after its instance paths
 * have been bound by OEngineMojoAwtInstallerHandoff.
 */
class OLauncherApplication : Application()
