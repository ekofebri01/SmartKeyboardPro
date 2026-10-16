package com.example.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.model.AutoTextEntry
import com.example.model.KeyboardSettings
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.util.zip.ZipInputStream

object SmartKeyboardProBackupImporter {

    fun parseZipBackup(context: Context, inputStream: InputStream): ParsedBackup {
        val zipIn = ZipInputStream(inputStream)
        var entry = zipIn.nextEntry
        val autoTexts = mutableListOf<AutoTextEntry>()
        var parsedSettings: KeyboardSettings? = null

        val tempDb = File(context.cacheDir, "temp_import_autotext.db")
        val tempXml = File(context.cacheDir, "temp_import_prefs.xml")

        try {
            while (entry != null) {
                val name = entry.name.lowercase()
                if (name.endsWith("autotext.db") || name.endsWith(".db")) {
                    FileOutputStream(tempDb).use { out ->
                        zipIn.copyTo(out)
                    }
                    val readList = readSqliteAutoTexts(tempDb)
                    autoTexts.addAll(readList)
                } else if (name.endsWith(".xml")) {
                    FileOutputStream(tempXml).use { out ->
                        zipIn.copyTo(out)
                    }
                    parsedSettings = parsePreferencesXml(tempXml)
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempDb.exists()) tempDb.delete()
            if (tempXml.exists()) tempXml.delete()
        }

        return ParsedBackup(autoTexts, parsedSettings)
    }

    fun readSqliteAutoTexts(dbFile: File): List<AutoTextEntry> {
        val list = mutableListOf<AutoTextEntry>()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery("SELECT key, value FROM autotext", null)
            val keyIndex = cursor.getColumnIndex("key")
            val valIndex = cursor.getColumnIndex("value")
            while (cursor.moveToNext()) {
                val key = cursor.getString(keyIndex)
                val value = cursor.getString(valIndex)
                if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                    list.add(
                        AutoTextEntry(
                            id = 0,
                            shortcut = key,
                            replacement = value,
                            category = resolveCategory(key)
                        )
                    )
                }
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db?.close()
        }
        return list
    }

    fun parsePreferencesXml(xmlFile: File): KeyboardSettings {
        var settings = KeyboardSettings()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(FileInputStream(xmlFile), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val nameAttr = parser.getAttributeValue(null, "name")
                    val valueAttr = parser.getAttributeValue(null, "value")

                    when (nameAttr) {
                        "sound_on" -> settings = settings.copy(soundOnPress = valueAttr == "true")
                        "vibrate" -> settings = settings.copy(vibrateOnPress = valueAttr == "true")
                        "vibrator_duration_ms" -> valueAttr?.toIntOrNull()?.let { settings = settings.copy(vibrationDurationMs = it) }
                        "volume" -> valueAttr?.toIntOrNull()?.let { settings = settings.copy(soundVolume = it / 100f) }
                        "show_numbers_top" -> settings = settings.copy(showNumberRow = valueAttr != "0")
                        "show_preview" -> settings = settings.copy(showKeyPreview = valueAttr == "true")
                        "auto_cap" -> settings = settings.copy(autoCapitalize = valueAttr == "true")
                        "space_when_pick" -> settings = settings.copy(autoSpaceAfterShortcut = valueAttr == "true")
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return settings
    }

    fun resolveCategory(shortcut: String): String {
        return when (shortcut.lowercase()) {
            "bca", "bri", "mandiri", "jago", "sea", "panin", "npwp", "bpjs", "pdam", "kk" -> "Perbankan & Data Diri"
            "ccgojek", "soplaka", "jamngalong", "cuacahujan", "perkenalan", "menutuppasarpolis", "settinghp" -> "Gojek & Komunitas"
            "eco", "lok", "gmaptoko", "rumahdavine", "hargatembakau", "produk", "barracktaste", "jagorasa", "grp", "grf" -> "Toko & Produk"
            "wa", "profilwa", "grupbangredy", "grupshopeetanam", "grupshopeetanam1" -> "Kontak & Grup"
            else -> "Pesan & Obrolan"
        }
    }
}

data class ParsedBackup(
    val autoTexts: List<AutoTextEntry>,
    val settings: KeyboardSettings?
)
