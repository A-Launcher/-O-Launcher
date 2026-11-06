package com.alauncher.olauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
    private val onJvmProbe: (Result<OEngineJvmLibraryProbe>) -> Unit = {}
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val connectStarted = AtomicBoolean(false)
    private val responseDelivered = AtomicBoolean(false)
    private val probeResponseDelivered = AtomicBoolean(false)
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

    private fun report(result: Result<OEngineGameProcessStatus>) {
        if (responseDelivered.compareAndSet(false, true)) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            onStatus(result)
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
            onJvmProbe(Result.failure(error))
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
            serviceMessenger = null
            runCatching { appContext.unbindService(connection) }
        }
    }
}
