package io.github.foxesrcool1.margin.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.foxesrcool1.margin.core.log.AppLog
import java.time.LocalDateTime

/**
 * The time, updated once a minute.
 *
 * E-ink rule 7: change as few pixels as possible, and update the clock once a
 * minute only. `ACTION_TIME_TICK` fires on the minute, so there is no timer and
 * no polling. The broadcast only reaches a receiver registered in code, never
 * one declared in the manifest.
 *
 * It listens only while the screen is on show. With the tablet asleep, or a
 * book in front of Home, nothing listens, so Android has no reason to wake
 * the app each minute. The time is read again the moment Home comes back.
 */
@Composable
fun rememberMinuteClock(): State<LocalDateTime> {
    val context = LocalContext.current
    val time = remember { mutableStateOf(LocalDateTime.now()) }

    LifecycleStartEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(received: Context?, intent: Intent?) {
                time.value = LocalDateTime.now()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        time.value = LocalDateTime.now()
        onStopOrDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    return time
}

/** Battery percent, or null when the tablet does not report one. */
fun batteryPercent(context: Context): Int? = runCatching {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
}.getOrNull()

/** True when the tablet is on Wi-Fi right now. */
fun onWifi(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
    val network = manager?.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
}.getOrDefault(false)

/**
 * Opens Android's internet panel, where Wi-Fi goes off in one tap, or the Wi-Fi
 * settings where a tablet has no such panel. Never throws.
 */
fun openInternetPanel(context: Context) {
    runCatching { context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        .onFailure { AppLog.w("HomeStatus", "Could not open the Wi-Fi panel or the Wi-Fi settings", it) }
}
