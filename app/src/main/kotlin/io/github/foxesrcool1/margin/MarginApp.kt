package io.github.foxesrcool1.margin

import android.app.Activity
import android.app.Application
import android.os.Bundle
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.log.CrashHandler
import io.github.foxesrcool1.margin.core.speed.SpeedWatch
import io.github.foxesrcool1.margin.core.storage.DataRoot
import kotlin.concurrent.thread

class MarginApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Remembers which activity the process made first. See HomeActivity.logStartTime.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (SpeedWatch.firstActivity == null) SpeedWatch.firstActivity = activity.javaClass.name
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        AppLog.init(this)
        // Tells on disk or network work on the main thread, in a debug build.
        io.github.foxesrcool1.margin.core.speed.SpeedWatch.watchMainThread(BuildConfig.DEBUG)
        CrashHandler.install(this)
        AppLog.i(
            "App",
            "Margin ${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR}, " +
                "build ${BuildConfig.VERSION_CODE}) started",
        )
        // The Kotlin version comes from the Android Gradle plugin now, and it
        // is useful to know which one ended up in the build.
        AppLog.i("App", "Kotlin ${KotlinVersion.CURRENT}, Android ${android.os.Build.VERSION.SDK_INT}")

        // A fast pen path that killed the app last time is switched off here,
        // before any screen can try it again.
        io.github.foxesrcool1.margin.core.eink.EinkDevices.settleAfterStart(this)

        // Making a few folders is fast, but a launcher must not touch the disk
        // on the main thread while the home screen is trying to appear.
        thread(name = "eink-data-root", isDaemon = true) {
            DataRoot.prepare(this)
            // Temporary files a crash left behind, hidden from every list, go now.
            runCatching { DataRoot.repository(this).sweepLeftovers() }
                .onFailure { AppLog.w("App", "Could not sweep leftover files: $it") }
            io.github.foxesrcool1.margin.core.update.UpdateManager.settleAfterStart(this)
        }
    }
}
