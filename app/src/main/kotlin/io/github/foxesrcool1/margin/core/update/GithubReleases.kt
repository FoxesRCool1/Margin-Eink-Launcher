package io.github.foxesrcool1.margin.core.update

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** What a check found. */
sealed interface UpdateCheck {

    data class Available(val release: ReleaseInfo, val asset: ReleaseAsset) : UpdateCheck

    /** [newest] is the newest version on GitHub that this build could take, when there is one. */
    data class UpToDate(val newest: AppVersion?) : UpdateCheck

    /** [tokenProblem] is true when an access token, or a better one, would help. */
    data class Failed(val message: String, val tokenProblem: Boolean = false) : UpdateCheck
}

/**
 * Talks to the GitHub Releases of one repository.
 *
 * The app goes online here and nowhere else, and only when the user asks.
 * It sends nothing about the user or the tablet: a GET for the list of
 * releases, and a GET for the one file it wants.
 *
 * No Android class is used, so plain JUnit tests cover it with a fake
 * [UpdateHttp].
 */
class GithubReleases(
    private val http: UpdateHttp,
    /** "owner/name". */
    private val repository: String,
    private val userAgent: String,
    private val apiBase: String = "https://api.github.com",
) {

    fun check(installed: AppVersion, flavor: String, debug: Boolean, token: String?): UpdateCheck {
        val answer = try {
            http.getText("$apiBase/repos/$repository/releases?per_page=$PAGE_SIZE", headers(token, ACCEPT_JSON))
        } catch (error: IOException) {
            return UpdateCheck.Failed("No connection. Check the Wi-Fi. (${error.message})")
        }

        if (answer.code != 200) return failureFor(answer, hasToken = !token.isNullOrBlank())

        val releases = runCatching { ReleaseParser.parseList(answer.body) }.getOrElse {
            return UpdateCheck.Failed("Could not read the answer.")
        }
        val newest = UpdateAssets.newestFor(releases, flavor, debug)
            ?: return UpdateCheck.UpToDate(newest = null)

        return if (newest.first.version > installed) {
            UpdateCheck.Available(newest.first, newest.second)
        } else {
            UpdateCheck.UpToDate(newest = newest.first.version)
        }
    }

    /**
     * Fetches [asset] into [directory] and checks it against the SHA-256 that
     * GitHub lists for it. The file only gets its real name once it is whole
     * and correct, so a file with that name can be trusted to be both.
     */
    fun download(asset: ReleaseAsset, token: String?, directory: File, onPercent: (Int) -> Unit): Result<File> =
        runCatching {
            // The name comes from the network. Keep only the last part of it.
            val safeName = File(asset.name).name
            val target = File(directory, safeName)
            if (target.isFile && matches(target, asset)) return@runCatching target

            directory.mkdirs()
            val part = File(directory, "$safeName.part")
            var lastPercent = -1
            val code = http.download(asset.apiUrl, headers(token, ACCEPT_BINARY), part) { bytes ->
                if (asset.sizeBytes > 0) {
                    val percent = ((bytes * 100) / asset.sizeBytes).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onPercent(percent)
                    }
                }
            }
            if (code != 200) {
                part.delete()
                throw IOException("GitHub answered HTTP $code for the file.")
            }
            if (!matches(part, asset)) {
                part.delete()
                throw IOException("The file did not arrive whole. Try again.")
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("The file could not be saved.")
            target
        }

    private fun matches(file: File, asset: ReleaseAsset): Boolean {
        if (asset.sizeBytes > 0 && file.length() != asset.sizeBytes) return false
        val expected = asset.sha256 ?: return file.length() > 0
        return sha256Of(file) == expected
    }

    private fun headers(token: String?, accept: String): Map<String, String> = buildMap {
        put("Accept", accept)
        put("X-GitHub-Api-Version", API_VERSION)
        put("User-Agent", userAgent)
        if (!token.isNullOrBlank()) put("Authorization", "Bearer ${token.trim()}")
    }

    private fun failureFor(answer: HttpText, hasToken: Boolean): UpdateCheck.Failed = when (answer.code) {
        401 -> UpdateCheck.Failed("GitHub refused the token.", tokenProblem = true)

        403, 429 ->
            if (answer.headers["x-ratelimit-remaining"] == "0") {
                UpdateCheck.Failed("Limit is 60 checks an hour. Try again later.")
            } else {
                UpdateCheck.Failed("Refused, HTTP ${answer.code}.", tokenProblem = hasToken)
            }

        // GitHub says "not found" for a private repository, so as not to give away that it exists.
        404 ->
            if (hasToken) {
                UpdateCheck.Failed("Repository not found with this token.", tokenProblem = true)
            } else {
                UpdateCheck.Failed(
                    "Repository not found. If it is private, add a token.",
                    tokenProblem = true,
                )
            }

        else -> UpdateCheck.Failed("GitHub error, HTTP ${answer.code}.")
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val API_VERSION = "2022-11-28"
        private const val ACCEPT_JSON = "application/vnd.github+json"
        private const val ACCEPT_BINARY = "application/octet-stream"

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
