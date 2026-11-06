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
    private val onStatus: (Result<OEngineGameProcessStatus>) -> Unit
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == OEngineGameProcessService.MSG_HEALTH_CHECK_RESULT) {
            val data = message.data
            val status = OEngineGameProcessStatus(
                ready = data.getBoolean(OEngineGameProcessService.KEY_READY),
                pid = data.getInt(OEngineGameProcessService.KEY_PID),
                processName = data.getString(OEngineGameProcessService.KEY_PROCESS).orEmpty(),
                phase = data.getString(OEngineGameProcessService.KEY_PHASE).orEmpty()
            )
            onStatus(Result.success(status))
            true
        } else {
            false
        }
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed.get()) return
            val request = Message.obtain(null, OEngineGameProcessService.MSG_HEALTH_CHECK).apply {
                replyTo = replyMessenger
            }
            try {
                Messenger(binder).send(request)
            } catch (error: RemoteException) {
                onStatus(Result.failure(error))
                close()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (!closed.get()) onStatus(Result.failure(IllegalStateException("The isolated game process disconnected.")))
        }

        override fun onBindingDied(name: ComponentName) {
            if (!closed.get()) onStatus(Result.failure(IllegalStateException("The isolated game-process binding died.")))
            close()
        }

        override fun onNullBinding(name: ComponentName) {
            if (!closed.get()) onStatus(Result.failure(IllegalStateException("The isolated game process returned a null binding.")))
            close()
        }
    }

    fun connect(): Boolean {
        check(!closed.get()) { "This game-process client has already been closed." }
        val intent = Intent(appContext, OEngineGameProcessService::class.java)
        return try {
            val bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) onStatus(Result.failure(IllegalStateException("Android refused the isolated game-process binding.")))
            bound
        } catch (error: SecurityException) {
            onStatus(Result.failure(error))
            false
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            runCatching { appContext.unbindService(connection) }
        }
    }
}
