package io.github.foxesrcool1.margin.core.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Answers from a script, and remembers what it was asked. */
class FakeUpdateHttp(
    var text: () -> HttpText = { HttpText(200, "[]", emptyMap()) },
    var fileCode: Int = 200,
    var fileBytes: ByteArray = ByteArray(0),
) : UpdateHttp {

    val requests = mutableListOf<Pair<String, Map<String, String>>>()

    override fun getText(url: String, headers: Map<String, String>): HttpText {
        requests += url to headers
        return text()
    }

    override fun download(url: String, headers: Map<String, String>, target: File, onBytes: (Long) -> Unit): Int {
        requests += url to headers
        if (fileCode == 200) {
            target.parentFile?.mkdirs()
            target.writeBytes(fileBytes)
            // In two steps, the way a real download arrives.
            onBytes(fileBytes.size / 2L)
            onBytes(fileBytes.size.toLong())
        }
        return fileCode
    }
}

class GithubReleasesTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val http = FakeUpdateHttp()
    private val github = GithubReleases(http, repository = "o/r", userAgent = "Margin/test")
    private val installed = AppVersion(0, 1, 0)

    private fun answer(code: Int, body: String = "", headers: Map<String, String> = emptyMap()) {
        http.text = { HttpText(code, body, headers) }
    }

    @Test
    fun `a newer release with a file for this build is an update`() {
        answer(200, UpdateTestData.releasesJson())
        val found = github.check(installed, "viwoods", debug = true, token = null) as UpdateCheck.Available
        assertEquals("v0.2.0", found.release.tag)
        assertEquals("margin-v0.2.0-viwoods-debug.apk", found.asset.name)
    }

    @Test
    fun `the same version is not an update, and neither is an older one`() {
        answer(200, UpdateTestData.releasesJson())
        assertEquals(
            UpdateCheck.UpToDate(AppVersion(0, 2, 0)),
            github.check(AppVersion(0, 2, 0), "viwoods", debug = true, token = null),
        )
        assertEquals(
            UpdateCheck.UpToDate(AppVersion(0, 2, 0)),
            github.check(AppVersion(0, 3, 0), "viwoods", debug = true, token = null),
        )
    }

    @Test
    fun `no release for this build is up to date with nothing to name`() {
        answer(200, "[]")
        assertEquals(UpdateCheck.UpToDate(null), github.check(installed, "viwoods", debug = true, token = null))
    }

    @Test
    fun `the request goes to the releases of the repository, with no token unless there is one`() {
        github.check(installed, "viwoods", debug = true, token = null)
        val (url, headers) = http.requests.single()
        assertEquals("https://api.github.com/repos/o/r/releases?per_page=20", url)
        assertEquals("application/vnd.github+json", headers["Accept"])
        assertEquals("Margin/test", headers["User-Agent"])
        assertFalse(headers.containsKey("Authorization"))

        github.check(installed, "viwoods", debug = true, token = " secret\n")
        assertEquals("Bearer secret", http.requests.last().second["Authorization"])
    }

    @Test
    fun `not found without a token asks for a token`() {
        answer(404, """{"message":"Not Found"}""")
        val failed = github.check(installed, "viwoods", debug = true, token = null) as UpdateCheck.Failed
        assertTrue(failed.tokenProblem)
        assertTrue(failed.message.contains("private"))
    }

    @Test
    fun `not found or refused with a token blames the token`() {
        answer(404)
        assertTrue((github.check(installed, "viwoods", true, "t") as UpdateCheck.Failed).tokenProblem)
        answer(401)
        assertTrue((github.check(installed, "viwoods", true, "t") as UpdateCheck.Failed).tokenProblem)
    }

    @Test
    fun `the hourly limit is named, and is not a token problem`() {
        answer(403, headers = mapOf("x-ratelimit-remaining" to "0"))
        val failed = github.check(installed, "viwoods", debug = true, token = null) as UpdateCheck.Failed
        assertFalse(failed.tokenProblem)
        assertTrue(failed.message.contains("60 checks"))
    }

    @Test
    fun `no network is a message and not a crash`() {
        http.text = { throw IOException("Unable to resolve host") }
        val failed = github.check(installed, "viwoods", debug = true, token = null) as UpdateCheck.Failed
        assertTrue(failed.message.contains("Wi-Fi"))
    }

    @Test
    fun `an answer that is not JSON is a message and not a crash`() {
        answer(200, "<html>captive portal</html>")
        assertTrue(github.check(installed, "viwoods", debug = true, token = null) is UpdateCheck.Failed)
    }

    // The download.

    private val bytes = ByteArray(5000) { (it % 251).toByte() }

    private fun assetFor(content: ByteArray, name: String = "margin-v0.2.0-viwoods-debug.apk"): ReleaseAsset {
        val file = temporary.newFile().apply { writeBytes(content) }
        return ReleaseAsset(name, content.size.toLong(), "https://api.github.com/a/13", GithubReleases.sha256Of(file))
    }

    @Test
    fun `a whole file gets its real name and no part file is left`() {
        http.fileBytes = bytes
        val folder = File(temporary.root, "updates")
        val percents = mutableListOf<Int>()

        val file = github.download(assetFor(bytes), token = "t", directory = folder) { percents += it }.getOrThrow()

        assertEquals("margin-v0.2.0-viwoods-debug.apk", file.name)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(listOf(file.name), folder.list()!!.toList())
        assertEquals(listOf(50, 100), percents)
        val (url, headers) = http.requests.single()
        assertEquals("https://api.github.com/a/13", url)
        assertEquals("application/octet-stream", headers["Accept"])
        assertEquals("Bearer t", headers["Authorization"])
    }

    @Test
    fun `a file with the wrong content is refused and removed`() {
        http.fileBytes = bytes.copyOf().also { it[10] = 0 }
        val folder = File(temporary.root, "updates")
        val result = github.download(assetFor(bytes), null, folder) {}
        assertTrue(result.isFailure)
        assertTrue(folder.list()!!.isEmpty())
    }

    @Test
    fun `a file that is cut short is refused and removed`() {
        http.fileBytes = bytes.copyOf(100)
        val folder = File(temporary.root, "updates")
        assertTrue(github.download(assetFor(bytes), null, folder) {}.isFailure)
        assertTrue(folder.list()!!.isEmpty())
    }

    @Test
    fun `an error code is a failure and leaves nothing behind`() {
        http.fileCode = 404
        val folder = File(temporary.root, "updates")
        val result = github.download(assetFor(bytes), null, folder) {}
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("404"))
        assertNull(folder.list()?.firstOrNull())
    }

    @Test
    fun `a file that is already there and whole is not fetched again`() {
        http.fileBytes = bytes
        val folder = File(temporary.root, "updates")
        val asset = assetFor(bytes)
        github.download(asset, null, folder) {}.getOrThrow()
        github.download(asset, null, folder) {}.getOrThrow()
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `a download that throws part way leaves no part file`() {
        val throwing = object : UpdateHttp {
            override fun getText(url: String, headers: Map<String, String>): HttpText = error("not used")

            override fun download(url: String, headers: Map<String, String>, target: File, onBytes: (Long) -> Unit): Int {
                target.writeBytes(bytes.copyOf(100))
                throw IOException("The connection dropped")
            }
        }
        val folder = File(temporary.root, "updates")
        val result = GithubReleases(throwing, repository = "o/r", userAgent = "Margin/test")
            .download(assetFor(bytes), null, folder) {}
        assertTrue(result.isFailure)
        assertTrue(folder.list()!!.isEmpty())
    }

    @Test
    fun `a file larger than the free space is refused before any request`() {
        val folder = File(temporary.root, "updates")
        val huge = ReleaseAsset("margin-v0.2.0-viwoods-debug.apk", Long.MAX_VALUE, "https://api.github.com/a/13", null)
        val result = github.download(huge, null, folder) {}
        assertEquals("Not enough space.", result.exceptionOrNull()?.message)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `a download address that is not on the API host is refused before any request`() {
        val folder = File(temporary.root, "updates")
        val elsewhere = ReleaseAsset("margin-v0.2.0-viwoods-debug.apk", bytes.size.toLong(), "https://evil.example/a/13", null)
        val result = github.download(elsewhere, "t", folder) {}
        assertEquals("The download address is not GitHub.", result.exceptionOrNull()?.message)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `a name with a path in it cannot leave the folder`() {
        http.fileBytes = bytes
        val folder = File(temporary.root, "updates")
        val file = github.download(assetFor(bytes, name = "../../evil-viwoods-debug.apk"), null, folder) {}.getOrThrow()
        assertEquals(folder.canonicalFile, file.canonicalFile.parentFile)
    }
}
