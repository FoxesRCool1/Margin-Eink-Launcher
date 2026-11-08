package io.github.foxesrcool1.margin.ui.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import io.github.foxesrcool1.margin.core.log.AppLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.text.Collator

private const val TAG = "AppsRepository"

/** Sorts A to Z by label, so accented letters sit with their plain ones. */
internal fun sortByLabel(entries: List<LauncherEntry>): List<LauncherEntry> {
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    return entries.sortedWith(compareBy(collator) { it.label })
}

/** True only when the system confirms the package is not installed. */
internal fun isUninstalled(pm: PackageManager, pkg: String): Boolean =
    runCatching { pm.getPackageInfo(pkg, 0) }.exceptionOrNull() is PackageManager.NameNotFoundException

/**
 * The list of apps on the tablet.
 *
 * It uses `LauncherApps`, which is the API meant for home apps. Every call is
 * guarded: a vendor build that refuses one of them must not take the launcher
 * down with it, because then the user cannot reach any app at all.
 */
class AppsRepository(context: Context) {

    private val appContext = context.applicationContext

    private val launcherApps: LauncherApps? =
        runCatching {
            appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        }.getOrElse {
            AppLog.e(TAG, "LauncherApps is not available", it)
            null
        }

    /** Every app with a launcher entry, sorted A to Z by label. */
    fun loadAll(): List<LauncherEntry> {
        // One app with broken resources throws from its label. It is skipped,
        // and the other apps stay: a guard around the whole list would turn
        // one bad app into "No apps found".
        val fromLauncherApps = runCatching {
            launcherApps
                ?.getActivityList(null, Process.myUserHandle())
                ?.mapNotNull { info ->
                    runCatching {
                        LauncherEntry(
                            packageName = info.componentName.packageName,
                            className = info.componentName.className,
                            label = info.label?.toString().orEmpty()
                                .ifBlank { info.componentName.packageName },
                        )
                    }.getOrElse {
                        AppLog.w(TAG, "Skipped an app whose label could not be read", it)
                        null
                    }
                }
                .orEmpty()
        }.getOrElse {
            AppLog.e(TAG, "getActivityList failed, falling back to the package manager", it)
            emptyList()
        }

        // This app has a launcher entry too, so the stock launcher can open
        // it. In its own list it would only lead back to where the user is.
        val entries = fromLauncherApps.ifEmpty { loadFromPackageManager() }
            .filter { it.packageName != appContext.packageName }

        AppLog.i(TAG, "Loaded ${entries.size} apps")
        return sortByLabel(entries)
    }

