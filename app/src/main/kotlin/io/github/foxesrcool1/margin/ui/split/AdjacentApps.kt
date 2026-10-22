package io.github.foxesrcool1.margin.ui.split

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.foxesrcool1.margin.HomeActivity
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.ui.apps.AppsRepository
import io.github.foxesrcool1.margin.ui.apps.LauncherEntry

private const val TAG = "AdjacentApps"

/**
 * Another app beside this one: Android's own split screen.
 *
 * An app from Apps, chosen in the second half, is asked to open beside the
 * page in the first half. From Android 12L on, the intent flag
 * FLAG_ACTIVITY_LAUNCH_ADJACENT does that from a full screen app. The
 * emulator, Android 13, does it. Whether this tablet does is up to its
 * firmware: the maker can turn multi-window off. Nobody has tried it on the
 * tablet yet.
 *
 * Android never puts the task of the home screen in a half, and everything
 * this app opens from Home lives in that task. Asked from there, Android made
 * a split with the app in one half and nothing, black, in the other. So the
 * page that should stay on screen first opens again in a task of its own
 * (see [PaneHost.keeper]), and asks for the app from there.
 *
 * When the tablet says no, the flag is simply ignored and the app opens on its
 * own, the same as from the Apps page. The log says what happened.
 */
object AdjacentApps {

    private const val EXTRA_PACKAGE = "beside_package"
    private const val EXTRA_CLASS = "beside_class"
    private const val EXTRA_LABEL = "beside_label"
    private const val EXTRA_AT = "beside_at"

    /**
     * A request older than this is dead. After the process dies, Android gives
     * the screen its first intent back, extras and all, and removing them from
     * the copy in memory does not change that one.
     */
    private const val REQUEST_LIFETIME_MILLIS = 15_000L

    /** The app of the last try, until the screen that asked comes back and checks. */
    private var tried: LauncherEntry? = null
    private var triedFrom: Int = 0

    /**
     * Opens [entry] beside the page of [activity]. From the task of the home
     * screen that is two steps: [host] gives the page again in a task of its
     * own, with the app to ask for, and then lets go of its own copy.
     */
    fun open(activity: Activity?, entry: LauncherEntry, host: PaneHost): Boolean {
        if (activity == null) return false
        if (inHomeTask(activity)) {
            val keeper = host.keeper()
            if (keeper != null) {
                // One task beside apps, never a pile: see BesideActivity.
                keeper.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(EXTRA_PACKAGE, entry.packageName)
                    .putExtra(EXTRA_CLASS, entry.className)
                    .putExtra(EXTRA_LABEL, entry.label)
                    .putExtra(EXTRA_AT, System.currentTimeMillis())
                AppLog.i(TAG, "Moving ${keeper.component?.shortClassName} to a task of its own, to put ${entry.key} beside it")
                return runCatching {
                    activity.startActivity(keeper)
                    host.leave()
                    true
                }.getOrElse {
                    AppLog.w(TAG, "Could not move the page to a task of its own", it)
                    AppsRepository(activity).launch(entry)
                }
            }
        }
        return launchBeside(activity, entry)
    }

    /** Hands the request for an app on from [BesideActivity] to the screen it opens. */
    fun moveRequest(from: Intent, to: Intent) {
        listOf(EXTRA_PACKAGE, EXTRA_CLASS, EXTRA_LABEL).forEach { key ->
            from.getStringExtra(key)?.let { to.putExtra(key, it) }
            from.removeExtra(key)
        }
        to.putExtra(EXTRA_AT, from.getLongExtra(EXTRA_AT, 0L))
        from.removeExtra(EXTRA_AT)
    }

    /**
     * The second step: a screen that was opened to stand beside an app asks
     * for that app, once, as soon as it is on screen.
     */
    fun takeRequest(activity: Activity) {
        val intent = activity.intent ?: return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val className = intent.getStringExtra(EXTRA_CLASS) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val askedAt = intent.getLongExtra(EXTRA_AT, 0L)
        listOf(EXTRA_PACKAGE, EXTRA_CLASS, EXTRA_LABEL, EXTRA_AT).forEach(intent::removeExtra)
        if (System.currentTimeMillis() - askedAt > REQUEST_LIFETIME_MILLIS) {
            AppLog.i(TAG, "Dropped an old request for $packageName. The screen was probably restored.")
            return
        }
        val entry = LauncherEntry(packageName = packageName, className = className, label = label)
        // After this frame, so Android has this task on screen before the app joins it.
        activity.window.decorView.post { launchBeside(activity, entry) }
    }

    private fun launchBeside(activity: Activity, entry: LauncherEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(entry.packageName, entry.className)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        AppLog.i(
            TAG,
            "Asking for ${entry.key} beside ${activity.javaClass.simpleName}. " +
                "Task ${activity.taskId}, multi-window now: ${activity.isInMultiWindowMode}",
        )
        return runCatching {
            activity.startActivity(intent)
            tried = entry
            triedFrom = System.identityHashCode(activity)
            true
        }.getOrElse {
            AppLog.w(TAG, "The side by side start failed, starting ${entry.key} the usual way", it)
            AppsRepository(activity).launch(entry)
        }
    }

    /** True when [activity] lives in the task of the home screen, which Android never splits. */
    private fun inHomeTask(activity: Activity): Boolean = runCatching {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val task = manager.appTasks.firstOrNull { it.taskInfo?.taskId == activity.taskId }?.taskInfo
        // No task found is read as the home task: moving works either way.
        task == null || task.baseIntent?.component?.className == HomeActivity::class.java.name
    }.getOrElse {
        AppLog.w(TAG, "Could not read the task of ${activity.javaClass.simpleName}", it)
        // The safe guess: moving to a task of its own works either way.
        true
    }

    /**
     * Called when a screen with a split screen comes back. Returns a line for
     * the user when the last try did not put the app beside it, or null.
     */
    fun checkResult(activity: Activity): String? {
        val entry = tried ?: return null
        if (System.identityHashCode(activity) != triedFrom) return null
        tried = null
        val beside = activity.isInMultiWindowMode
        AppLog.i(TAG, "Result for ${entry.key}: side by side $beside")
        return if (beside) null else "This tablet opened ${entry.label} on its own screen, not beside this one"
    }
}

/**
 * Hooks a screen's split screen up to Android's own.
 *
 * When Android puts this screen into its split screen, the second half of this
 * app closes: half of a half is too small to use. [closeOnSplit] is false for
 * [BesideActivity], whose whole screen is that half. When the screen comes
 * back after a try to open an app beside it, the second half says how that
 * went.
 */
fun ComponentActivity.watchAndroidSplit(split: SplitState, closeOnSplit: Boolean = true) {
    addOnMultiWindowModeChangedListener { info ->
        AppLog.i(TAG, "${javaClass.simpleName} multi-window: ${info.isInMultiWindowMode}")
        if (info.isInMultiWindowMode && closeOnSplit) split.close()
    }
    lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) AdjacentApps.checkResult(this)?.let(split::say)
        },
    )
}
