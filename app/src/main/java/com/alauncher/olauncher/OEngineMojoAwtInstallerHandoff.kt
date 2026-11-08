package com.alauncher.olauncher

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray
import java.io.File

/**
 * Validates the durable Forge/NeoForge request and translates it to Mojo's actual
 * AWTActivity Intent contract. The caller must launch this only after the pinned
 * Mojo AWT runtime has been compiled into the application.
 */
object OEngineMojoAwtInstallerHandoff {
    const val AWT_ACTIVITY_CLASS = "com.alauncher.olauncher.OEngineMojoAwtActivity"
    private const val MAX_ARGS = 32
    private const val MAX_ARG_LENGTH = 4096

    data class Request(
        val profileKey: String,
        val instanceRoot: File,
        val loader: String,
        val minecraftVersion: String,
        val installerJar: File,
        val agentJar: File,
        val javaArgs: List<String>
    )

    fun readPending(preferences: SharedPreferences, profileKey: String): Request {
        require(profileKey.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Invalid launcher profile key." }
        fun required(key: String): String =
            preferences.getString("$key.$profileKey", null)?.takeIf { it.isNotBlank() }
                ?: error("Pending Mojo installer request is missing $key.")

        val rawInstance = File(required("pending_installer_instance")).absoluteFile
        require(!java.nio.file.Files.isSymbolicLink(rawInstance.toPath())) {
            "Selected Minecraft instance must not be a symbolic link."
        }
        val instance = rawInstance.canonicalFile
        require(instance.isDirectory) {
            "Selected Minecraft instance is missing or unsafe."
        }
        val installer = containedFile(instance, required("pending_installer_jar"), "oengine/installers")
        val agent = containedFile(instance, required("pending_installer_agent"), "oengine/installers/agent")
        require(installer.isFile && installer.length() in 1..512L * 1024L * 1024L) {
            "Official loader installer JAR is missing or too large."
        }
        require(OEngineInstallerAgent.isValidAgentJar(agent)) {
            "Mojo installer agent is missing or invalid."
        }
        val loader = required("pending_installer_loader")
        require(loader == "Forge" || loader == "NeoForge") { "Unsupported AWT installer type." }
        val minecraft = required("pending_installer_mc")
        require(minecraft.matches(Regex("""\d+(?:\.\d+){1,2}(?:[-+][A-Za-z0-9._-]+)?"""))) {
            "Pending Minecraft version is invalid."
        }
        val encodedArgs = required("pending_installer_args")
        require(encodedArgs.length <= 64 * 1024) { "Pending installer arguments are too large." }
        val args = parseJavaArgs(encodedArgs, agent.canonicalPath)
        return Request(profileKey, instance, loader, minecraft, installer, agent, args)
    }

    internal fun parseJavaArgs(encoded: String, agentPath: String): List<String> {
        require(encoded.length <= 64 * 1024) { "Pending installer arguments are too large." }
        val array = JSONArray(encoded)
        require(array.length() in 1..MAX_ARGS) { "Pending installer argument count is invalid." }
        val args = (0 until array.length()).map { index ->
            require(!array.isNull(index)) { "Pending installer argument is invalid." }
            val value = array.getString(index)
            require(value.isNotBlank() && value.length <= MAX_ARG_LENGTH &&
                !value.contains('\u0000')) { "Pending installer argument is invalid." }
            value
        }
        require(args.any {
            val target = it.takeIf { value -> value.startsWith("-javaagent:") }?.substringAfter("-javaagent:")
            target == agentPath || target?.startsWith("$agentPath=") == true
        }) {
            "Pending installer arguments do not reference the staged Mojo agent."
        }
        return args
    }

    /** Creates exactly the extras consumed by the pinned Mojo AWTActivity. */
    fun createIntent(context: Context, request: Request): Intent =
        Intent().setComponent(ComponentName(context.packageName, AWT_ACTIVITY_CLASS)).apply {
            putExtra("trusted", false)
            putStringArrayListExtra("javaArgs", ArrayList(request.javaArgs))
            putExtra("modPath", request.installerJar.canonicalPath)
            putExtra("openLogOutput", true)
            putExtra("oengine.profileKey", request.profileKey)
            putExtra("oengine.instanceRoot", request.instanceRoot.canonicalPath)
            putExtra("oengine.loader", request.loader)
            putExtra("oengine.minecraftVersion", request.minecraftVersion)
        }

    /** Called by the AWT process bootstrap before it invokes AWTActivity.onCreate(). */
    fun bindRuntimePaths(request: Request, context: Context): OEngineMojoAwtRuntimeLayout.Paths {
        val paths = OEngineMojoAwtRuntimeLayout.paths(request.instanceRoot)
        migrateLegacyRuntimeLayout(paths)
        initializeMojoPreferences(context, paths)
        OEngineMojoAwtRuntimeProvisioner.prepare(context, paths, 8)
        OEngineMojoAwtRuntimeProvisioner.prepare(context, paths, 17)
        val nativeLibraryDirectory = File(context.applicationInfo.nativeLibraryDir)
        OEngineMojoAwtPathBinder.bind(request.instanceRoot, context.classLoader, nativeLibraryDirectory)
        require(paths.instanceRoot == request.instanceRoot.canonicalFile) {
            "Mojo AWT runtime was bound to a different Minecraft instance."
        }
        return paths
    }

    /**
     * Older O Engine builds stored Java folders in runtimes/installed and archives in
     * runtimes/archives. Mojo snapshots the entire runtimes directory before the path
     * binder runs, so migrate these legacy children out of the scan root first.
     */
    private fun migrateLegacyRuntimeLayout(paths: OEngineMojoAwtRuntimeLayout.Paths) {
        val runtimeRoot = paths.runtimes
        require(!java.nio.file.Files.isSymbolicLink(runtimeRoot.toPath())) {
            "Java runtime root must not be a symbolic link."
        }
        require(runtimeRoot.isDirectory || runtimeRoot.mkdirs()) {
            "Could not create the Java runtime root."
        }

        val legacyStore = File(paths.instanceRoot, "oengine/mojo/legacy-runtimes")
        val archiveStore = File(paths.instanceRoot, "oengine/runtime-archives")

        fun movePreserving(source: File, destination: File) {
            require(!java.nio.file.Files.isSymbolicLink(source.toPath())) {
                "Legacy runtime migration refuses symbolic links."
            }
            val parent = destination.parentFile
                ?: error("Legacy runtime migration destination has no parent.")
            require(parent.isDirectory || parent.mkdirs()) {
                "Could not create a legacy runtime migration directory."
            }
            require(!java.nio.file.Files.isSymbolicLink(parent.toPath())) {
                "Legacy runtime migration destination contains a symbolic link."
            }
            require(destination.canonicalFile.toPath().startsWith(paths.instanceRoot.toPath())) {
                "Legacy runtime migration destination escapes the selected instance."
            }
            java.nio.file.Files.move(source.toPath(), destination.toPath())
        }

        val legacyInstalled = File(runtimeRoot, "installed")
        if (legacyInstalled.exists()) {
            require(!java.nio.file.Files.isSymbolicLink(legacyInstalled.toPath()) && legacyInstalled.isDirectory) {
                "Legacy installed runtimes directory is unsafe."
            }
            val children = legacyInstalled.listFiles()
                ?: error("Could not inspect legacy installed runtimes.")
            children.forEach { source ->
                require(source.name.matches(Regex("[A-Za-z0-9._+-]{1,100}"))) {
                    "Legacy runtime folder name is invalid."
                }
                val directTarget = File(runtimeRoot, source.name)
                val target = if (directTarget.exists()) {
                    File(legacyStore, "installed/${source.name}")
                } else {
                    directTarget
                }
                require(!target.exists()) { "Legacy runtime migration found a duplicate backup folder." }
                movePreserving(source, target)
            }
            if (legacyInstalled.exists()) {
                require(legacyInstalled.delete()) { "Could not finish legacy runtime migration." }
            }
        }

        val legacyArchives = File(runtimeRoot, "archives")
        if (legacyArchives.exists()) {
            require(!java.nio.file.Files.isSymbolicLink(legacyArchives.toPath()) && legacyArchives.isDirectory) {
                "Legacy runtime archive directory is unsafe."
            }
            val children = legacyArchives.listFiles()
                ?: error("Could not inspect legacy runtime archives.")
            children.forEach { source ->
                require(source.isFile && source.name.matches(Regex("[A-Za-z0-9._+-]{1,160}"))) {
                    "Legacy runtime archive entry is invalid."
                }
                val directTarget = File(archiveStore, source.name)
                val target = if (directTarget.exists()) {
                    File(legacyStore, "archives/${source.name}")
                } else {
                    directTarget
                }
                require(!target.exists()) { "Legacy runtime migration found a duplicate archive backup." }
                movePreserving(source, target)
            }
            if (legacyArchives.exists()) {
                require(legacyArchives.delete()) { "Could not finish legacy runtime archive migration." }
            }
        }
    }

    /**
     * Mojo's LauncherPreferences.loadPreferences() initializes MultiRTUtils as a side effect.
     * Give it an instance-scoped Context first so its one-time static runtime-root snapshot
     * points at O Engine's real runtimes directory, not Mojo's default app storage.
     */
    private fun initializeMojoPreferences(
        context: Context,
        paths: OEngineMojoAwtRuntimeLayout.Paths
    ) {
        val instanceContext = object : ContextWrapper(context) {
            private val scopedFiles = File(paths.instanceRoot, "files").apply {
                require(isDirectory || mkdirs()) { "Could not create Mojo instance files directory." }
            }

            override fun getFilesDir(): File = scopedFiles
            override fun getCacheDir(): File = paths.cache.apply {
                require(isDirectory || mkdirs()) { "Could not create Mojo instance cache directory." }
            }
            override fun getExternalFilesDir(type: String?): File = paths.instanceRoot
        }
        val loader = context.classLoader
        val preferenceManager = Class.forName("androidx.preference.PreferenceManager", true, loader)
        val sharedPreferences = preferenceManager
            .getMethod("getDefaultSharedPreferences", Context::class.java)
            .invoke(null, context) as SharedPreferences
        val launcherPreferences = Class.forName(
            "git.artdeell.mojo.prefs.LauncherPreferences", true, loader
        )
        launcherPreferences.getField("DEFAULT_PREF").set(null, sharedPreferences)
        val application = context.applicationContext as? android.app.Application
            ?: error("Mojo AWT bootstrap requires an Android Application context.")
        Class.forName("git.artdeell.mojo.utils.lifecycle.ContextExecutor", true, loader)
            .getMethod("setApplication", android.app.Application::class.java)
            .invoke(null, application)
        val architecture = Class.forName("git.artdeell.mojo.Architecture", true, loader)
            .getMethod("getDeviceArchitecture")
            .invoke(null) as Int
        Class.forName("git.artdeell.mojo.Tools", true, loader)
            .getField("DEVICE_ARCHITECTURE")
            .setInt(null, architecture)
        launcherPreferences.getMethod("loadPreferences", Context::class.java)
            .invoke(null, instanceContext)
    }

    fun complete(request: Request, preferences: SharedPreferences): OEngineInstallerProfileWatcher.InstalledProfile =
        OEngineInstallerProfileWatcher.completePending(request.instanceRoot, preferences, request.profileKey)

    private fun containedFile(root: File, rawPath: String, relativeParent: String): File {
        val raw = File(rawPath).absoluteFile
        val allowed = File(root, relativeParent).canonicalFile
        val candidate = raw.canonicalFile
        require(allowed.toPath().startsWith(root.toPath()) &&
            candidate.toPath().startsWith(allowed.toPath())) {
            "Pending installer file escapes its private staging directory."
        }
        var cursor = raw
        while (cursor.toPath().startsWith(root.toPath())) {
            require(!java.nio.file.Files.isSymbolicLink(cursor.toPath())) {
                "Pending installer path contains a symbolic link."
            }
            if (cursor == root) break
            cursor = cursor.parentFile ?: break
        }
        return candidate
    }
}