    private fun loadFromPackageManager(): List<LauncherEntry> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        appContext.packageManager
            .queryIntentActivities(intent, 0)
            .mapNotNull { resolved ->
                runCatching {
                    LauncherEntry(
                        packageName = resolved.activityInfo.packageName,
                        className = resolved.activityInfo.name,
                        label = resolved.loadLabel(appContext.packageManager).toString()
                            .ifBlank { resolved.activityInfo.packageName },
                    )
                }.getOrElse {
                    AppLog.w(TAG, "Skipped an app whose label could not be read", it)
                    null
                }
            }
    }.getOrElse {
        AppLog.e(TAG, "The package manager fallback failed too", it)
        emptyList()
    }

    /**
     * The fixed entries that always let the user leave. Plan section 3.2: the
     * user must always be able to reach the ViWoods settings and the stock
     * launcher from our Apps tab.
     *
     * It takes the list from [loadAll] rather than reading it again. Listing
     * every app twice on a tablet with a slow processor is a visible pause.
     */
    fun escapeEntries(apps: List<LauncherEntry>): List<EscapeEntry> {
        val result = mutableListOf<EscapeEntry>()

        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            appContext.packageManager
                .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.packageName != appContext.packageName }
                // Android's own settings app holds a home screen with no name
                // and a priority of -1000. It is the blank screen shown before
                // the tablet is unlocked, not a launcher anybody can use.
                .filter { it.priority >= 0 }
                .forEach { resolved ->
                    // The way back to the stock launcher must survive one
                    // home app with a broken label.
                    val label = runCatching { resolved.loadLabel(appContext.packageManager).toString() }
                        .getOrDefault("")
                    result += EscapeEntry(
                        label = label.ifBlank { resolved.activityInfo.packageName },
                        hint = "Other home app",
                        kind = EscapeKind.OtherHome,
                        packageName = resolved.activityInfo.packageName,
                        className = resolved.activityInfo.name,
                    )
                }
        }.onFailure { AppLog.w(TAG, "Could not list the other home apps", it) }

        // The ViWoods settings app holds the front light, the refresh mode and
        // Wi-Fi. Its package name is not documented, so look for it by name.
        // Step 3 device testing has to confirm what it really is.
        runCatching {
            apps
                .filter { it.packageName.contains("viwoods", ignoreCase = true) }
                .forEach { entry ->
                    result += EscapeEntry(
                        label = entry.label,
                        hint = "Device settings",
                        kind = EscapeKind.DeviceSettings,
                        packageName = entry.packageName,
                        className = entry.className,
                    )
                }
        }.onFailure { AppLog.w(TAG, "Could not find the device settings app", it) }

        runCatching {
            val settings = Intent(Settings.ACTION_SETTINGS)
            if (appContext.packageManager.resolveActivity(settings, 0) != null) {
                result += EscapeEntry(
                    label = "Android settings",
                    hint = "System",
                    kind = EscapeKind.AndroidSettings,
                )
            }
        }.onFailure { AppLog.w(TAG, "Could not find the Android settings app", it) }

        return result
    }

    /** Starts an app. Returns false when it could not be started. */
    fun launch(entry: LauncherEntry): Boolean = runCatching {
        val component = ComponentName(entry.packageName, entry.className)
        val apps = launcherApps
        val started = apps != null && runCatching {
            apps.startMainActivity(component, Process.myUserHandle(), null, null)
        }.isSuccess

        if (!started) {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(entry.packageName, entry.className)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            appContext.startActivity(intent)
        }
        true
    }.getOrElse {
        AppLog.e(TAG, "Could not start ${entry.key}", it)
        false
    }

    fun launchEscape(entry: EscapeEntry): Boolean = runCatching {
        val intent = when (entry.kind) {
            EscapeKind.AndroidSettings ->
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            EscapeKind.OtherHome, EscapeKind.DeviceSettings -> {
                val pkg = entry.packageName ?: return false
                val cls = entry.className
                if (cls != null) {
                    // A home screen is found by the HOME category, and that is
                    // what its intent filter says. Android 13 refuses an intent
                    // that names an activity but does not match its filter,
                    // when the other app targets Android 13 too. This is the
                    // way out of this launcher, so it asks the way that must work.
                    val category =
                        if (entry.kind == EscapeKind.OtherHome) Intent.CATEGORY_HOME else Intent.CATEGORY_LAUNCHER
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(category)
                        .setClassName(pkg, cls)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } else {
                    appContext.packageManager.getLaunchIntentForPackage(pkg)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?: return false
                }
            }
        }
        appContext.startActivity(intent)
        true
    }.getOrElse {
        AppLog.e(TAG, "Could not start the escape entry ${entry.label}", it)
        false
    }

    /** Opens the system app info screen. */
    fun openAppInfo(entry: LauncherEntry): Boolean = runCatching {
        val component = ComponentName(entry.packageName, entry.className)
        val apps = launcherApps
        val opened = apps != null && runCatching {
            apps.startAppDetailsActivity(component, Process.myUserHandle(), null, null)
        }.isSuccess

        if (!opened) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${entry.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        }
        true
    }.getOrElse {
        AppLog.e(TAG, "Could not open app info for ${entry.key}", it)
        false
    }

    /** Asks the system to uninstall an app. The system asks the user first. */
    fun requestUninstall(entry: LauncherEntry): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(Uri.parse("package:${entry.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        true
    }.getOrElse {
        AppLog.e(TAG, "Could not ask to uninstall ${entry.packageName}", it)
        false
    }

    /**
     * Emits once every time an app is added, removed or changed, so the list
     * can reload. Emits once at the start as well.
     */
    fun packageChanges(): Flow<Unit> = callbackFlow {
        val apps = launcherApps
        if (apps == null) {
            trySend(Unit)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: android.os.UserHandle?) {
                AppLog.i(TAG, "Package removed: $packageName")
                trySend(Unit)
            }

            override fun onPackageAdded(packageName: String?, user: android.os.UserHandle?) {
                AppLog.i(TAG, "Package added: $packageName")
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String?, user: android.os.UserHandle?) {
                trySend(Unit)
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: android.os.UserHandle?,
                replacing: Boolean,
            ) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: android.os.UserHandle?,
                replacing: Boolean,
            ) {
                trySend(Unit)
            }
        }

        runCatching { apps.registerCallback(callback, Handler(Looper.getMainLooper())) }
            .onFailure { AppLog.e(TAG, "Could not watch for package changes", it) }

        trySend(Unit)

        awaitClose {
            runCatching { apps.unregisterCallback(callback) }
        }
    }
}
