package io.github.foxesrcool1.margin.core.update

import android.content.Context
import io.github.foxesrcool1.margin.BuildConfig
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The manager with a fake network. Work runs on the calling thread here, so
 * each verb has finished by the time it returns.
 */
@RunWith(RobolectricTestRunner::class)
class UpdateManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val http = FakeUpdateHttp()

    @Before
    fun setUp() {
        UpdateManager.reset()
        UpdateManager.dispatcher = Dispatchers.Unconfined
        UpdateManager.releasesFactory = { GithubReleases(http, "o/r", "test") }
        UpdateToken.save(context, "")
    }

    @After
    fun tearDown() {
        UpdateManager.reset()
        UpdateManager.dispatcher = Dispatchers.IO
    }

    private fun flavourJson(sha: String, size: Long): String {
        // The test runs as one flavour or the other, and as a debug build.
        val name = "margin-v99.0.0-${BuildConfig.FLAVOR}-debug.apk"
        return """[{"tag_name":"v99.0.0","draft":false,"prerelease":true,"body":"notes","assets":[
            {"name":"$name","size":$size,"state":"uploaded","url":"https://api.github.com/a/1","digest":"sha256:$sha"}]}]"""
    }

    @Test
    fun `a check that finds a newer release offers it`() {
        http.text = { HttpText(200, flavourJson("a".repeat(64), 10), emptyMap()) }
        UpdateManager.check(context)
        val state = UpdateManager.state.value as UpdateState.Available
        assertEquals(AppVersion(99, 0, 0), state.release.version)
    }

    @Test
    fun `a private repository ends in a failure that asks for a token`() {
        http.text = { HttpText(404, "", emptyMap()) }
        UpdateManager.check(context)
        val state = UpdateManager.state.value as UpdateState.Failed
        assertTrue(state.tokenProblem)
    }

    @Test
    fun `the saved token goes out with the check, and an empty one is no token`() {
        UpdateToken.save(context, "  test-token \n")
        assertEquals("test-token", UpdateToken.read(context))
        UpdateManager.check(context)
        assertEquals("Bearer test-token", http.requests.last().second["Authorization"])

        UpdateToken.save(context, "   ")
        assertNull(UpdateToken.read(context))
    }

    @Test
    fun `a download that is not an app is refused and removed`() {
        val bytes = "this is not an apk".toByteArray()
        val sha = File.createTempFile("sha", null).let { it.writeBytes(bytes); GithubReleases.sha256Of(it) }
        http.text = { HttpText(200, flavourJson(sha, bytes.size.toLong()), emptyMap()) }
        http.fileBytes = bytes

        UpdateManager.check(context)
        UpdateManager.download(context)

        val state = UpdateManager.state.value as UpdateState.Failed
        assertTrue(state.message, state.message.contains("not an Android app"))
        assertTrue(UpdateManager.downloadFolder(context).list().orEmpty().isEmpty())
    }

    @Test
    fun `a failed install keeps the file and says why`() {
        // A name the start-up tidy keeps. The app class starts that tidy on
        // its own thread in every test, so any other name is a race.
        val file = File(UpdateManager.downloadFolder(context).apply { mkdirs() }, "margin-v99.0.0-viwoods-debug.apk")
            .apply { writeText("x") }
        val release = ReleaseInfo("v99.0.0", AppVersion(99, 0, 0), "t", "", false, emptyList())
        UpdateManager.force(UpdateState.Ready(release, file))

        UpdateManager.onInstallFailed("The install was cancelled.")

        val state = UpdateManager.state.value as UpdateState.Ready
        assertTrue(state.handedOver)
        assertEquals("The install was cancelled.", state.note)
        assertTrue(file.exists())
    }

    @Test
    fun `a second install while the first is handed over does nothing`() {
        val file = File(UpdateManager.downloadFolder(context).apply { mkdirs() }, "margin-v99.0.0-viwoods-debug.apk")
            .apply { writeText("x") }
        val release = ReleaseInfo("v99.0.0", AppVersion(99, 0, 0), "t", "", false, emptyList())
        UpdateManager.force(UpdateState.Ready(release, file, handedOver = true, note = UpdateManager.INSTALLING_NOTE))

        UpdateManager.install(context)

        val state = UpdateManager.state.value as UpdateState.Ready
        assertTrue(state.handedOver)
        assertEquals(UpdateManager.INSTALLING_NOTE, state.note)
    }

    @Test
    fun `a retry after a failed install calls the installer again`() {
        val file = File(UpdateManager.downloadFolder(context).apply { mkdirs() }, "margin-v99.0.0-viwoods-debug.apk")
            .apply { writeText("x") }
        val release = ReleaseInfo("v99.0.0", AppVersion(99, 0, 0), "t", "", false, emptyList())
        UpdateManager.force(UpdateState.Ready(release, file, handedOver = true, note = "Install cancelled."))

        UpdateManager.install(context)

        val state = UpdateManager.state.value as UpdateState.Ready
        assertTrue(state.handedOver)
        assertNotEquals("Install cancelled.", state.note)
    }

    @Test
    fun `a check while a download is waiting to install does nothing`() {
        val file = File(UpdateManager.downloadFolder(context).apply { mkdirs() }, "margin-v99.0.0-viwoods-debug.apk")
            .apply { writeText("x") }
        val release = ReleaseInfo("v99.0.0", AppVersion(99, 0, 0), "t", "", false, emptyList())
        val ready = UpdateState.Ready(release, file)
        UpdateManager.force(ready)

        UpdateManager.check(context)

        assertEquals(ready, UpdateManager.state.value)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `install failure codes become plain sentences and other text passes through`() {
        assertEquals(
            "This build was signed with a different key. Uninstall Margin, then install the new file.",
            plainInstallFailure("INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match"),
        )
        assertEquals("Not enough space.", plainInstallFailure("INSTALL_FAILED_INSUFFICIENT_STORAGE"))
        assertEquals("Install cancelled.", plainInstallFailure("INSTALL_FAILED_ABORTED"))
        assertEquals("Install cancelled.", plainInstallFailure("STATUS_FAILURE_ABORTED"))
        assertEquals("Install cancelled.", plainInstallFailure("User cancelled the install"))
        assertEquals("Android blocked the install.", plainInstallFailure("Android blocked the install."))
    }

    @Test
    fun `a failure report with no download behind it still shows`() {
        UpdateManager.onInstallFailed("Android blocked the install.")
        assertEquals(UpdateState.Failed("Android blocked the install."), UpdateManager.state.value)
    }

    @Test
    fun `start-up removes a download that is no longer newer, and keeps one that is`() {
        val folder = UpdateManager.downloadFolder(context).apply { mkdirs() }
        val old = File(folder, "margin-v0.0.1-viwoods-debug.apk").apply { writeText("x") }
        val part = File(folder, "margin-v99.0.0-viwoods-debug.apk.part").apply { writeText("x") }
        val lan = File(folder, "lan-build.apk").apply { writeText("x") }
        val newer = File(folder, "margin-v99.0.0-viwoods-debug.apk").apply { writeText("x") }

        UpdateManager.settleAfterStart(context)

        assertFalse(old.exists())
        assertFalse(part.exists())
        assertFalse(lan.exists())
        assertTrue(newer.exists())
        assertEquals(BuildConfig.VERSION_CODE, UpdateToken.lastVersionCode(context))
    }
}
