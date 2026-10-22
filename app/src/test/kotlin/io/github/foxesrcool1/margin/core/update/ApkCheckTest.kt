package io.github.foxesrcool1.margin.core.update

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkCheckTest {

    private val installed = ApkFacts("io.example.app", versionCode = 100, signers = setOf("debug-key"))

    private fun refusal(archive: ApkFacts?): String =
        (ApkCheck.judge(archive, installed) as ApkVerdict.Refused).message

    @Test
    fun `a newer build with the same key is fine`() {
        assertEquals(ApkVerdict.Ok, ApkCheck.judge(installed.copy(versionCode = 101), installed))
    }

    @Test
    fun `the same version again is fine, as Android allows it`() {
        assertEquals(ApkVerdict.Ok, ApkCheck.judge(installed, installed))
    }

    @Test
    fun `a file Android cannot read is refused`() {
        assertTrue(refusal(null).contains("not an Android app"))
    }

    @Test
    fun `another app is refused`() {
        assertTrue(refusal(installed.copy(packageName = "io.example.other")).contains("io.example.other"))
    }

    @Test
    fun `an older build is refused`() {
        assertTrue(refusal(installed.copy(versionCode = 99)).contains("older"))
    }

    @Test
    fun `a release build does not go over a debug build`() {
        assertTrue(refusal(installed.copy(versionCode = 101, signers = setOf("release-key"))).contains("different key"))
    }

    @Test
    fun `a rotated key still matches the key it came from`() {
        val rotated = installed.copy(versionCode = 101, signers = setOf("new-key", "debug-key"))
        assertEquals(ApkVerdict.Ok, ApkCheck.judge(rotated, installed))
    }

    @Test
    fun `when Android will not name the signers the choice is left to Android`() {
        assertEquals(ApkVerdict.Ok, ApkCheck.judge(installed.copy(versionCode = 101, signers = null), installed))
        assertEquals(ApkVerdict.Ok, ApkCheck.judge(installed.copy(versionCode = 101), installed.copy(signers = null)))
    }
}

class InstallMessagesTest {

    @Test
    fun `each failure has its own plain line`() {
        assertEquals(
            "Install cancelled.",
            InstallMessages.forStatus(PackageInstaller.STATUS_FAILURE_ABORTED, null),
        )
        assertTrue(InstallMessages.forStatus(PackageInstaller.STATUS_FAILURE_STORAGE, "").contains("free space"))
        assertTrue(InstallMessages.forStatus(PackageInstaller.STATUS_FAILURE_CONFLICT, null).contains("different key"))
    }

    @Test
    fun `what Android said is kept, because the owner has no logcat`() {
        val line = InstallMessages.forStatus(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_VERSION_DOWNGRADE")
        assertEquals("Install failed. (INSTALL_FAILED_VERSION_DOWNGRADE)", line)
    }
}
