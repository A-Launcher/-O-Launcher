package com.alauncher.olauncher

import android.content.Context
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Small cross-process session log shared by the launcher and the isolated :game process.
 * Never pass authentication tokens or full game arguments to this logger.
 */
object OEngineSessionLog {
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private val lock = Any()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun file(context: Context): File =
        File(context.filesDir, "oengine/logs/latest-session.log")

    fun append(context: Context, event: String, detail: String, error: Throwable? = null) {
        runCatching {
            synchronized(lock) {
                val target = file(context)
                target.parentFile?.mkdirs()
                if (target.exists() && target.length() > MAX_BYTES) {
                    val previous = File(target.parentFile, "previous-session.log")
                    if (previous.exists()) previous.delete()
                    target.renameTo(previous)
                }
                val safeEvent = event.replace('\n', ' ').take(100)
                val safeDetail = detail.replace('\u0000', ' ').take(3000)
                FileOutputStream(target, true).bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.append(formatter.format(Instant.now()))
                    writer.append(" [pid=").append(android.os.Process.myPid().toString()).append("] ")
                    writer.append(safeEvent).append(": ").append(safeDetail).append('\n')
                    if (error != null) {
                        writer.append(error.stackTraceToString().take(12000)).append('\n')
                    }
                }
            }
        }
    }

    fun read(context: Context, maxChars: Int = 24000): String {
        val target = file(context)
        val content = if (!target.isFile) {
            "No O Engine session log has been created yet."
        } else {
            runCatching {
                val value = target.readText(Charsets.UTF_8)
                if (value.length > maxChars) "… earlier entries omitted …\n" + value.takeLast(maxChars)
                else value
            }.getOrElse { "Could not read session log: ${it.message}" }
        }
        val exits = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val gameExits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 8)
                    .filter { it.processName == context.packageName + ":game" || it.processName.endsWith(":game") }
                if (gameExits.isEmpty()) {
                    "No recent Android exit records for the :game process."
                } else {
                    gameExits.joinToString("\\n") { info ->
                        val reason = when (info.reason) {
                            ApplicationExitInfo.REASON_CRASH -> "CRASH"
                            ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                            ApplicationExitInfo.REASON_ANR -> "ANR"
                            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
                            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
                            else -> "REASON_" + info.reason
                        }
                        formatter.format(Instant.ofEpochMilli(info.timestamp)) +
                            " • " + reason +
                            " • status=" + info.status +
                            " • " + (info.description ?: "No OS description") +
                            " • PSS=" + info.pss + "KB RSS=" + info.rss + "KB"
                    }
                }
            }.getOrElse { "Android exit history unavailable: ${it.message}" }
        } else {
            "Android process-exit history requires Android 11 or newer."
        }
        return content + "\\n\\nANDROID :GAME PROCESS EXIT HISTORY\\n" + exits
    }
}
