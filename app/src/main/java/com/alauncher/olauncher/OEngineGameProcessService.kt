package com.alauncher.olauncher

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process

/**
 * IPC endpoint hosted in the isolated :game process.
 *
 * This first milestone intentionally performs no runtime loading or VM creation. It proves
 * that the launcher can start and communicate with a separately-owned game process.
 */
class OEngineGameProcessService : Service() {
    private val messenger by lazy {
        Messenger(Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                MSG_HEALTH_CHECK -> {
                    val reply = Message.obtain(null, MSG_HEALTH_CHECK_RESULT).apply {
                        data.putBoolean(KEY_READY, true)
                        data.putInt(KEY_PID, Process.myPid())
                        data.putString(KEY_PROCESS, applicationInfo.processName + ":game")
                        data.putString(KEY_PHASE, "process_ready")
                    }
                    runCatching { message.replyTo?.send(reply) }
                    true
                }
                else -> false
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    companion object {
        const val MSG_HEALTH_CHECK = 1
        const val MSG_HEALTH_CHECK_RESULT = 2
        const val KEY_READY = "oengine.ready"
        const val KEY_PID = "oengine.pid"
        const val KEY_PROCESS = "oengine.process"
        const val KEY_PHASE = "oengine.phase"
    }
}
