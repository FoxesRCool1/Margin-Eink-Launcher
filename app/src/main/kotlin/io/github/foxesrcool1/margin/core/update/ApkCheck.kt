package io.github.foxesrcool1.margin.core.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import io.github.foxesrcool1.margin.core.log.AppLog
import java.io.File
import java.security.MessageDigest

/** What is known about an APK file, or about the installed app. */
data class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    /** SHA-256 of each signing certificate. Null when Android would not say. */
    val signers: Set<String>?,
)

sealed interface ApkVerdict {
    data object Ok : ApkVerdict
    data class Refused(val message: String) : ApkVerdict
}

/**
 * Looks at a downloaded APK before Android is asked to install it.
 *
 * Android makes the same checks itself, and its word is final. But when it
 * says no, all the user sees is "App not installed", and this tablet has no
 * logcat to say why. So the app checks first and gives the reason in words.
 */
object ApkCheck {

    private const val TAG = "ApkCheck"

    /** The rules, with no Android class in them, so a plain test covers them. */
    fun judge(archive: ApkFacts?, installed: ApkFacts): ApkVerdict {
        if (archive == null) return ApkVerdict.Refused("The file is not an Android app.")

        if (archive.packageName != installed.packageName) {
            return ApkVerdict.Refused("Different app: ${archive.packageName}.")
        }
        if (archive.versionCode < installed.versionCode) {
            return ApkVerdict.Refused(
                "This is an older version.",
            )
        }
        val theirs = archive.signers
        val ours = installed.signers
        if (theirs != null && ours != null && theirs.intersect(ours).isEmpty()) {
            return ApkVerdict.Refused(
                "Signed with a different key.",
            )
        }
        return ApkVerdict.Ok
    }

    fun inspect(context: Context, apk: File): ApkVerdict {
        val packages = context.packageManager
        val archive = runCatching {
            packages.getPackageArchiveInfo(apk.absolutePath, SIGNER_FLAGS)?.let(::factsOf)
        }.onFailure { AppLog.w(TAG, "Could not read the downloaded APK", it) }.getOrNull()

        val installed = runCatching {
            factsOf(packages.getPackageInfo(context.packageName, SIGNER_FLAGS))
        }.onFailure { AppLog.w(TAG, "Could not read the installed app", it) }.getOrNull()
            // Without this there is nothing to compare with. Let Android decide.
            ?: return ApkVerdict.Ok

        val verdict = judge(archive, installed)
        AppLog.i(TAG, "Downloaded: $archive. Installed: $installed. Verdict: $verdict")
        return verdict
    }

    // Some Android versions leave `signingInfo` empty for a file on disk and
    // only fill the old `signatures` field, so both are asked for.
    @Suppress("DEPRECATION")
    private const val SIGNER_FLAGS = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun factsOf(info: PackageInfo): ApkFacts {
        val modern = info.signingInfo?.let { signing ->
            // The history is included so that a rotated key still matches the key it came from.
            signing.apkContentsSigners.orEmpty().toList() + signing.signingCertificateHistory.orEmpty().toList()
        }.orEmpty()
        val all: List<Signature> = modern.ifEmpty { info.signatures.orEmpty().toList() }
        return ApkFacts(
            packageName = info.packageName,
            versionCode = info.longVersionCode,
            signers = all.map { sha256Of(it.toByteArray()) }.toSet().ifEmpty { null },
        )
    }

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
