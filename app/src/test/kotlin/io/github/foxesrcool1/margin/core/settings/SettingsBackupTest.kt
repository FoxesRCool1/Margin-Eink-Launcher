package io.github.foxesrcool1.margin.core.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.foxesrcool1.margin.core.log.AppLog
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackupTest {

    private val knownKeys: Map<String, String> = mapOf(
        "reader_font_size" to "int",
        "home_labels" to "bool",
        "botanical_art" to "bool",
        "pen_width" to "float",
        "reader_font" to "str",
        "pinned_apps" to "str",
        "tags" to "set",
    )

    @Test
    fun roundTripsIntBooleanStringAndSet() {
        val source = mutablePreferencesOf(
            intPreferencesKey("reader_font_size") to 120,
            booleanPreferencesKey("home_labels") to true,
            stringPreferencesKey("reader_font") to "Literata",
            stringSetPreferencesKey("tags") to setOf("a:b", "c"),
        )

        val encoded = SettingsBackup.encode(source)
        val target = mutablePreferencesOf()
        SettingsBackup.apply(encoded, target, knownKeys)

        assertEquals(source.asMap(), target.asMap())
    }

    @Test
    fun wrongTypedValueForDeclaredNameIsNotWritten() {
        AppLog.mirrorToLogcat = false
        val target = mutablePreferencesOf()

        SettingsBackup.apply(mapOf("reader_font_size" to "str:big"), target, knownKeys)

        assertEquals(emptyMap<Preferences.Key<*>, Any>(), target.asMap())
    }

    @Test
    fun unknownNameIsNotWrittenAndDeclaredTypedValueIs() {
        AppLog.mirrorToLogcat = false
        val target = mutablePreferencesOf()

        SettingsBackup.apply(
            mapOf("not_a_setting" to "bool:true", "reader_font_size" to "int:120"),
            target,
            knownKeys,
        )

        assertEquals(mapOf<Preferences.Key<*>, Any>(Pair(intPreferencesKey("reader_font_size"), 120)), target.asMap())
    }

    @Test
    fun secretAndDevicePathNamesAreNotExported() {
        val source = mutablePreferencesOf(
            stringPreferencesKey("sync_token") to "abc",
            stringPreferencesKey("library_folder") to "/sdcard/books",
            intPreferencesKey("reader_font_size") to 100,
        )

        val encoded = SettingsBackup.encode(source)

        assertEquals(setOf("reader_font_size"), encoded.keys)
    }

    @Test
    fun garbageValuesAreIgnoredWithoutThrowing() {
        AppLog.mirrorToLogcat = false
        val target = mutablePreferencesOf()

        SettingsBackup.apply(
            mapOf(
                "reader_font_size" to "int:abc",
                "home_labels" to "bool:maybe",
                "pen_width" to "weird:1",
                "pinned_apps" to "set:not json",
                "botanical_art" to "bool:false",
            ),
            target,
            knownKeys,
        )

        assertEquals(mapOf<Preferences.Key<*>, Any>(Pair(booleanPreferencesKey("botanical_art"), false)), target.asMap())
    }
}
