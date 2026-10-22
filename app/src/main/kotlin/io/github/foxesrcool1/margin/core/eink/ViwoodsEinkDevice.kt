package io.github.foxesrcool1.margin.core.eink

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import io.github.foxesrcool1.margin.core.log.AppLog
import java.util.concurrent.TimeUnit

private const val TAG = "ViwoodsEinkDevice"

/**
 * The ViWoods e-ink platform, reached by reflection.
 *
 * There is no public SDK. Everything here is written from the facts in the
 * public notes at `jdkruzr/ViwoodsAppDev`. No code was copied from there.
 * Those notes contradict themselves in places, so **every call in this file
 * counts as unverified** until the owner has run the device test screen and
 * sent the log back. See `docs/decisions/0007-viwoods-ink-and-refresh.md`.
 *
 * Three ways in, tried in this order:
 *
 * 1. The wrapper object `android.os.enote.ENoteSetting.getInstance()`.
 * 2. The binder service behind it, first through its own `Stub.asInterface`
 *    and then with a raw `transact` and the documented transaction codes.
 * 3. The shell command `service call`, which the notes say works from an app.
 *
 * The class names and the service name are constructor arguments, so the unit
 * tests can point this at a fake class of the same shape.
 */
class ViwoodsEinkDevice(
    private val guard: FastPenGuard?,
    private val settingClassName: String = "android.os.enote.ENoteSetting",
    private val binderInterfaceName: String = "android.os.enote.IENoteSetting",
    private val serviceName: String = "ENoteSetting",
    private val allowShell: Boolean = true,
    private val runLater: (delayMillis: Long, action: () -> Unit) -> Unit = { delay, action ->
        Handler(Looper.getMainLooper()).postDelayed({ EinkThread.run(action) }, delay)
    },
) : EinkDevice {

    override val name: String = "ViWoods"

    private val wrapper: VendorObject? by lazy {
        VendorObject.load(settingClassName)
            .onFailure { AppLog.w(TAG, "No vendor class $settingClassName: ${it.shortReason()}") }
            .getOrNull()
    }

    private val binder: IBinder? by lazy { findBinder() }

    private val binderInterface: VendorObject? by lazy { findBinderInterface() }

    override val hasVendorControl: Boolean
        get() = wrapper != null

    @Volatile
    override var activeFastPen: FastPenPath? = null
        private set

    @Volatile
    private var shellFailed = false

    private var modeBeforeFastPen: Int? = null
    private var lastRegion: Rect? = null
    private var lastExcluded: List<Rect> = emptyList()

    // -- Facts --------------------------------------------------------------

    override fun deviceInfo(): List<Pair<String, String>> {
        val info = mutableListOf<Pair<String, String>>()
        info += "Device layer" to name
        info += "Manufacturer" to android.os.Build.MANUFACTURER.orEmpty()
        info += "Model" to android.os.Build.MODEL.orEmpty()
        info += "Vendor class found" to (wrapper != null).toString()
        info += "Binder service found" to (binder != null).toString()
        info += "Binder interface found" to (binderInterface != null).toString()

        listOf(
            "Picture mode" to "getPictureMode",
            "Real panel mode" to "getCurrRealEpdMode",
            "Wanted panel mode" to "getCurrShouldMode",
            "Wave version" to "getWaveVersion",
            "T1000 version" to "getT1000Version",
            "Wacom version" to "getWacomVersion",
            "Temperature" to "getTemperature",
        ).forEach { (label, method) ->
            val value = wrapper?.call(method)?.fold({ it?.toString() ?: "null" }, { "failed: ${it.shortReason()}" })
            info += label to (value ?: "no vendor class")
        }

        listOf(
            "ro.eink.model",
            "ro.eink.type",
            "persist.eink.mode_default",
            "persist.sys.focusmonitor.config",
        ).forEach { key -> info += key to systemProperty(key) }

        return info
    }

    /** Writes every method of the vendor classes to the log. The spike needs this once. */
    fun logVendorApi() {
        val found = wrapper
        if (found == null) {
            AppLog.w(TAG, "logVendorApi: no vendor class")
        } else {
            AppLog.i(TAG, "Methods of $settingClassName:")
            found.describeMethods().forEach { AppLog.i(TAG, "  $it") }
        }
        val remote = binderInterface
        if (remote == null) {
            AppLog.w(TAG, "logVendorApi: no binder interface")
        } else {
            AppLog.i(TAG, "Methods of $binderInterfaceName:")
            remote.describeMethods().forEach { AppLog.i(TAG, "  $it") }
        }
    }

    /**
     * One raw call on the wrapper, for the test screen. The spike has to try
     * calls nobody has documented well, and a new build for each one would
     * cost the owner an evening.
     */
    fun rawWrapperCall(method: String, vararg args: Any?): EinkCallResult {
        val found = wrapper ?: return log(EinkCallResult(method, false, "no vendor class"))
        val shown = "$method(${args.joinToString(", ")})"
        return log(
            found.call(method, *args).fold(
                { EinkCallResult(shown, true, "returned $it") },
                { EinkCallResult(shown, false, it.shortReason()) },
            ),
        )
    }

    // -- Refresh ------------------------------------------------------------

    override fun refreshMode(): RefreshMode? =
        (wrapper?.call("getPictureMode")?.getOrNull() as? Int)?.let(RefreshMode::fromVendorCode)

    override fun setRefreshMode(mode: RefreshMode): EinkCallResult = setPictureMode(mode.vendorCode)

    private fun setPictureMode(code: Int): EinkCallResult {
        val call = "setPictureMode($code)"
        val found = wrapper
        if (found != null) {
            val viaWrapper = found.call("setPictureMode", code)
            if (viaWrapper.isSuccess) {
                return log(EinkCallResult(call, true, "wrapper returned ${viaWrapper.getOrNull()}"))
            }
            AppLog.w(TAG, "$call failed on the wrapper: ${viaWrapper.exceptionOrNull()?.shortReason()}")
        }
        return binderCall(call, "setPictureMode", TXN_SET_PICTURE_MODE, listOf(code))
    }

    override fun fullRefresh(): EinkCallResult {
        val before = wrapper?.call("getPictureMode")?.getOrNull() as? Int
        val result = setPictureMode(RefreshMode.Full.vendorCode)
        if (result.ok) {
            // Nobody has written down whether the full refresh mode is one
            // repaint or a mode that stays. Going back by hand is right in
            // both cases.
            val restoreTo = before?.takeIf { it != RefreshMode.Full.vendorCode }
                ?: RefreshMode.Reading.vendorCode
            runLater(FULL_REFRESH_RESTORE_MILLIS) { setPictureMode(restoreTo) }
        }
        return result.copy(call = "fullRefresh")
    }

    // -- Fast pen -------------------------------------------------------------

    override fun startFastPen(
        context: Context,
        path: FastPenPath,
        drawRegion: Rect,
        excluded: List<Rect>,
    ): EinkCallResult {
        val call = "startFastPen(${path.name})"
        if (wrapper == null) return log(EinkCallResult(call, false, "no vendor class"))
        if (guard != null && !guard.mayTry(path)) {
            return log(EinkCallResult(call, false, "refused: this path crashed the app before. Reset it on the device test screen."))
        }
        if (activeFastPen != null) stopFastPen()

        guard?.markTrying(path)
        modeBeforeFastPen = wrapper?.call("getPictureMode")?.getOrNull() as? Int
        lastRegion = Rect(drawRegion)
        lastExcluded = excluded.map(::Rect)

        val steps = when (path) {
            FastPenPath.Writing -> startWritingPath(context, excluded)
            FastPenPath.AutoDraw -> startAutoDrawPath(drawRegion, excluded)
        }
        // Reaching this line means no native crash.
        guard?.markFine(path)

        val failed = steps.filterNot { it.ok }
        val ok = steps.isNotEmpty() && failed.isEmpty()
        if (steps.any { it.ok }) activeFastPen = path
        val detail = if (ok) {
            "${steps.size} calls went through"
        } else {
            "failed: " + failed.joinToString("; ") { "${it.call} (${it.detail})" }
        }
        return log(EinkCallResult(call, ok, detail))
    }

    /** Path A. The notes say these two calls are all it takes at target SDK 30. */
    private fun startWritingPath(context: Context, excluded: List<Rect>): List<EinkCallResult> {
        val steps = mutableListOf<EinkCallResult>()
        steps += setPictureMode(RefreshMode.Fast.vendorCode)
        steps += rawWrapperCall("setApplicationContext", context.applicationContext)
        steps += rawWrapperCall("initWriting")
        // A toolbar must stay a toolbar. Nobody has tested whether path A
        // listens to these rectangles, so a failure here is only logged.
        excluded.forEach { binderCall("addUnAutoDrawRect($it)", "addUnAutoDrawRect", TXN_ADD_UN_AUTO_DRAW_RECT, listOf(it)) }
        return steps
    }

    /** Path B. The order is the one the notes give. */
    private fun startAutoDrawPath(region: Rect, excluded: List<Rect>): List<EinkCallResult> {
        val steps = mutableListOf<EinkCallResult>()
        steps += setPictureMode(RefreshMode.Fast.vendorCode)
        steps += binderCall("setT1000AutoDrawEnable(true)", "setT1000AutoDrawEnable", TXN_SET_AUTO_DRAW_ENABLE, listOf(true))
        steps += binderCall("setAllRegionUnAutoDraw(false)", "setAllRegionUnAutoDraw", TXN_SET_ALL_REGION_UN_AUTO_DRAW, listOf(false))
        steps += setPenTool(PenTool.Pen)
        steps += binderCall("addAutoDrawRect($region)", "addAutoDrawRect", TXN_ADD_AUTO_DRAW_RECT, listOf(region))
        excluded.forEach {
            steps += binderCall("addUnAutoDrawRect($it)", "addUnAutoDrawRect", TXN_ADD_UN_AUTO_DRAW_RECT, listOf(it))
        }
        return steps
    }

    override fun stopFastPen(): EinkCallResult {
        val path = activeFastPen ?: return EinkCallResult("stopFastPen", true, "was not running")
        val steps = mutableListOf<EinkCallResult>()
        when (path) {
            FastPenPath.Writing -> steps += rawWrapperCall("exitWriting")
            FastPenPath.AutoDraw -> {
                lastRegion?.let {
                    steps += binderCall("removeAutoDrawRect($it)", "removeAutoDrawRect", TXN_REMOVE_AUTO_DRAW_RECT, listOf(it))
                }
                steps += binderCall("setAllRegionUnAutoDraw(true)", "setAllRegionUnAutoDraw", TXN_SET_ALL_REGION_UN_AUTO_DRAW, listOf(true))
                steps += binderCall("setT1000AutoDrawEnable(false)", "setT1000AutoDrawEnable", TXN_SET_AUTO_DRAW_ENABLE, listOf(false))
            }
        }
        lastExcluded.forEach {
            binderCall("removeUnAutoDrawRect($it)", "removeUnAutoDrawRect", TXN_REMOVE_UN_AUTO_DRAW_RECT, listOf(it))
        }
        steps += setPictureMode(modeBeforeFastPen ?: RefreshMode.Reading.vendorCode)
        activeFastPen = null
        lastRegion = null
        lastExcluded = emptyList()
        val failed = steps.filterNot { it.ok }
        return log(
            EinkCallResult(
                "stopFastPen(${path.name})",
                failed.isEmpty(),
                if (failed.isEmpty()) "stopped" else failed.joinToString("; ") { "${it.call} (${it.detail})" },
            ),
        )
    }

    override fun setPenTool(tool: PenTool): EinkCallResult {
        val code = when (tool) {
            PenTool.Pen -> TOOL_PEN
            PenTool.Eraser -> TOOL_ERASER
        }
        val call = "setAutoDrawToolType($code)"
        val viaWrapper = wrapper?.call("setAutoDrawToolType", code)
        if (viaWrapper?.isSuccess == true) return log(EinkCallResult(call, true, "wrapper"))
        return binderCall(call, "setAutoDrawToolType", TXN_SET_AUTO_DRAW_TOOL_TYPE, listOf(code))
    }

    override fun setPenWidthRange(min: Int, max: Int): EinkCallResult {
        val call = "setAutoDrawPenWidthRange($min, $max)"
        val viaWrapper = wrapper?.call("setAutoDrawPenWidthRange", min, max)
        if (viaWrapper?.isSuccess == true) return log(EinkCallResult(call, true, "wrapper"))
        return binderCall(call, "setAutoDrawPenWidthRange", TXN_SET_AUTO_DRAW_PEN_WIDTH_RANGE, listOf(min, max))
    }

    // -- The binder, three ways -------------------------------------------------

    private fun binderCall(call: String, method: String, code: Int, args: List<Any>): EinkCallResult {
        val reasons = mutableListOf<String>()

        binderInterface?.let { remote ->
            val result = remote.call(method, *args.toTypedArray())
            if (result.isSuccess) return log(EinkCallResult(call, true, "binder interface returned ${result.getOrNull()}"))
            reasons += "interface: ${result.exceptionOrNull()?.shortReason()}"
        }

        binder?.let { remote ->
            val result = runCatching { transact(remote, code, args) }
            if (result.isSuccess) return log(EinkCallResult(call, true, "raw transact $code"))
            reasons += "transact: ${result.exceptionOrNull()?.shortReason()}"
        }

        if (allowShell && !shellFailed) {
            val result = runCatching { shellServiceCall(code, args) }
            if (result.isSuccess) return log(EinkCallResult(call, true, "service call: ${result.getOrNull()}"))
            // A shell that failed once will fail again, and each try costs a process and a wait.
            shellFailed = true
            reasons += "shell: ${result.exceptionOrNull()?.shortReason()}"
        }

        if (reasons.isEmpty()) reasons += "no binder service named $serviceName"
        return log(EinkCallResult(call, false, reasons.joinToString(" | ")))
    }

    private fun findBinder(): IBinder? = runCatching {
        val manager = Class.forName("android.os.ServiceManager")
        manager.getMethod("getService", String::class.java).invoke(null, serviceName) as? IBinder
    }.onFailure { AppLog.w(TAG, "ServiceManager.getService($serviceName): ${it.shortReason()}") }.getOrNull()

    private fun findBinderInterface(): VendorObject? {
        val remote = binder ?: return null
        return runCatching {
            val stub = Class.forName("$binderInterfaceName\$Stub")
            val proxy = stub.getMethod("asInterface", IBinder::class.java).invoke(null, remote)
                ?: throw IllegalStateException("asInterface returned null")
            VendorObject(proxy, proxy.javaClass)
        }.onFailure { AppLog.w(TAG, "No binder interface: ${it.shortReason()}") }.getOrNull()
    }

    /**
     * The parcel layout is the one AIDL makes: a boolean and an int are one
     * int each, and a Rect is a "not null" marker and then its four sides.
     */
    private fun transact(remote: IBinder, code: Int, args: List<Any>) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(remote.interfaceDescriptor ?: binderInterfaceName)
            args.forEach { arg ->
                when (arg) {
                    is Boolean -> data.writeInt(if (arg) 1 else 0)
                    is Int -> data.writeInt(arg)
                    is Rect -> {
                        data.writeInt(1)
                        data.writeInt(arg.left)
                        data.writeInt(arg.top)
                        data.writeInt(arg.right)
                        data.writeInt(arg.bottom)
                    }
                    else -> throw IllegalArgumentException("cannot send ${arg.javaClass.simpleName}")
                }
            }
            val sent = remote.transact(code, data, reply, 0)
            if (!sent) throw IllegalStateException("transact $code was not understood")
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun shellServiceCall(code: Int, args: List<Any>): String {
        val words = mutableListOf("service", "call", serviceName, code.toString())
        args.forEach { arg ->
            when (arg) {
                is Boolean -> words += listOf("i32", if (arg) "1" else "0")
                is Int -> words += listOf("i32", arg.toString())
                is Rect -> words += listOf("i32", "1", "i32", "${arg.left}", "i32", "${arg.top}", "i32", "${arg.right}", "i32", "${arg.bottom}")
                else -> throw IllegalArgumentException("cannot send ${arg.javaClass.simpleName}")
            }
        }
        val process = ProcessBuilder(words).redirectErrorStream(true).start()
        if (!process.waitFor(SHELL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("service call did not finish")
        }
        val output = process.inputStream.bufferedReader().readText().trim().take(120)
        if (process.exitValue() != 0 || !output.contains("Parcel")) {
            throw IllegalStateException("exit ${process.exitValue()}: $output")
        }
        return output
    }

    private fun systemProperty(key: String): String = runCatching {
        val type = Class.forName("android.os.SystemProperties")
        type.getMethod("get", String::class.java).invoke(null, key) as? String
    }.getOrNull().orEmpty().ifEmpty { "(empty)" }

    private fun log(result: EinkCallResult): EinkCallResult {
        if (result.ok) AppLog.i(TAG, result.toString()) else AppLog.w(TAG, result.toString())
        return result
    }

    companion object {
        /** How long the full refresh gets before the old mode comes back. */
        const val FULL_REFRESH_RESTORE_MILLIS = 700L

        private const val SHELL_TIMEOUT_MILLIS = 500L

        private const val TOOL_PEN = 2
        private const val TOOL_ERASER = 4

        // Transaction codes of IENoteSetting, from the public notes.
        private const val TXN_SET_PICTURE_MODE = 13
        private const val TXN_SET_AUTO_DRAW_ENABLE = 20
        private const val TXN_SET_AUTO_DRAW_TOOL_TYPE = 21
        private const val TXN_SET_AUTO_DRAW_PEN_WIDTH_RANGE = 23
        private const val TXN_ADD_AUTO_DRAW_RECT = 24
        private const val TXN_REMOVE_AUTO_DRAW_RECT = 25
        private const val TXN_ADD_UN_AUTO_DRAW_RECT = 26
        private const val TXN_REMOVE_UN_AUTO_DRAW_RECT = 27
        private const val TXN_SET_ALL_REGION_UN_AUTO_DRAW = 28
    }
}
