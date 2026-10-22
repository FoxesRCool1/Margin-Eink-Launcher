package io.github.foxesrcool1.margin.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import io.github.foxesrcool1.margin.core.log.AppLog
import java.io.File

private const val TAG = "ApkInstaller"

/**
 * Hands a downloaded APK to Android.
 *
 * The first route is a `PackageInstaller` session. Android shows its own
 * "install this update?" window, and then reports back to
 * [InstallStatusReceiver] in words. On a tablet with no logcat, that report
 * in the app log is the only way to find out why an install failed.
 *
 * If this firmware refuses to make a session, the second route is the older
 * one: open the file with the system installer. It reports nothing back.
 */
object ApkInstaller {

    const val ACTION_STATUS = "io.github.foxesrcool1.margin.INSTALL_STATUS"

    /** Android asks the user once to let this app install things. False until they have said yes. */
    fun isAllowed(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)

    /** Opens the system screen where the user gives that permission. */
    fun openPermissionScreen(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { AppLog.e(TAG, "Could not open the unknown sources screen", it) }.isSuccess

    fun install(context: Context, apk: File): Result<Unit> {
        AppLog.i(TAG, "Installing ${apk.name}, ${apk.length()} bytes")
        return runCatching { installWithSession(context.applicationContext, apk) }
            .recoverCatching { error ->
                AppLog.w(TAG, "The install session failed. Trying the system installer.", error)
                installWithViewIntent(context.applicationContext, apk)
            }
            .onFailure { AppLog.e(TAG, "No install route worked", it) }
    }

    private fun installWithSession(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output, 256 * 1024) }
                    session.fsync(output)
                }
                // Android fills in the result, so the intent has to be mutable.
                // It names the receiver class, so nothing else can catch it.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val report = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(context, InstallStatusReceiver::class.java).setAction(ACTION_STATUS),
                    flags,
                )
                session.commit(report.intentSender)
            }
            AppLog.i(TAG, "Install session $sessionId committed")
        } catch (error: Throwable) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
    }

    private fun installWithViewIntent(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        AppLog.i(TAG, "Opened the system installer")
    }
}

/** Turns the result code of an install session into a line for the user. */
object InstallMessages {

    fun forStatus(status: Int, detail: String?): String {
        val line = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled."
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked the install."
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Signed with a different key."
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Not compatible with this tablet."
            PackageInstaller.STATUS_FAILURE_INVALID -> "The file is damaged."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free space."
            else -> "Install failed."
        }
        return if (detail.isNullOrBlank()) line else "$line ($detail)"
    }
}

/**
 * Gets the progress of an install session from Android.
 *
 * When the update goes in, Android stops this app first, so the good news
 * rarely arrives. The next start logs the new version instead. The bad news
 * always arrives, and that is the part that matters.
 */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                AppLog.i(TAG, "Android wants the user to confirm the install")
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                val shown = confirm != null && runCatching {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { AppLog.e(TAG, "Could not show the confirm window", it) }.isSuccess
                if (!shown) UpdateManager.onInstallFailed("Could not show the install window.")
            }

            PackageInstaller.STATUS_SUCCESS -> AppLog.i(TAG, "The install worked")

            else -> {
                AppLog.e(TAG, "Install failed with status $status: $detail")
                UpdateManager.onInstallFailed(InstallMessages.forStatus(status, detail))
            }
        }
    }
}
