package com.alauncher.olauncher

import android.content.Context
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
        if (!target.isFile) return "No O Engine session log has been created yet."
        return runCatching {
            val content = target.readText(Charsets.UTF_8)
            if (content.length > maxChars) "… earlier entries omitted …\n" + content.takeLast(maxChars)
            else content
        }.getOrElse { "Could not read session log: ${it.message}" }
    }
}
