package io.github.foxesrcool1.margin.core.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryAppsTest {

    private fun app(packageName: String, label: String, colorDict: Boolean = false) =
        DictionaryApp(
            packageName = packageName,
            className = packageName + ".Main",
            label = label,
            colorDict = colorDict,
        )

    @Test
    fun processTextWinsOverColorDictForTheSamePackage() {
        val merged = mergeDictionaryApps(
            processText = listOf(app("com.process", "Process", colorDict = false)),
            colorDict = listOf(app("com.process", "Process", colorDict = true)),
            lastChosen = null,
            ownPackage = "io.github.foxesrcool1.margin",
        )
        assertEquals(1, merged.size)
        assertFalse(merged[0].colorDict)
    }

    @Test
    fun theLastChosenPackageSortsFirst() {
        val merged = mergeDictionaryApps(
            processText = listOf(app("com.aaa", "A"), app("com.zzz", "Z")),
            colorDict = listOf(app("com.mmm", "M", colorDict = true)),
            lastChosen = "com.mmm",
            ownPackage = "io.github.foxesrcool1.margin",
        )
        assertEquals(listOf("com.mmm", "com.aaa", "com.zzz"), merged.map { it.packageName })
    }

    @Test
    fun theOwnPackageIsDropped() {
        val merged = mergeDictionaryApps(
            processText = listOf(app("io.github.foxesrcool1.margin", "Margin")),
            colorDict = listOf(app("io.github.foxesrcool1.margin", "Margin", colorDict = true)),
            lastChosen = null,
            ownPackage = "io.github.foxesrcool1.margin",
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun bothListsAreKeptWhenThePackagesDiffer() {
        val merged = mergeDictionaryApps(
            processText = listOf(app("com.process", "Process")),
            colorDict = listOf(app("com.colordict", "ColorDict", colorDict = true)),
            lastChosen = null,
            ownPackage = "io.github.foxesrcool1.margin",
        )
        assertEquals(2, merged.size)
        assertTrue(merged.any { it.colorDict && it.packageName == "com.colordict" })
    }
}
