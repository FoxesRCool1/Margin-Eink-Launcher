package io.github.foxesrcool1.margin.core.update

import android.content.Context
import io.github.foxesrcool1.margin.BuildConfig
import io.github.foxesrcool1.margin.core.log.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "UpdateManager"

/** Where an update stands. The update screen draws exactly this. */
sealed interface UpdateState {

    data object Idle : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val newest: AppVersion?) : UpdateState

    data class Available(val release: ReleaseInfo, val asset: ReleaseAsset) : UpdateState

    /** [percent] moves in steps of ten, so the panel repaints ten times and not a thousand. */
    data class Downloading(val release: ReleaseInfo, val asset: ReleaseAsset, val percent: Int) : UpdateState

    /**
     * The file is on the tablet and checked. [handedOver] is true once
     * Android has been asked to install it. [note] is what Android said.
     */
    data class Ready(
        val release: ReleaseInfo,
        val file: File,
        val handedOver: Boolean = false,
        val note: String? = null,
    ) : UpdateState

    data class Failed(val message: String, val tokenProblem: Boolean = false) : UpdateState
}

/**
 * The one place that runs an update: check, download, check the file, install.
 *
 * It lives outside any screen on purpose. The file is about 60 MB, and the
 * Home key must not stop the download half way. The update screen only shows
 * [state] and calls the three verbs.
 */
object UpdateManager {

    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = mutableState

    /** A test swaps these two. */
    internal var dispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var releasesFactory: () -> GithubReleases = {
        GithubReleases(
            http = UrlConnectionHttp(),
            repository = BuildConfig.UPDATE_REPOSITORY,
            userAgent = "Margin/${BuildConfig.VERSION_NAME}",
        )
    }

    private val scope = CoroutineScope(SupervisorJob())

    val installedVersion: AppVersion?
        get() = AppVersion.parse(BuildConfig.VERSION_NAME)

    fun check(context: Context) {
        val busy = mutableState.value.let { it is UpdateState.Checking || it is UpdateState.Downloading }
        if (busy) return
        val installed = installedVersion ?: run {
            mutableState.value = UpdateState.Failed("No version number: ${BuildConfig.VERSION_NAME}")
            return
        }
        val app = context.applicationContext
        mutableState.value = UpdateState.Checking
        scope.launch(dispatcher) {
            AppLog.i(TAG, "Checking ${BuildConfig.UPDATE_REPOSITORY} for a version after $installed")
            val found = runCatching {
                releasesFactory().check(installed, BuildConfig.FLAVOR, BuildConfig.DEBUG, UpdateToken.read(app))
            }.getOrElse { error ->
                AppLog.e(TAG, "The check threw", error)
                UpdateCheck.Failed("Check failed: ${error.message}")
            }
            AppLog.i(TAG, "Check result: ${describe(found)}")
            mutableState.value = when (found) {
                is UpdateCheck.Available -> UpdateState.Available(found.release, found.asset)
                is UpdateCheck.UpToDate -> UpdateState.UpToDate(found.newest)
                is UpdateCheck.Failed -> UpdateState.Failed(found.message, found.tokenProblem)
            }
        }
    }

    fun download(context: Context) {
        val from = mutableState.value as? UpdateState.Available ?: return
        val app = context.applicationContext
        mutableState.value = UpdateState.Downloading(from.release, from.asset, percent = 0)
        scope.launch(dispatcher) {
            AppLog.i(TAG, "Downloading ${from.asset.name}, ${from.asset.sizeBytes} bytes")
            val result = releasesFactory().download(from.asset, UpdateToken.read(app), downloadFolder(app)) { percent ->
                val step = percent / 10 * 10
                mutableState.update { now ->
                    if (now is UpdateState.Downloading && now.percent != step) now.copy(percent = step) else now
                }
            }
            val file = result.getOrElse { error ->
                AppLog.e(TAG, "The download failed", error)
                mutableState.value = UpdateState.Failed(error.message ?: "Download failed.")
                return@launch
            }
            AppLog.i(TAG, "Downloaded and checked ${file.name}")

            mutableState.value = when (val verdict = ApkCheck.inspect(app, file)) {
                is ApkVerdict.Ok -> UpdateState.Ready(from.release, file)
                is ApkVerdict.Refused -> {
                    file.delete()
                    UpdateState.Failed(verdict.message)
                }
            }
        }
    }

    fun install(context: Context) {
        val from = mutableState.value as? UpdateState.Ready ?: return
        val app = context.applicationContext
        mutableState.value = from.copy(handedOver = true, note = INSTALLING_NOTE)
        scope.launch(dispatcher) {
            ApkInstaller.install(app, from.file).onFailure { error ->
                onInstallFailed("Install did not start: ${error.message}")
            }
        }
    }

    /** Called by [InstallStatusReceiver]. The file stays, so the user can try again. */
    fun onInstallFailed(message: String) {
        mutableState.update { now ->
            if (now is UpdateState.Ready) now.copy(handedOver = true, note = message) else UpdateState.Failed(message)
        }
    }

    /**
     * Called once at start-up, off the main thread. Writes the update that
     * just happened into the log, and removes downloads that are no use now.
     */
    fun settleAfterStart(context: Context) {
        runCatching {
            val last = UpdateToken.lastVersionCode(context)
            val now = BuildConfig.VERSION_CODE
            if (last != 0 && last != now) AppLog.i(TAG, "The app was updated from build $last to build $now")
            if (last != now) UpdateToken.setLastVersionCode(context, now)

            val installed = installedVersion
            downloadFolder(context).listFiles()?.forEach { file ->
                val version = VERSION_IN_NAME.find(file.name)?.value?.let(AppVersion::parse)
                val useful = file.name.endsWith(".apk") && version != null && installed != null && version > installed
                if (!useful && file.delete()) AppLog.i(TAG, "Removed the old download ${file.name}")
            }
        }.onFailure { AppLog.w(TAG, "Could not tidy the update folder", it) }
    }

    /** `cache/updates/`. The file provider in the manifest shares this folder and no other. */
    fun downloadFolder(context: Context): File = File(context.cacheDir, "updates")

    private fun describe(found: UpdateCheck): String = when (found) {
        is UpdateCheck.Available -> "${found.release.tag} is available as ${found.asset.name}"
        is UpdateCheck.UpToDate -> "up to date, newest on GitHub is ${found.newest ?: "nothing for this build"}"
        is UpdateCheck.Failed -> "failed, ${found.message}"
    }

    private val VERSION_IN_NAME = Regex("""v\d+\.\d+\.\d+""")

    // While Android swaps the app, it cannot be the home screen, so Android
    // shows the other launcher. It stays in front until the Home key is
    // pressed. Seen on the emulator, where this app was the home app.
    private const val INSTALLING_NOTE =
        "Installing. If the old home screen shows, press Home."

    /** For tests. */
    internal fun reset() = force(UpdateState.Idle)

    /** For tests. */
    internal fun force(state: UpdateState) {
        mutableState.value = state
    }
}
