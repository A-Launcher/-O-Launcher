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
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
    private val onRendererPreload: (Result<OEngineJvmLibraryProbe>) -> Unit = {},
    private val onJvmMainStarted: (OEngineJvmLibraryProbe) -> Unit = {},
    private val onJvmMainRunning: (OEngineJvmLibraryProbe) -> Unit = {},
    private val onGameSurface: (Result<OEngineJvmLibraryProbe>) -> Unit = {}
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val connectStarted = AtomicBoolean(false)
    private val responseDelivered = AtomicBoolean(false)
    private val probeResponseDelivered = AtomicBoolean(false)
    private val createResponseDelivered = AtomicBoolean(false)
    private val rendererResponseDelivered = AtomicBoolean(false)
    private val rendererPreloadResponseDelivered = AtomicBoolean(false)
    private val mainStartedDelivered = AtomicBoolean(false)
    private val mainResponseDelivered = AtomicBoolean(false)
    private val mainRunningDelivered = AtomicBoolean(false)
    private val mainInvocationAttempted = AtomicBoolean(false)
    private val surfaceRequestId = AtomicLong(0L)
    private val surfaceRequestPending = AtomicBoolean(false)
    private val probeRequestPending = AtomicBoolean(false)
    private val createRequestPending = AtomicBoolean(false)
    private val rendererPreloadRequestPending = AtomicBoolean(false)
    private val rendererHookRequestPending = AtomicBoolean(false)
    private val mainRequestPending = AtomicBoolean(false)
    private var serviceMessenger: Messenger? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val surfaceTimeoutRunnable = Runnable {
        if (surfaceRequestPending.compareAndSet(true, false)) {
            onGameSurface(Result.failure(IllegalStateException("The game process did not attach the Android Surface within 10 seconds.")))
        }
    }
    private val mainStartTimeoutRunnable = Runnable {
        if (mainStartedDelivered.compareAndSet(false, true)) {
            mainResponseDelivered.set(true)
            mainRequestPending.set(false)
            onJvmMain(Result.failure(IllegalStateException("The game process did not dispatch Minecraft main within 30 seconds.")))
        }
    }
    private val mainRunningNoticeRunnable = Runnable {
        if (mainStartedDelivered.get() && !mainResponseDelivered.get() &&
            mainRunningDelivered.compareAndSet(false, true)) {
            onJvmMainRunning(OEngineJvmLibraryProbe(
                successful = true,
                status = "main_running",
                detail = "Minecraft main has not returned yet; the isolated HotSpot thread is still executing it."
            ))
        }
    }
    private val mainExecutionTimeoutRunnable = Runnable {
        if (mainStartedDelivered.get() && mainResponseDelivered.compareAndSet(false, true)) {
            mainRequestPending.set(false)
            onJvmMain(Result.failure(IllegalStateException(
                "Minecraft main did not return within 10 minutes. The game process may be stalled; it was not force-stopped."
            )))
        }
    }
    private val timeoutRunnable = Runnable {
        report(Result.failure(IllegalStateException("The isolated game process did not answer its health check within 5 seconds.")))
        close()
    }
    private val probeTimeoutRunnable = Runnable {
        if (probeResponseDelivered.compareAndSet(false, true)) {
            probeRequestPending.set(false)
            onJvmProbe(Result.failure(IllegalStateException("The isolated game process did not finish the JVM linker probe within 15 seconds.")))
        }
    }
    private val createTimeoutRunnable = Runnable {
        if (createResponseDelivered.compareAndSet(false, true)) {
            createRequestPending.set(false)
            onJvmCreate(Result.failure(IllegalStateException("The isolated game process did not finish JVM creation within 60 seconds.")))
        }
    }

    private fun failPendingOperations(error: Throwable) {
        if (probeRequestPending.compareAndSet(true, false) && probeResponseDelivered.compareAndSet(false, true)) {
            OEngineSessionLog.append(appContext, "IPC_PROBE_ABORTED", "Game process disconnected during JVM linker probe", error)
            timeoutHandler.removeCallbacks(probeTimeoutRunnable)
            onJvmProbe(Result.failure(IllegalStateException("Game process disconnected during JVM linker probe.", error)))
        }
        if (createRequestPending.compareAndSet(true, false) && createResponseDelivered.compareAndSet(false, true)) {
            OEngineSessionLog.append(appContext, "IPC_JVM_CREATE_ABORTED", "Game process disconnected during JVM creation", error)
            timeoutHandler.removeCallbacks(createTimeoutRunnable)
            onJvmCreate(Result.failure(IllegalStateException("Game process disconnected during JVM creation.", error)))
        }
        if (rendererPreloadRequestPending.compareAndSet(true, false) && rendererPreloadResponseDelivered.compareAndSet(false, true)) {
            OEngineSessionLog.append(appContext, "IPC_RENDERER_PRELOAD_ABORTED", "Game process disconnected during renderer preload", error)
            timeoutHandler.removeCallbacks(rendererPreloadTimeoutRunnable)
            onRendererPreload(Result.failure(IllegalStateException("Game process disconnected during renderer preload.", error)))
        }
        if (rendererHookRequestPending.compareAndSet(true, false) && rendererResponseDelivered.compareAndSet(false, true)) {
            OEngineSessionLog.append(appContext, "IPC_RENDERER_HOOK_ABORTED", "Game process disconnected during renderer hook installation", error)
            timeoutHandler.removeCallbacks(rendererTimeoutRunnable)
            onRendererHook(Result.failure(IllegalStateException("Game process disconnected during renderer hook installation.", error)))
        }
        if (mainRequestPending.compareAndSet(true, false) && mainResponseDelivered.compareAndSet(false, true)) {
            OEngineSessionLog.append(appContext, "IPC_MINECRAFT_MAIN_ABORTED", "Game process disconnected during Minecraft main invocation", error)
            mainStartedDelivered.set(true)
            timeoutHandler.removeCallbacks(mainStartTimeoutRunnable)
            timeoutHandler.removeCallbacks(mainRunningNoticeRunnable)
            timeoutHandler.removeCallbacks(mainExecutionTimeoutRunnable)
            onJvmMain(Result.failure(IllegalStateException("Game process disconnected during Minecraft main invocation.", error)))
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
            rendererPreloadRequestPending.set(false)
            onRendererPreload(Result.failure(IllegalStateException("The isolated game process did not finish LTW preload within 30 seconds.")))
        }
    }
    private val rendererTimeoutRunnable = Runnable {
        if (rendererResponseDelivered.compareAndSet(false, true)) {
            rendererHookRequestPending.set(false)
            onRendererHook(Result.failure(IllegalStateException("The isolated game process did not finish renderer hook installation within 30 seconds.")))
        }
    }
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            OEngineGameProcessService.MSG_GAME_SURFACE_RESULT -> {
                val data = message.data
                if (data.getLong(OEngineGameProcessService.KEY_SURFACE_REQUEST_ID) != surfaceRequestId.get()) {
                    true
                } else {
                    timeoutHandler.removeCallbacks(surfaceTimeoutRunnable)
                    surfaceRequestPending.set(false)
                    val result = OEngineJvmLibraryProbe(
                    successful = data.getBoolean(OEngineGameProcessService.KEY_PROBE_SUCCESS),
                    status = data.getString(OEngineGameProcessService.KEY_PROBE_STATUS).orEmpty(),
                    detail = data.getString(OEngineGameProcessService.KEY_PROBE_DETAIL).orEmpty()
                )
                OEngineSessionLog.append(
                    appContext,
                    if (result.successful) "IPC_GAME_SURFACE_ATTACHED" else "IPC_GAME_SURFACE_FAILED",
                    result.status + " • " + result.detail
                )
                    onGameSurface(Result.success(result))
                    true
                }
            }
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
                    createRequestPending.set(false)
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
                    rendererPreloadRequestPending.set(false)
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
                    rendererHookRequestPending.set(false)
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
            OEngineGameProcessService.MSG_INVOKE_MAIN_STARTED -> {
                if (mainStartedDelivered.compareAndSet(false, true)) {
                    timeoutHandler.removeCallbacks(mainStartTimeoutRunnable)
                    timeoutHandler.postDelayed(mainRunningNoticeRunnable, 15_000L)
                    timeoutHandler.postDelayed(mainExecutionTimeoutRunnable, 10 * 60 * 1000L)
                    val data = message.data
                    onJvmMainStarted(OEngineJvmLibraryProbe(
                        successful = data.getBoolean(OEngineGameProcessService.KEY_PROBE_SUCCESS),
                        status = data.getString(OEngineGameProcessService.KEY_PROBE_STATUS).orEmpty(),
                        detail = data.getString(OEngineGameProcessService.KEY_PROBE_DETAIL).orEmpty()
                    ))
                }
                true
            }
            OEngineGameProcessService.MSG_INVOKE_MAIN_RESULT -> {
                if (mainResponseDelivered.compareAndSet(false, true)) {
                    mainRequestPending.set(false)
                    mainStartedDelivered.set(true)
                    timeoutHandler.removeCallbacks(mainStartTimeoutRunnable)
                    timeoutHandler.removeCallbacks(mainRunningNoticeRunnable)
                    timeoutHandler.removeCallbacks(mainExecutionTimeoutRunnable)
                    val data = message.data
                    onJvmMain(Result.success(OEngineJvmLibraryProbe(
                        successful = data.getBoolean(OEngineGameProcessService.KEY_PROBE_SUCCESS),
                        status = data.getString(OEngineGameProcessService.KEY_PROBE_STATUS).orEmpty(),
                        detail = data.getString(OEngineGameProcessService.KEY_PROBE_DETAIL).orEmpty()
                    )))
                }
                true
            }
            OEngineGameProcessService.MSG_PROBE_JVM_LIBRARY_RESULT -> {
                if (probeResponseDelivered.compareAndSet(false, true)) {
                    probeRequestPending.set(false)
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
            OEngineSessionLog.append(appContext, "IPC_CONNECTED", "Bound to isolated game process")
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
            OEngineSessionLog.append(appContext, "IPC_DISCONNECTED", "Android reported isolated game-process disconnection")
            if (!closed.get()) {
                val error = IllegalStateException("The isolated game process disconnected.")
                report(Result.failure(error))
                failPendingOperations(error)
                serviceMessenger = null
            }
        }

        override fun onBindingDied(name: ComponentName) {
            OEngineSessionLog.append(appContext, "IPC_BINDING_DIED", "Android reported game-process binding death")
            if (!closed.get()) {
                val error = IllegalStateException("The isolated game-process binding died.")
                report(Result.failure(error))
                failPendingOperations(error)
                serviceMessenger = null
            }
            close()
        }

        override fun onNullBinding(name: ComponentName) {
            OEngineSessionLog.append(appContext, "IPC_NULL_BINDING", "Game-process service returned a null binding")
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
        probeRequestPending.set(true)
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
            probeRequestPending.set(false)
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
        createRequestPending.set(true)
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
            createRequestPending.set(false)
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
        rendererPreloadRequestPending.set(true)
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
            rendererPreloadRequestPending.set(false)
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
        require(targetClassName == OEngineLwjglNativeResolver.DYNAMIC_LINK_LOADER_CLASS_NAME) {
            "Only the exact LWJGL DynamicLinkLoader class is supported by the LTW hook."
        }
        require(selectedAlias == OEngineGraphicsStack.LTW_LWJGL_LIBRARY_ALIAS) {
            "The selected alias does not match the pinned LTW renderer contract."
        }
        val target = serviceMessenger
        if (closed.get() || target == null) {
            onRendererHook(Result.failure(IllegalStateException("The isolated game process is not connected yet.")))
            return false
        }
        rendererResponseDelivered.set(false)
        rendererHookRequestPending.set(true)
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
            rendererHookRequestPending.set(false)
            if (rendererResponseDelivered.compareAndSet(false, true)) onRendererHook(Result.failure(error))
            false
        }
    }

    fun invokeMain(mainClass: String, arguments: List<String>): Boolean {
        val target = serviceMessenger ?: return false
        if (closed.get() || !mainInvocationAttempted.compareAndSet(false, true)) return false
        mainStartedDelivered.set(false)
        mainResponseDelivered.set(false)
        mainRequestPending.set(true)
        mainRunningDelivered.set(false)
        timeoutHandler.removeCallbacks(mainStartTimeoutRunnable)
        timeoutHandler.removeCallbacks(mainRunningNoticeRunnable)
        timeoutHandler.removeCallbacks(mainExecutionTimeoutRunnable)
        timeoutHandler.postDelayed(mainStartTimeoutRunnable, 30_000L)
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
        } catch (error: RemoteException) {
            timeoutHandler.removeCallbacks(mainStartTimeoutRunnable)
            timeoutHandler.removeCallbacks(mainRunningNoticeRunnable)
            timeoutHandler.removeCallbacks(mainExecutionTimeoutRunnable)
            mainRequestPending.set(false)
            if (mainStartedDelivered.compareAndSet(false, true) && mainResponseDelivered.compareAndSet(false, true)) {
                onJvmMain(Result.failure(error))
            }
            false
        }
    }

    /** Passes an Android window surface to the persistent EGL context in :game. */
    fun attachGameSurface(surface: Surface, width: Int, height: Int): Boolean {
        val target = serviceMessenger
        if (closed.get() || target == null || !surface.isValid) return false
        val requestId = surfaceRequestId.incrementAndGet()
        surfaceRequestPending.set(true)
        timeoutHandler.removeCallbacks(surfaceTimeoutRunnable)
        val request = Message.obtain(null, OEngineGameProcessService.MSG_ATTACH_GAME_SURFACE).apply {
            data.putParcelable(OEngineGameProcessService.KEY_GAME_SURFACE, surface)
            data.putInt(OEngineGameProcessService.KEY_SURFACE_WIDTH, width.coerceAtLeast(1))
            data.putInt(OEngineGameProcessService.KEY_SURFACE_HEIGHT, height.coerceAtLeast(1))
            data.putLong(OEngineGameProcessService.KEY_SURFACE_REQUEST_ID, requestId)
            replyTo = replyMessenger
        }
        return try {
            target.send(request)
            timeoutHandler.postDelayed(surfaceTimeoutRunnable, 10_000L)
            true
        } catch (error: RemoteException) {
            surfaceRequestPending.set(false)
            timeoutHandler.removeCallbacks(surfaceTimeoutRunnable)
            onGameSurface(Result.failure(error))
            false
        }
    }

    /** Releases the EGL window surface and returns to the bootstrap pbuffer. */
    fun detachGameSurface(): Boolean {
        val target = serviceMessenger
        if (closed.get() || target == null) return false
        surfaceRequestId.incrementAndGet()
        surfaceRequestPending.set(false)
        timeoutHandler.removeCallbacks(surfaceTimeoutRunnable)
        return try {
            target.send(Message.obtain(null, OEngineGameProcessService.MSG_DETACH_GAME_SURFACE))
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
            if (bound) {
                OEngineSessionLog.append(appContext, "IPC_BIND_REQUESTED", "Android accepted the isolated game-process bind request")
                timeoutHandler.postDelayed(timeoutRunnable, 5_000L)
            } else {
                OEngineSessionLog.append(appContext, "IPC_BIND_REJECTED", "Android refused the isolated game-process binding")
                report(Result.failure(IllegalStateException("Android refused the isolated game-process binding.")))
            }
            bound
        } catch (error: SecurityException) {
            OEngineSessionLog.append(appContext, "IPC_BIND_SECURITY_ERROR", error.message ?: "SecurityException", error)
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
            timeoutHandler.removeCallbacks(mainStartTimeoutRunnable)
            timeoutHandler.removeCallbacks(mainRunningNoticeRunnable)
            timeoutHandler.removeCallbacks(mainExecutionTimeoutRunnable)
            timeoutHandler.removeCallbacks(surfaceTimeoutRunnable)
            surfaceRequestPending.set(false)
            surfaceRequestId.incrementAndGet()
            serviceMessenger = null
            OEngineSessionLog.append(appContext, "IPC_CLIENT_CLOSED", "Game-process client closed")
            runCatching { appContext.unbindService(connection) }
        }
    }
}
