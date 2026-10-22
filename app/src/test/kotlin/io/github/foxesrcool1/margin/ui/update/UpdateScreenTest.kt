package io.github.foxesrcool1.margin.ui.update

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.foxesrcool1.margin.core.update.AppVersion
import io.github.foxesrcool1.margin.core.update.ReleaseAsset
import io.github.foxesrcool1.margin.core.update.ReleaseInfo
import io.github.foxesrcool1.margin.core.update.UpdateState
import io.github.foxesrcool1.margin.design.EinkTheme
import io.github.foxesrcool1.margin.support.captureTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private val asset = ReleaseAsset("margin-v0.2.0-viwoods-debug.apk", 63_100_000, "https://x/1", null)

private val release = ReleaseInfo(
    tag = "v0.2.0",
    version = AppVersion(0, 2, 0),
    title = "Margin v0.2.0",
    notes = "## 0.2.0\n\n- The reader no longer opens on a blank page.\n" +
        "- A long note keeps its last line when the keyboard opens, and the\n" +
        "  cursor stays in view.\n- The habit streak counts a day that ends after midnight.",
    prerelease = true,
    assets = listOf(asset),
)

class UpdateStringsTest {

    @Test
    fun `notes lose their Markdown marks, the repeated version, and a wrapped bullet is one line again`() {
        assertEquals(
            "- The reader no longer opens on a blank page.\n" +
                "- A long note keeps its last line when the keyboard opens, and the cursor stays in view.\n" +
                "- The habit streak counts a day that ends after midnight.",
            UpdateStrings.plainNotes(release.notes),
        )
        assertEquals("", UpdateStrings.plainNotes("\n\n"))
    }

    @Test
    fun `a size is rounded to whole megabytes`() {
        assertEquals("60 MB", UpdateStrings.megabytes(63_100_000))
        assertEquals("0 MB", UpdateStrings.megabytes(0))
    }

    @Test
    fun `every state has a headline, and a failure shows its reason`() {
        val states = listOf(
            UpdateState.Idle,
            UpdateState.Checking,
            UpdateState.UpToDate(null),
            UpdateState.Available(release, asset),
            UpdateState.Downloading(release, asset, 40),
            UpdateState.Ready(release, File("x.apk")),
            UpdateState.Failed("No connection to GitHub."),
        )
        states.forEach { assertTrue(UpdateStrings.headline(it).isNotBlank()) }
        assertEquals("No connection to GitHub.", UpdateStrings.detail(UpdateState.Failed("No connection to GitHub.")))
        assertTrue(UpdateStrings.headline(UpdateState.Downloading(release, asset, 40)).contains("40%"))
    }
}

/** The update screen does not scroll, so each state has to fit the panel. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "sw480dp-w480dp-h640dp-port-xxhdpi")
class UpdateScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(state: UpdateState, name: String, installAllowed: Boolean = true, hasToken: Boolean = false) {
        compose.setContent {
            EinkTheme(botanicalArt = false) {
                UpdateContent(
                    state = state,
                    installAllowed = installAllowed,
                    hasToken = hasToken,
                    onBack = {},
                    onCheck = {},
                    onDownload = {},
                    onInstall = {},
                    onAllowInstalls = {},
                    onToken = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureTo(name)
    }

    @Test
    fun available() = shoot(UpdateState.Available(release, asset), "update_available")

    @Test
    fun downloading() = shoot(UpdateState.Downloading(release, asset, 40), "update_downloading")

    @Test
    fun upToDate() = shoot(UpdateState.UpToDate(AppVersion(0, 1, 0)), "update_up_to_date", hasToken = true)

    /** The tallest case: a long message, the token button, and the install permission still missing. */
    @Test
    fun privateRepositoryAndNoPermission() = shoot(
        UpdateState.Failed(
            "Repository not found. If it is private, add a token.",
            tokenProblem = true,
        ),
        "update_needs_token",
        installAllowed = false,
    )

    @Test
    fun installFailed() = shoot(
        UpdateState.Ready(
            release,
            File("x.apk"),
            handedOver = true,
            note = "Signed with a different key. (INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match)",
        ),
        "update_install_failed",
    )
}
