package io.github.foxesrcool1.margin.ui.reading.epub

import android.content.Context
import io.github.foxesrcool1.margin.core.json.Json
import io.github.foxesrcool1.margin.core.json.JsonObject
import io.github.foxesrcool1.margin.core.log.AppLog
import io.github.foxesrcool1.margin.core.settings.ReaderSettings
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

private const val TAG = "EpubSupport"

/** The name the Literata face gets inside the book's web view. */
val LITERATA_FAMILY = FontFamily("Literata")

/** Where the font file is served from. `app/build.gradle.kts` adds `res/font` to the assets. */
const val LITERATA_ASSET = "literata.ttf"

/** Opens an EPUB file with the Readium toolkit. */
object EpubOpener {

    /** One client for the life of the process: the retriever and the parser share it. */
    private val httpClient by lazy { DefaultHttpClient() }

    suspend fun open(context: Context, file: File): Publication? {
        val retriever = AssetRetriever(context.contentResolver, httpClient)
        val asset = retriever.retrieve(file).getOrElse {
            AppLog.e(TAG, "Could not read ${file.name}: $it")
            return null
        }
        val parser = DefaultPublicationParser(context, httpClient, retriever, null)
        return PublicationOpener(parser).open(asset, allowUserInteraction = false).getOrElse {
            AppLog.e(TAG, "Could not open ${file.name}: $it")
            asset.close()
            null
        }
    }
}

/**
 * The reader settings in the words of Readium.
 *
 * The e-ink rules decide the rest: one column, pages and not a scroll, pure
 * black on pure white whatever the book asks for.
 */
fun ReaderSettings.toEpubPreferences(): EpubPreferences {
    val ownStyles = font != ReaderSettings.FONT_PUBLISHER
    return EpubPreferences(
        backgroundColor = Color(android.graphics.Color.WHITE),
        textColor = Color(android.graphics.Color.BLACK),
        theme = Theme.LIGHT,
        scroll = false,
        columnCount = ColumnCount.ONE,
        fontFamily = when (font) {
            ReaderSettings.FONT_SERIF -> FontFamily.SERIF
            ReaderSettings.FONT_SANS -> FontFamily.SANS_SERIF
            ReaderSettings.FONT_PUBLISHER -> null
            else -> LITERATA_FAMILY
        },
        fontSize = fontSizePercent / 100.0,
        pageMargins = marginPercent / 100.0,
        // Line height and alignment only count when the book's own styles are off.
        publisherStyles = !ownStyles,
        lineHeight = if (ownStyles) lineHeightPercent / 100.0 else null,
        textAlign = if (ownStyles) (if (justify) TextAlign.JUSTIFY else TextAlign.START) else null,
        hyphens = if (ownStyles) justify else null,
    )
}

/** A Readium locator as the app's own JSON, so the storage layer needs no Readium. */
fun Locator.toJsonObject(): JsonObject? =
    Json.parseOrNull(toJSON().toString()) as? JsonObject

fun JsonObject.toLocator(): Locator? = runCatching {
    Locator.fromJSON(JSONObject(Json.write(this, pretty = false)))
}.getOrNull()
