package io.github.foxesrcool1.margin.core.eink

import io.github.foxesrcool1.margin.core.log.AppLog
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val TAG = "EinkThread"

/**
 * The one thread every vendor e-ink call runs on.
 *
 * A vendor call is a binder call, and a failed one can wait for a shell
 * command. On the main thread that froze the screen. There is one thread, not
 * a pool, so the calls run in the order they were made: a stop never overtakes
 * a start. [ViwoodsEinkDevice] keeps its state, such as the fast pen that is
 * on, for this thread alone.
 */
object EinkThread {

    /** A variable for the tests, which run the work in place. */
    @Volatile
    var executor: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "eink-vendor").apply { isDaemon = true }
    }

    /** Queues [task]. It never throws, and a task that throws does not stop the next one. */
    fun run(task: () -> Unit) {
        runCatching {
            executor.execute { runCatching(task).onFailure { AppLog.e(TAG, "A vendor call failed", it) } }
        }.onFailure { AppLog.e(TAG, "Could not queue a vendor call", it) }
    }
}
