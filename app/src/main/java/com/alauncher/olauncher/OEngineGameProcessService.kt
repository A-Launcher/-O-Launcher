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
        const val KEY_VM_OPTIONS = "oengine.vm_options"
        const val KEY_JAVA_MAJOR = "oengine.java_major"
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
