package io.github.foxesrcool1.margin.ui.devicetest

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.foxesrcool1.margin.core.eink.EinkDevice
import io.github.foxesrcool1.margin.core.eink.EinkDevices
import io.github.foxesrcool1.margin.core.eink.EinkThread
import io.github.foxesrcool1.margin.core.eink.FastPenPath
import io.github.foxesrcool1.margin.core.eink.RefreshMode
import io.github.foxesrcool1.margin.core.eink.ViwoodsEinkDevice
import io.github.foxesrcool1.margin.core.launcher.DefaultLauncher
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.window.ScreenWindow
import io.github.foxesrcool1.margin.core.window.findActivity
import io.github.foxesrcool1.margin.design.EinkDimens
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.design.EinkType
import io.github.foxesrcool1.margin.design.components.EinkRow
import io.github.foxesrcool1.margin.design.components.EinkText
import io.github.foxesrcool1.margin.design.components.HairlineDivider
import io.github.foxesrcool1.margin.design.components.InvertPressButton
import io.github.foxesrcool1.margin.design.components.IconPressButton
import io.github.foxesrcool1.margin.design.components.PagedList
import io.github.foxesrcool1.margin.design.icons.Lucide
import io.github.foxesrcool1.margin.ui.common.ScreenScaffold

private const val TAG = "DeviceTest"

/**
 * The Step 2 device spike, as a screen the owner can work through.
 *
 * The developer cannot see the tablet, so every test writes what it did and
 * what came back into the log file. The owner runs the tests, writes down
 * what the panel looked like, and sends the log.
 */
class DeviceTestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenWindow.attach(this)
        AppLog.i(TAG, "Device test opened. Target SDK ${applicationInfo.targetSdkVersion}.")
        setContent {
            EinkTheme {
                DeviceTestScreen(onClose = { finish() })
            }
        }
    }
}

private class DeviceTest(
    val label: String,
    val vendor: Boolean = false,
    val run: (Context, EinkDevice) -> String,
)

@Composable
private fun DeviceTestScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val device = remember { EinkDevices.get(context) }
    val results = remember { mutableStateMapOf<String, String>() }
    val tests = remember { buildTests() }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    ScreenScaffold(
        title = "Device test",
        overline = "${device.name} layer, target SDK ${context.applicationInfo.targetSdkVersion}",
        onBack = onClose,
        backIcon = Lucide.ArrowLeft,
        backLabel = "Back",
        showRotate = false,
        actions = {
            IconPressButton(
                icon = Lucide.Download,
                label = "Copy the log files to the Download folder",
                bordered = true,
                onClick = {
                    AppLog.flush()
                    val copied = AppLog.copyAllToDownloads(context)
                    results["Log"] = "Copied $copied files to Download/Margin"
                },
            )
        },
    ) {
        // Every test gets the same room, and the list works out how many fit.
        // With a fixed count of seven, the last tests fell off the screen.
        PagedList(
            items = tests,
            pageSize = 4,
            rowHeight = TestRowHeight,
            modifier = Modifier.weight(1f),
        ) { _, test ->
            // The two lines every answer gets: one in the log, one on the row.
            fun report(answer: String) {
                AppLog.i(TAG, "=== ${test.label}: $answer")
                results[test.label] = answer
            }
            EinkRow(
                onClick = {
                    AppLog.i(TAG, "--- ${test.label}")
                    if (test.vendor) {
                        // A vendor call can wait on a binder call, so it never
                        // runs on the main thread.
                        results[test.label] = "Running"
                        EinkThread.run {
                            val answer = runCatching { test.run(context, device) }
                                .getOrElse { "crashed in our own code: ${it.javaClass.simpleName} ${it.message}" }
                            mainHandler.post { report(answer) }
                        }
                    } else {
                        val answer = runCatching { test.run(context, device) }
                            .getOrElse { "crashed in our own code: ${it.javaClass.simpleName} ${it.message}" }
                        report(answer)
                    }
                },
            ) { pressed ->
                val colour = if (pressed) io.github.foxesrcool1.margin.design.EinkColors.Paper
                else io.github.foxesrcool1.margin.design.EinkColors.Ink
                EinkText(text = test.label, style = EinkType.rowTitle.copy(color = colour), maxLines = 1)
                EinkText(
                    text = results[test.label] ?: "Not run yet",
                    style = EinkType.help.copy(color = colour),
                    maxLines = 2,
                )
            }
            HairlineDivider(color = io.github.foxesrcool1.margin.design.EinkColors.Faded)
        }

        EinkText(text = results["Log"].orEmpty(), style = EinkType.help, maxLines = 1)
    }
}

/** The name of a test, two lines of its answer, and the rule. */
private val TestRowHeight = 96.dp

