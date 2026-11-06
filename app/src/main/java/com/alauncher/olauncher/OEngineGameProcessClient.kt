package com.alauncher.olauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File

/** Result of the isolated game-process IPC handshake. */
data class OEngineGameProcessStatus(
    val ready: Boolean,
    val pid: Int,
    val processName: String,
    val phase: String
)

/**
 * Binds to the :game process and performs a small IPC handshake.
 * The client owns the binding and must be closed when the game-process session is no longer used.
 */
class OEngineGameProcessClient(
    context: Context,
    private val onStatus: (Result<OEngineGameProcessStatus>) -> Unit,
    private val onJvmProbe: (Result<OEngineJvmLibraryProbe>) -> Unit = {},
    private val onJvmCreate: (Result<OEngineJvmLibraryProbe>) -> Unit = {},
    private val onJvmMain: (Result<OEngineJvmLibraryProbe>) -> Unit = {},
    private val onRendererHook: (Result<OEngineJvmLibraryProbe>) -> Unit = {},
    private val onRendererPreload: (Result<OEngineJvmLibraryProbe>) -> Unit = {}
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val connectStarted = AtomicBoolean(false)
    private val responseDelivered = AtomicBoolean(false)
    private val probeResponseDelivered = AtomicBoolean(false)
    private val createResponseDelivered = AtomicBoolean(false)
    private val rendererResponseDelivered = AtomicBoolean(false)
    private val rendererPreloadResponseDelivered = AtomicBoolean(false)
    private var serviceMessenger: Messenger? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        report(Result.failure(IllegalStateException("The isolated game process did not answer its health check within 5 seconds.")))
        close()
    }
    private val probeTimeoutRunnable = Runnable {
        if (probeResponseDelivered.compareAndSet(false, true)) {
            onJvmProbe(Result.failure(IllegalStateException("The isolated game process did not finish the JVM linker probe within 15 seconds.")))
        }
    }
    private val createTimeoutRunnable = Runnable {
        if (createResponseDelivered.compareAndSet(false, true)) {
            onJvmCreate(Result.failure(IllegalStateException("The isolated game process did not finish JVM creation within 60 seconds.")))
        }
    }

    private fun report(result: Result<OEngineGameProcessStatus>) {
        if (responseDelivered.compareAndSet(false, true)) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            onStatus(result)
        }
    }
    private val rendererPreloadTimeoutRunnable = Runnable {
        if (rendererPreloadResponseDelivered.compareAndSet(false, true)) {
            onRendererPreload(Result.failure(IllegalStateException("The isolated game process did not finish LTW preload within 30 seconds.")))
        }
    }
    private val rendererTimeoutRunnable = Runnable {
        if (rendererResponseDelivered.compareAndSet(false, true)) {
            onRendererHook(Result.failure(IllegalStateException("The isolated game process did not finish renderer hook installation within 30 seconds.")))
        }
    }
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            OEngineGameProcessService.MSG_HEALTH_CHECK_RESULT -> {
                val data = message.data
                val status = OEngineGameProcessStatus(
                    ready = data.getBoolean(OEngineGameProcessService.KEY_READY),
                    pid = data.getInt(OEngineGameProcessService.KEY_PID),
                    processName = data.getString(OEngineGameProcessService.KEY_PROCESS).orEmpty(),
                    phase = data.getString(OEngineGameProcessService.KEY_PHASE).orEmpty()
                )
                report(Result.success(status))
                true
            }
            OEngineGameProcessService.MSG_CREATE_JVM_RESULT -> {
                if (createResponseDelivered.compareAndSet(false, true)) {
                    timeoutHandler.removeCallbacks(createTimeoutRunnable)
                    val data = message.data
                    onJvmCreate(Result.success(OEngineJvmLibraryProbe(
                        successful = data.getBoolean(OEngineGameProcessService.KEY_PROBE_SUCCESS),
                        status = data.getString(OEngineGameProcessService.KEY_PROBE_STATUS).orEmpty(),
                        detail = data.getString(OEngineGameProcessService.KEY_PROBE_DETAIL).orEmpty()
                    )))
                }
                true
            }
            OEngineGameProcessService.MSG_PREPARE_RENDERER_RESULT -> {
                if (rendererPreloadResponseDelivered.compareAndSet(false, true)) {
                    timeoutHandler.removeCallbacks(rendererPreloadTimeoutRunnable)
                    val data = message.data
                    onRendererPreload(Result.success(OEngineJvmLibraryProbe(
                        successful = data.getBoolean(OEngineGameProcessService.KEY_PROBE_SUCCESS),
                        status = data.getString(OEngineGameProcessService.KEY_PROBE_STATUS).orEmpty(),
                        detail = data.getString(OEngineGameProcessService.KEY_PROBE_DETAIL).orEmpty()
                    )))
                }
                true
            }
            OEngineGameProcessService.MSG_INSTALL_RENDERER_HOOK_RESULT -> {
                if (rendererResponseDelivered.compareAndSet(false, true)) {
                    timeoutHandler.removeCallbacks(rendererTimeoutRunnable)
                    val data = message.data
                    onRendererHook(Result.success(OEngineJvmLibraryProbe(
                        successful = data.getBoolean(OEngineGameProcessService.KEY_PROBE_SUCCESS),
                        status = data.getString(OEngineGameProcessService.KEY_PROBE_STATUS).orEmpty(),
                        detail = data.getString(OEngineGameProcessService.KEY_PROBE_DETAIL).orEmpty()
                    )))
                }
                true
            }
            OEngineGameProcessService.MSG_INVOKE_MAIN_RESULT -> {
                val data = message.data
                onJvmMain(Result.success(OEngineJvmLibraryProbe(
                    successful = data.getBoolean(OEngineGameProcessService.KEY_PROBE_SUCCESS),
                    status = data.getString(OEngineGameProcessService.KEY_PROBE_STATUS).orEmpty(),
                    detail = data.getString(OEngineGameProcessService.KEY_PROBE_DETAIL).orEmpty()
                )))
                true
            }
            OEngineGameProcessService.MSG_PROBE_JVM_LIBRARY_RESULT -> {
                if (probeResponseDelivered.compareAndSet(false, true)) {
                    timeoutHandler.removeCallbacks(probeTimeoutRunnable)
                    val data = message.data
                    onJvmProbe(Result.success(OEngineJvmLibraryProbe(
                        successful = data.getBoolean(OEngineGameProcessService.KEY_PROBE_SUCCESS),
                        status = data.getString(OEngineGameProcessService.KEY_PROBE_STATUS).orEmpty(),
                        detail = data.getString(OEngineGameProcessService.KEY_PROBE_DETAIL).orEmpty()
                    )))
                }
                true
            }
            else -> false
        }
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed.get()) return
            serviceMessenger = Messenger(binder)
            val request = Message.obtain(null, OEngineGameProcessService.MSG_HEALTH_CHECK).apply {
                replyTo = replyMessenger
            }
            try {
                serviceMessenger?.send(request)
            } catch (error: RemoteException) {
                report(Result.failure(error))
                close()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) report(Result.failure(IllegalStateException("The isolated game process disconnected.")))
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) report(Result.failure(IllegalStateException("The isolated game-process binding died.")))
            close()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) report(Result.failure(IllegalStateException("The isolated game process returned a null binding.")))
            close()
        }
    }

    fun probeJvmLibrary(runtimeHome: File, workingDirectory: File, jvmLibrary: File): Boolean {
        if (closed.get() || serviceMessenger == null) {
            onJvmProbe(Result.failure(IllegalStateException("The isolated game process is not connected yet.")))
            return false
        }
        probeResponseDelivered.set(false)
        timeoutHandler.postDelayed(probeTimeoutRunnable, 15_000L)
        val request = Message.obtain(null, OEngineGameProcessService.MSG_PROBE_JVM_LIBRARY).apply {
            data.putString(OEngineGameProcessService.KEY_RUNTIME_HOME, runtimeHome.canonicalPath)
            data.putString(OEngineGameProcessService.KEY_WORKING_DIRECTORY, workingDirectory.canonicalPath)
            data.putString(OEngineGameProcessService.KEY_JVM_LIBRARY, jvmLibrary.canonicalPath)
            replyTo = replyMessenger
        }
        return try {
            serviceMessenger?.send(request)
            true
        } catch (error: RemoteException) {
            timeoutHandler.removeCallbacks(probeTimeoutRunnable)
            if (probeResponseDelivered.compareAndSet(false, true)) {
                onJvmProbe(Result.failure(error))
            }
            false
        }
    }

    fun createJvm(runtimeHome: File, workingDirectory: File, jvmLibrary: File, vmOptions: List<String>, javaMajor: Int): Boolean {
        if (closed.get() || serviceMessenger == null) {
            onJvmCreate(Result.failure(IllegalStateException("The isolated game process is not connected yet.")))
            return false
        }
        createResponseDelivered.set(false)
        timeoutHandler.postDelayed(createTimeoutRunnable, 60_000L)
        val request = Message.obtain(null, OEngineGameProcessService.MSG_CREATE_JVM).apply {
            data.putString(OEngineGameProcessService.KEY_RUNTIME_HOME, runtimeHome.canonicalPath)
            data.putString(OEngineGameProcessService.KEY_WORKING_DIRECTORY, workingDirectory.canonicalPath)
            data.putString(OEngineGameProcessService.KEY_JVM_LIBRARY, jvmLibrary.canonicalPath)
            data.putStringArray(OEngineGameProcessService.KEY_VM_OPTIONS, vmOptions.toTypedArray())
            data.putInt(OEngineGameProcessService.KEY_JAVA_MAJOR, javaMajor)
            replyTo = replyMessenger
        }
        return try {
            serviceMessenger?.send(request)
            true
        } catch (error: RemoteException) {
            timeoutHandler.removeCallbacks(createTimeoutRunnable)
            if (createResponseDelivered.compareAndSet(false, true)) onJvmCreate(Result.failure(error))
            false
        }
    }

    /** Prepare renderer driver environment and preload LTW before creating HotSpot. */
    fun prepareRenderer(
        rendererFile: File,
        rendererRoot: File,
        eglLibraryPath: String? = null,
        glesLibraryPath: String? = null
    ): Boolean {
        val target = serviceMessenger
        if (closed.get() || target == null) {
            onRendererPreload(Result.failure(IllegalStateException("The isolated game process is not connected yet.")))
            return false
        }
        rendererPreloadResponseDelivered.set(false)
        timeoutHandler.postDelayed(rendererPreloadTimeoutRunnable, 30_000L)
        val request = Message.obtain(null, OEngineGameProcessService.MSG_PREPARE_RENDERER).apply {
            data.putString(OEngineGameProcessService.KEY_RENDERER_FILE, rendererFile.canonicalPath)
            data.putString(OEngineGameProcessService.KEY_RENDERER_ROOT, rendererRoot.canonicalPath)
            data.putString(OEngineGameProcessService.KEY_RENDERER_EGL, eglLibraryPath)
            data.putString(OEngineGameProcessService.KEY_RENDERER_GLES, glesLibraryPath)
            replyTo = replyMessenger
        }
        return try {
            target.send(request)
            true
        } catch (error: RemoteException) {
            timeoutHandler.removeCallbacks(rendererPreloadTimeoutRunnable)
            if (rendererPreloadResponseDelivered.compareAndSet(false, true)) onRendererPreload(Result.failure(error))
            false
        }
    }

    fun installRendererHook(
        rendererFile: File,
        rendererRoot: File,
        targetClassName: String,
        selectedAlias: String,
        eglLibraryPath: String? = null,
        glesLibraryPath: String? = null
    ): Boolean {
        val target = serviceMessenger
        if (closed.get() || target == null) {
            onRendererHook(Result.failure(IllegalStateException("The isolated game process is not connected yet.")))
            return false
        }
        rendererResponseDelivered.set(false)
        timeoutHandler.postDelayed(rendererTimeoutRunnable, 30_000L)
        val request = Message.obtain(null, OEngineGameProcessService.MSG_INSTALL_RENDERER_HOOK).apply {
            data.putString(OEngineGameProcessService.KEY_RENDERER_FILE, rendererFile.canonicalPath)
            data.putString(OEngineGameProcessService.KEY_RENDERER_ROOT, rendererRoot.canonicalPath)
            data.putString(OEngineGameProcessService.KEY_LWJGL_TARGET_CLASS, targetClassName)
            data.putString(OEngineGameProcessService.KEY_RENDERER_ALIAS, selectedAlias)
            data.putString(OEngineGameProcessService.KEY_RENDERER_EGL, eglLibraryPath)
            data.putString(OEngineGameProcessService.KEY_RENDERER_GLES, glesLibraryPath)
            replyTo = replyMessenger
        }
        return try {
            target.send(request)
            true
        } catch (error: RemoteException) {
            timeoutHandler.removeCallbacks(rendererTimeoutRunnable)
            if (rendererResponseDelivered.compareAndSet(false, true)) onRendererHook(Result.failure(error))
            false
        }
    }

    fun invokeMain(mainClass: String, arguments: List<String>): Boolean {
        val target = serviceMessenger ?: return false
        val data = Bundle().apply {
            putString(OEngineGameProcessService.KEY_MAIN_CLASS, mainClass)
            putStringArray(OEngineGameProcessService.KEY_GAME_ARGUMENTS, arguments.toTypedArray())
        }
        val reply = Message.obtain(null, OEngineGameProcessService.MSG_INVOKE_MAIN).apply {
            this.data = data
            replyTo = replyMessenger
        }
        return try {
            target.send(reply)
            true
        } catch (_: RemoteException) {
            false
        }
    }

    fun connect(): Boolean {
        check(!closed.get()) { "This game-process client has already been closed." }
        check(connectStarted.compareAndSet(false, true)) { "This game-process client has already started binding." }
        val intent = Intent(appContext, OEngineGameProcessService::class.java)
        return try {
            val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (bound) timeoutHandler.postDelayed(timeoutRunnable, 5_000L)
            else report(Result.failure(IllegalStateException("Android refused the isolated game-process binding.")))
            bound
        } catch (error: SecurityException) {
            report(Result.failure(error))
            false
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            timeoutHandler.removeCallbacks(probeTimeoutRunnable)
            timeoutHandler.removeCallbacks(createTimeoutRunnable)
            timeoutHandler.removeCallbacks(rendererTimeoutRunnable)
            timeoutHandler.removeCallbacks(rendererPreloadTimeoutRunnable)
            serviceMessenger = null
            runCatching { appContext.unbindService(connection) }
        }
    }
}
