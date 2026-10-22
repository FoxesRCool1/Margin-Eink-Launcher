package io.github.foxesrcool1.margin.core.log

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executors

/**
 * The app log.
 *
 * The tablet gives us no `adb shell` and no logcat (see plan section 3.3), so
 * the app must keep its own log. Lines go to a file in `filesDir/logs/` and to
 * an in memory buffer that the log viewer reads. The owner can copy the files
 * to `Download/Margin/` from the log viewer and send them to us.
 */
object AppLog {

    private const val KEEP_DAYS = 14
    private const val MEMORY_LINES = 600

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "eink-log").apply { isDaemon = true }
    }

    private val memory = ArrayDeque<LogLine>(MEMORY_LINES)

    /** Lines on their way to the file. See [drain]. */
    private val pending = java.util.concurrent.ConcurrentLinkedQueue<LogLine>()
    private val draining = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var logDir: File? = null

    /** Also send every line to logcat. Off on the tablet, on for the tests. */
    @Volatile
    var mirrorToLogcat: Boolean = true

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs")
        dir.mkdirs()
        logDir = dir
        writer.execute { deleteOldFiles(dir, System.currentTimeMillis()) }
        i("AppLog", "Log started. Directory: ${dir.absolutePath}")
    }

    fun d(tag: String, message: String) = write(LogLevel.DEBUG, tag, message, null)

    fun i(tag: String, message: String) = write(LogLevel.INFO, tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) =
        write(LogLevel.WARN, tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) =
        write(LogLevel.ERROR, tag, message, error)

    private fun write(level: LogLevel, tag: String, message: String, error: Throwable?) {
        val full = if (error == null) message else message + "\n" + LogFormat.stackTrace(error)
        val line = LogLine(System.currentTimeMillis(), level, tag, full)

        synchronized(memory) {
            if (memory.size >= MEMORY_LINES) memory.removeFirst()
            memory.addLast(line)
        }

        if (mirrorToLogcat) {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, full)
                LogLevel.INFO -> Log.i(tag, full)
                LogLevel.WARN -> Log.w(tag, full)
                LogLevel.ERROR -> Log.e(tag, full)
            }
        }

        val dir = logDir ?: return
        pending.add(line)
        if (draining.compareAndSet(false, true)) writer.execute { drain(dir) }
    }

    /**
     * Writes every line that is waiting, with one open of the file for all of
     * them. A burst, such as the vendor method list, used to open and close
     * the file once per line, and each of those is a write to the flash.
     * A line that comes in after [draining] is cleared starts the next drain,
     * so none is left behind.
     */
    private fun drain(dir: File) {
        draining.set(false)
        val batch = generateSequence { pending.poll() }.toList()
        batch.groupBy { LogFormat.fileNameFor(it.timeMillis) }.forEach { (name, lines) ->
            runCatching { File(dir, name).appendText(lines.joinToString("") { it.format() + "\n" }) }
        }
    }

    /** The newest lines, oldest first. The log viewer shows these. */
    fun recentLines(): List<LogLine> = synchronized(memory) { memory.toList() }

    /** Every log file, newest first. */
    fun logFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("log-") }
            ?.sortedByDescending { it.name }
            ?: emptyList()
    }

    fun logDirectory(): File? = logDir

    /** Waits for the writer queue to drain. Used before an export. */
    fun flush() {
        val done = java.util.concurrent.CountDownLatch(1)
        runCatching { writer.execute { done.countDown() } }
            .onFailure { return }
        done.await(2, java.util.concurrent.TimeUnit.SECONDS)
    }

    internal fun deleteOldFiles(dir: File, nowMillis: Long) {
        dir.listFiles()?.forEach { file ->
            if (LogFormat.isExpiredLogFile(file.name, nowMillis, KEEP_DAYS)) {
                file.delete()
            }
        }
    }

    /**
     * Copies one file into `Download/Margin/`. Returns the display name
     * on success, or null. MediaStore needs no permission for this on API 29
     * and above. An earlier copy of the same file is replaced, so the folder
     * holds one copy and not "name (1)", "name (2)" after every press.
     */
    fun copyToDownloads(context: Context, file: File): String? {
        if (!file.exists()) return null
        val folder = Environment.DIRECTORY_DOWNLOADS + "/Margin"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        // Android stores the folder with a slash at the end. A row from an
        // earlier install belongs to that install and stays, and then the new
        // copy gets a number: that is the most this can do without permission.
        runCatching {
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} IN (?, ?)",
                arrayOf(file.name, folder, "$folder/"),
            )
        }.onFailure { Log.w("AppLog", "Could not remove the earlier copy of ${file.name}", it) }
        val uri = runCatching {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null

        val copied = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } != null
        }.getOrDefault(false)

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        runCatching { resolver.update(uri, values, null, null) }

        return if (copied) file.name else null
    }

    /** Copies every log file into `Download/Margin/`. Returns how many worked. */
    fun copyAllToDownloads(context: Context): Int {
        flush()
        return logFiles().count { copyToDownloads(context, it) != null }
    }
}
