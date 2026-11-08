package io.github.foxesrcool1.margin.core.apps

import io.github.foxesrcool1.margin.core.storage.DataRepository
import io.github.foxesrcool1.margin.core.storage.FileStore
import io.github.foxesrcool1.margin.core.storage.LocalFileStore
import io.github.foxesrcool1.margin.core.storage.StorageLayout
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppFoldersRepositoryTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private val kindle = "com.amazon.kindle/com.amazon.kindle.Main"

    @Test
    fun `a folder with no name is dropped, the good folder loads, and a copy of the file is kept`() {
        val real = LocalFileStore(temporary.newFolder("Margin"))
        val text = """{"version": 1, "folders": [{"name": "Reading", "apps": ["$kindle"]}, {"apps": []}]}"""
        real.writeText(StorageLayout.appFoldersPath(), text)

        val loaded = AppFoldersRepository(DataRepository(real)).load()

        assertEquals(listOf(AppFolder("Reading", listOf(kindle))), loaded.folders)
        assertEquals(text, real.readText("${StorageLayout.APPS}/folders.unreadable.json"))
    }

    @Test
    fun `a folders file that cannot be read is not written over until it reads again`() {
        val real = LocalFileStore(temporary.newFolder("Margin"))
        val text = AppFoldersFile.serialise(AppFolders().withFolder("Reading"))
        real.writeText(StorageLayout.appFoldersPath(), text)
        val flaky = FlakyStore(real).apply { failing = true }
        val repository = AppFoldersRepository(DataRepository(flaky))

        assertEquals(AppFolders(), repository.load())
        assertEquals(AppFolders(), repository.change { it.withFolder("Tools") })
        assertEquals(text, real.readText(StorageLayout.appFoldersPath()))

        flaky.failing = false
        assertNotNull(repository.load().folder("Reading"))
        repository.change { it.withFolder("Tools") }
        assertNotNull(AppFoldersRepository(DataRepository(real)).load().folder("Tools"))
    }

    /** A store whose read of the folders file throws while [failing] is true, like a card that fails. */
    private class FlakyStore(private val real: FileStore) : FileStore by real {
        var failing = false

        override fun readTextOrThrow(relativePath: String): String? =
            if (failing) throw IOException("broken") else real.readTextOrThrow(relativePath)
    }
}