private fun buildTests(): List<DeviceTest> {
    val tests = mutableListOf<DeviceTest>()

    tests += DeviceTest("1. Read device info", vendor = true) { context, device ->
        val metrics = context.resources.displayMetrics
        val lines = listOf(
            "Display" to "${metrics.widthPixels} x ${metrics.heightPixels} px",
            "Density" to "${metrics.densityDpi} dpi, scale ${metrics.density}",
            "Font scale" to context.resources.configuration.fontScale.toString(),
            "Android" to "API ${android.os.Build.VERSION.SDK_INT}, ${android.os.Build.DISPLAY}",
            "Target SDK" to context.applicationInfo.targetSdkVersion.toString(),
        ) + device.deviceInfo()
        lines.forEach { (key, value) -> AppLog.i(TAG, "$key: $value") }
        "${lines.size} facts written to the log. Layer: ${device.name}."
    }

    tests += DeviceTest("2. List the vendor API in the log", vendor = true) { _, device ->
        val viwoods = device as? ViwoodsEinkDevice ?: return@DeviceTest "No ViWoods layer on this device"
        viwoods.logVendorApi()
        "Written to the log"
    }

    RefreshMode.entries.forEach { mode ->
        tests += DeviceTest("3. Picture mode: ${mode.label}", vendor = true) { _, device ->
            val result = device.setRefreshMode(mode)
            "$result. Now reads ${device.refreshMode()?.label ?: "unknown"}. Turn a page and watch."
        }
    }

    tests += DeviceTest("4. Full refresh now, vendor mode only", vendor = true) { _, device -> device.fullRefresh().toString() }
    tests += DeviceTest("4b. Force refresh, black flash") { context, _ ->
        val activity = context.findActivity() ?: return@DeviceTest "No activity"
        io.github.foxesrcool1.margin.core.eink.ScreenRefresh.run(activity)
        "The screen went black once. Were the grey marks gone after?"
    }

    tests += DeviceTest("5. Pen canvas: this app draws (baseline)") { context, _ ->
        context.startActivity(PenTestActivity.intent(context, PenTestActivity.MODE_APP))
        "Opened"
    }
    tests += DeviceTest("5. Pen canvas: Jetpack Ink (baseline)") { context, _ ->
        context.startActivity(PenTestActivity.intent(context, PenTestActivity.MODE_JETPACK))
        "Opened. Debug builds only."
    }
    tests += DeviceTest("6. Pen canvas: Fast 1 (path A, initWriting)") { context, _ ->
        context.startActivity(PenTestActivity.intent(context, PenTestActivity.MODE_WRITING))
        "Opened. If the app closed by itself, open this screen again and run test 8."
    }
    tests += DeviceTest("7. Pen canvas: Fast 2 (path B, AutoDraw)") { context, _ ->
        context.startActivity(PenTestActivity.intent(context, PenTestActivity.MODE_AUTODRAW))
        "Opened"
    }

    tests += DeviceTest("7b. The real ink engine, on a test note") { context, _ ->
        context.startActivity(
            io.github.foxesrcool1.margin.ui.ink.InkNoteActivity.intent(
                context,
                "notes/Ink test.inknote",
                "Ink test",
            ),
        )
        "Opened. It uses the pen setting from Settings, Pen."
    }

    tests += DeviceTest("8. Fast pen crash guard") { context, _ ->
        val guard = EinkDevices.guard(context)
        FastPenPath.entries.joinToString(". ") { "${it.name}: ${guard.state(it).name}" }
    }
    tests += DeviceTest("8. Reset the crash guard") { context, _ ->
        val guard = EinkDevices.guard(context)
        FastPenPath.entries.forEach(guard::reset)
        "Both paths may be tried again"
    }

    tests += DeviceTest("9. Home role") { context, _ ->
        val roles = context.getSystemService(RoleManager::class.java)
        val available = runCatching { roles?.isRoleAvailable(RoleManager.ROLE_HOME) }.getOrNull()
        val held = runCatching { roles?.isRoleHeld(RoleManager.ROLE_HOME) }.getOrNull()
        val homeSettings = DefaultLauncher.homeSettingsIntent(context) != null
        val allSettings = DefaultLauncher.allSettingsIntent(context) != null
        "ROLE_HOME available=$available held=$held. Home settings screen=$homeSettings. " +
            "Settings app=$allSettings. Is default now=${DefaultLauncher.isDefault(context)}"
    }

    tests += DeviceTest("10. Find ViWoods settings and the stock launcher") { context, _ ->
        val manager = context.packageManager
        val homes = manager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_ALL,
        ).map { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
        homes.forEach { AppLog.i(TAG, "Home app: $it") }

        val launchable = manager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL,
        ).map { "${it.activityInfo.packageName}/${it.activityInfo.name} (${it.loadLabel(manager)})" }
        launchable.forEach { AppLog.i(TAG, "Launchable: $it") }

        val settings = manager.queryIntentActivities(Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_ALL)
            .map { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
        settings.forEach { AppLog.i(TAG, "Answers ACTION_SETTINGS: $it") }

        val vendor = launchable.filter { name ->
            listOf("viwoods", "wisky", "setting").any { name.contains(it, ignoreCase = true) }
        }
        "${homes.size} home apps, ${launchable.size} launchable, ${settings.size} settings. " +
            "Vendor looking: ${vendor.take(4).joinToString()}"
    }

    return tests
}
