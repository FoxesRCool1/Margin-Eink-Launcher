package io.github.foxesrcool1.margin.core.apps

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppFoldersTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val kindle = "com.amazon.kindle/com.amazon.kindle.Main"
    private val libby = "com.overdrive.libby/com.overdrive.Main"

    @Test
    fun `a new folder is empty and the folders stay in A to Z order`() {
        val folders = AppFolders().withFolder("Tools").withFolder("reading")
        assertEquals(listOf("reading", "Tools"), folders.folders.map { it.name })
        assertTrue(folders.folders.all { it.apps.isEmpty() })
    }

    @Test
    fun `a blank name or a name that is already there changes nothing`() {
        val folders = AppFolders().withFolder("Reading")
        assertEquals(folders, folders.withFolder("   "))
        assertEquals(folders, folders.withFolder("reading"))
        assertEquals(folders, folders.withFolder("  READING "))
    }

    @Test
    fun `a name is trimmed, its spaces are made single, and it is cut at the limit`() {
        val folders = AppFolders().withFolder("  Books   and   papers ").withFolder("x".repeat(90))
        assertNotNull(folders.folder("Books and papers"))
        assertEquals(AppFolders.MAX_NAME_LENGTH, folders.folders.maxOf { it.name.length })
    }

    @Test
    fun `a tap puts an app into a folder and the next tap takes it out`() {
        val start = AppFolders().withFolder("Reading")
        val put = start.toggled("Reading", kindle).toggled("reading", libby)
        assertEquals(listOf(kindle, libby), put.folder("Reading")?.apps)
        assertEquals(listOf(libby), put.toggled("Reading", kindle).folder("Reading")?.apps)
    }

    @Test
    fun `an app can be in two folders, and each folder knows it`() {
        val folders = AppFolders().withFolder("Reading").withFolder("Offline")
            .toggled("Reading", kindle).toggled("Offline", kindle)
        assertEquals(listOf("Offline", "Reading"), folders.foldersOf(kindle))
        assertEquals(emptyList<String>(), folders.foldersOf(libby))
    }

    @Test
    fun `a rename keeps the apps, and refuses a name another folder has`() {
        val folders = AppFolders().withFolder("Reading").withFolder("Tools").toggled("Reading", kindle)
        val renamed = folders.renamed("Reading", "Books")
        assertNull(renamed.folder("Reading"))
        assertEquals(listOf(kindle), renamed.folder("Books")?.apps)
        assertEquals(folders, folders.renamed("Reading", "tools"))
        // Only the capitals change: that is the same folder, so it is allowed.
        assertEquals("READING", folders.renamed("Reading", "READING").folder("reading")?.name)
    }

    @Test
    fun `deleting a folder removes only that folder`() {
        val folders = AppFolders().withFolder("Reading").withFolder("Tools").without("reading")
        assertEquals(listOf("Tools"), folders.folders.map { it.name })
    }

    @Test
    fun `the file survives a round trip`() {
        val folders = AppFolders().withFolder("Reading").withFolder("Tools").toggled("Reading", kindle)
        assertEquals(folders, AppFoldersFile.parse(AppFoldersFile.serialise(folders)))
    }

    @Test
    fun `text that is not this file is refused, and one bad folder does not cost the others`() {
        assertNull(AppFoldersFile.parse("not json"))
        assertNull(AppFoldersFile.parse("""{"folders": []}"""))

        val parsed = AppFoldersFile.parse(
            """{"version": 1, "folders": [{"name": "Reading", "apps": ["$kindle", "$kindle", 7]}, {"apps": []}, "junk", {"name": "reading"}]}""",
        )
        assertEquals(listOf(AppFolder("Reading", listOf(kindle))), parsed?.folders)
    }

    @Test
    fun `the parse counts the folders it dropped`() {
        val parsed = AppFoldersFile.parseCounting(
            """{"version": 1, "folders": [{"name": "Reading", "apps": []}, {"apps": []}, {"name": "reading"}]}""",
        )
        assertEquals(2, parsed?.dropped)
        assertEquals(listOf(AppFolder("Reading")), parsed?.folders?.folders)
        assertEquals(1, AppFoldersFile.parseCounting("""{"version": 1, "folders": "Reading"}""")?.dropped)
        assertEquals(0, AppFoldersFile.parseCounting("""{"version": 1, "folders": []}""")?.dropped)
    }

    @Test
    fun `the repository writes a file a person can read, into the apps folder`() {
        val data = DataRepository(LocalFileStore(temporary.newFolder("Margin")))
        val repository = AppFoldersRepository(data)

        assertEquals(AppFolders(), repository.load())
        repository.change { it.withFolder("Reading").toggled("Reading", kindle) }

        val text = data.store.readText(StorageLayout.appFoldersPath())
        assertTrue(text.orEmpty().contains("\"Reading\""))
        assertEquals(listOf(kindle), AppFoldersRepository(data).load().folder("Reading")?.apps)
    }

    @Test
    fun `a folders file with a typo is kept beside the new one, not written over`() {
        val data = DataRepository(LocalFileStore(temporary.newFolder("Margin")))
        data.store.writeText(StorageLayout.appFoldersPath(), "{ this is not json")

        AppFoldersRepository(data).change { it.withFolder("Reading") }

        assertEquals("{ this is not json", data.store.readText("${StorageLayout.APPS}/folders.unreadable.json"))
        assertNotNull(AppFoldersRepository(data).load().folder("Reading"))
    }

    @Test
    fun `the app folders are part of a backup`() {
        assertTrue(StorageLayout.APPS in StorageLayout.backedUpFolders)
    }

    @Test
    fun `hiding an app is a toggle, and it stays in its folders`() {
        val folders = AppFolders().withFolder("Reading").toggled("Reading", kindle)
        val hidden = folders.hiddenToggled(kindle)
        assertTrue(hidden.isHidden(kindle))
        assertEquals(listOf("Reading"), hidden.foldersOf(kindle))
        assertEquals(folders, hidden.hiddenToggled(kindle))
    }

    @Test
    fun `the hidden apps survive the file, and a file from before hiding reads as none hidden`() {
        val folders = AppFolders().withFolder("Tools").hiddenToggled(kindle).hiddenToggled(libby)
        assertEquals(folders, AppFoldersFile.parse(AppFoldersFile.serialise(folders)))

        val old = """{"version": 1, "folders": [{"name": "Tools", "apps": []}]}"""
        assertEquals(AppFolders().withFolder("Tools"), AppFoldersFile.parse(old))

        val messy = """{"version": 1, "folders": [], "hidden": ["$kindle", "", "$kindle"]}"""
        assertEquals(listOf(kindle), AppFoldersFile.parse(messy)?.hidden)
    }
}
