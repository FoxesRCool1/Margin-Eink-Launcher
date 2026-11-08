package io.github.foxesrcool1.margin.core.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.foxesrcool1.margin.core.json.Json
import io.github.foxesrcool1.margin.core.json.JsonArray
import io.github.foxesrcool1.margin.core.json.JsonString
import io.github.foxesrcool1.margin.core.json.jsonArrayOf
import io.github.foxesrcool1.margin.core.log.AppLog

/**
 * Turns the settings into plain text pairs for the backup, and back.
 *
 * Each value is written as "<type>:<value>". A set is a JSON array of strings.
 * Names that hold a secret or a path on this device are never exported, and
 * they are ignored on import too.
 */
object SettingsBackup {

    private const val TAG = "SettingsBackup"

    private val EXCLUDED_WORDS = listOf("token", "secret", "password", "uri", "path", "folder")

    fun isExcluded(name: String): Boolean {
        val lower = name.lowercase()
        return EXCLUDED_WORDS.any { lower.contains(it) }
    }

    fun encode(preferences: Preferences): Map<String, String> {
        val out = linkedMapOf<String, String>()
        preferences.asMap().forEach { (key, value) ->
            val name = key.name
            if (isExcluded(name)) return@forEach
            val encoded = when (value) {
                is Int -> "int:$value"
                is Long -> "long:$value"
                is Boolean -> "bool:$value"
                is Float -> "float:$value"
                is Double -> "double:$value"
                is String -> "str:$value"
                is Set<*> -> "set:" + Json.write(
                    jsonArrayOf(value.filterIsInstance<String>().map { JsonString(it) }),
                    pretty = false,
                )
                else -> null
            }
            if (encoded != null) out[name] = encoded
        }
        return out
    }

    /**
     * Writes the saved values into [target]. [known] maps each setting name the
     * app declares to its type tag ("int", "bool", "str", "set" and so on). A
     * value is written only when its name is there and its type tag matches.
     * A bad, unknown or wrongly typed value is logged and skipped. (A DataStore
     * key is equal to another key of the same name whatever its type, so the
     * type has to be checked here.)
     */
    fun apply(saved: Map<String, String>, target: MutablePreferences, known: Map<String, String>) {
        saved.forEach { (name, raw) ->
            if (isExcluded(name)) return@forEach
            val colon = raw.indexOf(':')
            val type = if (colon < 0) "" else raw.substring(0, colon)
            val value = if (colon < 0) "" else raw.substring(colon + 1)
            if (known[name] != type) {
                AppLog.w(TAG, "Setting $name is not a declared setting of type '$type', so it is skipped")
                return@forEach
            }
            runCatching { decode(name, type, value, target) }
                .onFailure { AppLog.w(TAG, "Setting $name is not valid, so it is skipped", it) }
        }
    }

    private fun decode(name: String, type: String, value: String, target: MutablePreferences) {
        when (type) {
            "int" -> target[intPreferencesKey(name)] = value.toInt()
            "long" -> target[longPreferencesKey(name)] = value.toLong()
            "bool" -> target[booleanPreferencesKey(name)] = value.toBooleanStrict()
            "float" -> target[floatPreferencesKey(name)] = value.toFloat()
            "double" -> target[doublePreferencesKey(name)] = value.toDouble()
            "str" -> target[stringPreferencesKey(name)] = value
            "set" -> {
                val json = Json.parse(value) as? JsonArray ?: error("The set is not an array")
                val items = json.strings()
                require(items.size == json.size) { "The set has a value that is not a string" }
                target[stringSetPreferencesKey(name)] = items.toSet()
            }
            else -> error("Unknown type '$type'")
        }
    }
}
