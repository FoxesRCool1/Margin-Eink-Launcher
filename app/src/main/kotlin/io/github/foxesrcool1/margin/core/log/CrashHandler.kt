package io.github.foxesrcool1.margin.core.log

import android.content.Context
import java.io.File

/**
 * Writes a crash file before the process dies, then hands over to the handler
 * that was there before. Without logcat this file is the only crash record.
 */
class CrashHandler private constructor(
    private val appContext: Context,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        runCatching {
            val now = System.currentTimeMillis()
            val text = buildString {
                append("Margin crash\n")
                append("Time: ").append(LogFormat.timestamp(now)).append('\n')
                append("Thread: ").append(thread.name).append('\n')
                append("Android: ").append(android.os.Build.VERSION.SDK_INT).append('\n')
                append("Device: ").append(android.os.Build.MANUFACTURER).append(' ')
                append(android.os.Build.MODEL).append('\n')
                append("Build: ").append(android.os.Build.FINGERPRINT).append("\n\n")
                append(LogFormat.stackTrace(error))
            }

            AppLog.e("Crash", "Uncaught exception on ${thread.name}", error)
            AppLog.flush()

            val dir = AppLog.logDirectory() ?: File(appContext.filesDir, "logs").apply { mkdirs() }
            val file = File(dir, "crash-${LogFormat.dayStamp(now)}-$now.txt")
            // AppLog copies it to Downloads on the next start.
            file.writeText(text)
        }

        previous?.uncaughtException(thread, error)
    }

    companion object {
        fun install(context: Context) {
            val appContext = context.applicationContext
            val current = Thread.getDefaultUncaughtExceptionHandler()
            if (current is CrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext, current))
        }
    }
}
