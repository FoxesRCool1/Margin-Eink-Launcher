package io.github.foxesrcool1.margin.core.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.foxesrcool1.margin.core.log.AppLog

private const val TAG = "DefaultLauncher"

/**
 * Making this app the home app.
 *
 * The stock launcher on the ViWoods tablet hides the standard Android Settings
 * app, so the usual route may not exist. Plan section 3.2 gives three ways to
 * try, in order. This object knows all three and never assumes one works.
 */
object DefaultLauncher {

    /** True when this app is already the home app. */
    fun isDefault(context: Context): Boolean = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            home,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        resolved?.activityInfo?.packageName == context.packageName
    }.getOrElse {
        AppLog.w(TAG, "Could not read the current home app", it)
        false
    }

    /**
     * Way one: ask the system for the home role. This is the clean route and it
     * exists from Android 10. It can still be missing on a vendor build.
     */
    fun roleRequestIntent(context: Context): Intent? = runCatching {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            AppLog.i(TAG, "ROLE_HOME is not available on this build")
            return null
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            AppLog.i(TAG, "ROLE_HOME is already held")
            return null
        }
        roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }.getOrElse {
        AppLog.w(TAG, "ROLE_HOME request failed", it)
        null
    }

    /** Way two: open the system screen that picks the home app. */
    fun homeSettingsIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (canBeStarted(context, intent)) intent else null
    }

    /** Way two, second try: open the whole Settings app. */
    fun allSettingsIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (canBeStarted(context, intent)) intent else null
    }

    /**
     * Way three: written steps. The DevCheck route is the one that is known to
     * work on this tablet when the settings app is hidden.
     */
    val manualSteps: List<String> = listOf(
        "Install DevCheck from the Play Store.",
        "Open DevCheck, then the Apps tab.",
        "Tap Margin, then Manage.",
        "Tap Set as default, Home app, Margin.",
    )

    private fun canBeStarted(context: Context, intent: Intent): Boolean = runCatching {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }.getOrDefault(false)
}
