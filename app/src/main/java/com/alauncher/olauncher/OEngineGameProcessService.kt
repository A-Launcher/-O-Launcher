package com.alauncher.olauncher

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.system.Os
import java.io.File

/**
 * IPC endpoint hosted in the isolated :game process.
 *
 * The service owns runtime linker diagnostics and the experimental HotSpot VM smoke test.
 * All JNI_CreateJavaVM work runs on its dedicated HandlerThread, away from the launcher UI process.
 */
class OEngineGameProcessService : Service() {
    private var workerThread: HandlerThread? = null
    @Volatile private var jvmCreationAttempted = false
    @Volatile private var jvmCreated = false
    @Volatile private var rendererHandle: Long = 0L
    @Volatile private var rendererHookInstalled = false
    private val messenger by lazy {
        val thread = HandlerThread("OEngineGameProcess").apply { start() }
        workerThread = thread
        Messenger(Handler(thread.looper) { message ->
            when (message.what) {
                MSG_HEALTH_CHECK -> {
                    val reply = Message.obtain(null, MSG_HEALTH_CHECK_RESULT).apply {
                        data.putBoolean(KEY_READY, true)
                        data.putInt(KEY_PID, Process.myPid())
                        data.putString(KEY_PROCESS, packageName + ":game")
                        data.putString(KEY_PHASE, "process_ready")
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_PREPARE_RENDERER -> {
                    val reply = Message.obtain(null, MSG_PREPARE_RENDERER_RESULT)
                    try {
                        check(!jvmCreationAttempted) { "Renderer and driver environment must be prepared before JVM creation." }
                        val rendererFile = File(message.data.getString(KEY_RENDERER_FILE).orEmpty())
                        val rendererRoot = File(message.data.getString(KEY_RENDERER_ROOT).orEmpty())
                        val eglPath = message.data.getString(KEY_RENDERER_EGL).orEmpty()
                        val glesPath = message.data.getString(KEY_RENDERER_GLES).orEmpty()
                        fun applyDriverOverride(key: String, value: String) {
                            if (value.isBlank()) return
                            require(value == "libEGL.so" && key == "LIBGL_EGL" ||
                                value == "libGLESv2.so" && key == "LIBGL_GLES" ||
                                (File(value).isAbsolute && File(value).isFile && File(value).length() > 0L)) {
                                "Invalid $key renderer driver override."
                            }
                            Os.setenv(key, value, true)
                        }
                        applyDriverOverride("LIBGL_EGL", eglPath)
                        applyDriverOverride("LIBGL_GLES", glesPath)
                        rendererHandle = OEngineJvmNativeBridge.preloadRenderer(rendererFile, rendererRoot)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, rendererHandle != 0L)
                        reply.data.putString(KEY_PROBE_STATUS, "renderer_preloaded")
                        reply.data.putString(KEY_PROBE_DETAIL, "Renderer retained in :game before HotSpot creation; LWJGL hook will be installed after VM creation.")
                    } catch (error: Throwable) {
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "renderer_preload_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_INSTALL_RENDERER_HOOK -> {
                    val reply = Message.obtain(null, MSG_INSTALL_RENDERER_HOOK_RESULT)
                    try {
                        check(jvmCreated) { "HotSpot must be created before installing a renderer hook." }
                        check(rendererHandle != 0L) { "Renderer must be preloaded before JVM creation." }
                        val targetClass = message.data.getString(KEY_LWJGL_TARGET_CLASS).orEmpty()
                        val alias = message.data.getString(KEY_RENDERER_ALIAS).orEmpty()
                        val installed = OEngineJvmNativeBridge.installLwjglHook(
                            targetClass, alias, rendererHandle
                        )
                        check(installed) { "HotSpot rejected the LWJGL native-loader hook." }
                        rendererHookInstalled = true
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, true)
                        reply.data.putString(KEY_PROBE_STATUS, "renderer_hook_installed")
                        reply.data.putString(KEY_PROBE_DETAIL, "Renderer loaded and LWJGL interception registered in :game.")
                    } catch (error: Throwable) {
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "renderer_hook_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_INVOKE_MAIN -> {
                    val reply = Message.obtain(null, MSG_INVOKE_MAIN_RESULT)
                    try {
                        val mainClass = message.data.getString(KEY_MAIN_CLASS).orEmpty()
                        val arguments = message.data.getStringArray(KEY_GAME_ARGUMENTS)?.toList().orEmpty()
                        require(mainClass.isNotBlank()) { "Minecraft main class is empty." }
                        check(jvmCreated) { "HotSpot has not been created successfully in the isolated game process." }
                        check(rendererHookInstalled) { "LTW must be preloaded and its LWJGL hook installed before Minecraft main can run." }
                        require(arguments.size <= 16_384 && arguments.all { it.length <= 65_536 && '\u0000' !in it }) {
                            "Minecraft game arguments exceed the IPC safety limits."
                        }
                        val result = OEngineJvmNativeBridge.invokeMain(mainClass, arguments)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, result.successful)
                        reply.data.putString(KEY_PROBE_STATUS, result.status)
                        reply.data.putString(KEY_PROBE_DETAIL, result.detail)
                    } catch (error: Throwable) {
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "main_invoke_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_CREATE_JVM -> {
                    val reply = Message.obtain(null, MSG_CREATE_JVM_RESULT)
                    try {
                        val runtimeHome = File(message.data.getString(KEY_RUNTIME_HOME).orEmpty()).canonicalFile
                        val workingDirectory = File(message.data.getString(KEY_WORKING_DIRECTORY).orEmpty()).canonicalFile
                        val libraryPath = File(message.data.getString(KEY_JVM_LIBRARY).orEmpty()).canonicalFile
                        val options = message.data.getStringArray(KEY_VM_OPTIONS)?.toList().orEmpty()
                        val javaMajor = message.data.getInt(KEY_JAVA_MAJOR)
                        require(runtimeHome.isDirectory) { "Selected Java runtime directory is missing." }
                        require(workingDirectory.isDirectory) { "Selected game working directory is missing." }
                        require(libraryPath.name == "libjvm.so" && libraryPath.toPath().startsWith(runtimeHome.toPath())) {
                            "The JVM library must be inside the selected runtime directory."
                        }
                        require(options.isNotEmpty() && options.size <= 512) { "JVM options are empty or exceed the safety limit." }
                        check(rendererHandle != 0L) { "LTW must be preloaded before HotSpot creation." }
                        require(options.contains("-Dorg.lwjgl.opengl.libname=${OEngineGraphicsStack.LTW_LWJGL_LIBRARY_ALIAS}")) {
                            "The JVM must use the libGLMojo.so LWJGL alias for the staged LTW renderer."
                        }
                        require(javaMajor in setOf(8, 17, 21)) { "Java $javaMajor has no verified native-library hook profile." }
                        val nativeDir = File(applicationInfo.nativeLibraryDir).canonicalFile
                        val searchPaths = listOf(
                            File(runtimeHome, "lib"),
                            File(runtimeHome, "lib/server"),
                            File(runtimeHome, "lib/jli"),
                            nativeDir
                        ).filter { it.isDirectory }.map { it.absolutePath }
                        val existing = System.getenv("LD_LIBRARY_PATH").orEmpty()
                        val ldPath = (searchPaths + existing.split(File.pathSeparator).filter { it.isNotBlank() })
                            .distinct().joinToString(File.pathSeparator)
                        Os.setenv("JAVA_HOME", runtimeHome.absolutePath, true)
                        Os.setenv("HOME", workingDirectory.absolutePath, true)
                        Os.setenv("TMPDIR", File(workingDirectory, "tmp").apply { mkdirs() }.absolutePath, true)
                        Os.setenv("LD_LIBRARY_PATH", ldPath, true)
                        val probe = OEngineJvmNativeBridge.probeLibrary(libraryPath.absolutePath, runtimeHome)
                        require(probe.successful) { probe.status + ": " + probe.detail }
                        jvmCreationAttempted = true
                        val result = OEngineJvmNativeBridge.createJvm(options, javaMajor)
                        jvmCreated = result.successful
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, result.successful)
                        reply.data.putString(KEY_PROBE_STATUS, result.status)
                        reply.data.putString(KEY_PROBE_DETAIL, result.detail)
                    } catch (error: LinkageError) {
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "native_link_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    } catch (error: Exception) {
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "create_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                MSG_PROBE_JVM_LIBRARY -> {
                    val reply = Message.obtain(null, MSG_PROBE_JVM_LIBRARY_RESULT)
                    try {
                        val runtimeHome = File(message.data.getString(KEY_RUNTIME_HOME).orEmpty()).canonicalFile
                        val workingDirectory = File(message.data.getString(KEY_WORKING_DIRECTORY).orEmpty()).canonicalFile
                        val libraryPath = File(message.data.getString(KEY_JVM_LIBRARY).orEmpty()).canonicalFile
                        require(runtimeHome.isDirectory) { "Selected Java runtime directory is missing." }
                        require(workingDirectory.isDirectory) { "Selected game working directory is missing." }
                        require(libraryPath.name == "libjvm.so" && libraryPath.toPath().startsWith(runtimeHome.toPath())) {
                            "The JVM library must be inside the selected runtime directory."
                        }
                        val nativeDir = File(applicationInfo.nativeLibraryDir).canonicalFile
                        val searchPaths = listOf(
                            File(runtimeHome, "lib"),
                            File(runtimeHome, "lib/server"),
                            File(runtimeHome, "lib/jli"),
                            nativeDir
                        ).filter { it.isDirectory }.map { it.absolutePath }
                        val existing = System.getenv("LD_LIBRARY_PATH").orEmpty()
                        val ldPath = (searchPaths + existing.split(File.pathSeparator).filter { it.isNotBlank() })
                            .distinct().joinToString(File.pathSeparator)
                        Os.setenv("JAVA_HOME", runtimeHome.absolutePath, true)
                        Os.setenv("HOME", workingDirectory.absolutePath, true)
                        Os.setenv("TMPDIR", File(workingDirectory, "tmp").apply { mkdirs() }.absolutePath, true)
                        Os.setenv("LD_LIBRARY_PATH", ldPath, true)
                        val probe = OEngineJvmNativeBridge.probeLibrary(libraryPath.absolutePath, runtimeHome)
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, probe.successful)
                        reply.data.putString(KEY_PROBE_STATUS, probe.status)
                        reply.data.putString(KEY_PROBE_DETAIL, probe.detail)
                    } catch (error: Exception) {
                        reply.data.putBoolean(KEY_PROBE_SUCCESS, false)
                        reply.data.putString(KEY_PROBE_STATUS, "probe_error")
                        reply.data.putString(KEY_PROBE_DETAIL, error.message ?: error.javaClass.simpleName)
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                else -> false
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        // JNI requires the VM-creating thread to remain attached until we implement a
        // coordinated DetachCurrentThread shutdown path.
        if (!jvmCreationAttempted) {
            workerThread?.quitSafely()
            workerThread = null
        }
        super.onDestroy()
    }

    companion object {
        const val MSG_HEALTH_CHECK = 1
        const val MSG_HEALTH_CHECK_RESULT = 2
        const val MSG_PROBE_JVM_LIBRARY = 3
        const val MSG_PROBE_JVM_LIBRARY_RESULT = 4
        const val MSG_CREATE_JVM = 5
        const val MSG_CREATE_JVM_RESULT = 6
        const val MSG_INVOKE_MAIN = 7
        const val MSG_INVOKE_MAIN_RESULT = 8
        const val MSG_PREPARE_RENDERER = 11
        const val MSG_PREPARE_RENDERER_RESULT = 12
        const val MSG_INSTALL_RENDERER_HOOK = 9
        const val MSG_INSTALL_RENDERER_HOOK_RESULT = 10
        const val KEY_VM_OPTIONS = "oengine.vm_options"
        const val KEY_JAVA_MAJOR = "oengine.java_major"
        const val KEY_MAIN_CLASS = "oengine.main_class"
        const val KEY_GAME_ARGUMENTS = "oengine.game_arguments"
        const val KEY_RENDERER_FILE = "oengine.renderer_file"
        const val KEY_RENDERER_ROOT = "oengine.renderer_root"
        const val KEY_LWJGL_TARGET_CLASS = "oengine.lwjgl_target_class"
        const val KEY_RENDERER_ALIAS = "oengine.renderer_alias"
        const val KEY_RENDERER_EGL = "oengine.renderer_egl"
        const val KEY_RENDERER_GLES = "oengine.renderer_gles"
        const val KEY_RUNTIME_HOME = "oengine.runtime_home"
        const val KEY_WORKING_DIRECTORY = "oengine.working_directory"
        const val KEY_JVM_LIBRARY = "oengine.jvm_library"
        const val KEY_PROBE_SUCCESS = "oengine.probe_success"
        const val KEY_PROBE_STATUS = "oengine.probe_status"
        const val KEY_PROBE_DETAIL = "oengine.probe_detail"
        const val KEY_READY = "oengine.ready"
        const val KEY_PID = "oengine.pid"
        const val KEY_PROCESS = "oengine.process"
        const val KEY_PHASE = "oengine.phase"
    }
}
