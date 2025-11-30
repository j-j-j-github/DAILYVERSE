package com.example.dailyverse

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.RemoteViews
import android.util.Log
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VerseRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("DailyVersePrefs", Context.MODE_PRIVATE)

    // -----------------------------------------------------------
    // PUBLIC METHODS USED BY APP + WIDGET
    // -----------------------------------------------------------

    fun loadSavedVerse(): Verse? {
        val id = prefs.getInt("saved_id", -1)
        if (id == -1) return null
        return getVerseById(id)
    }

    fun getDailyVerse(): Verse? {
        Log.d("DAILY_TEST", "getDailyVerse() CALLED")

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val last = prefs.getString("saved_date", null)

        Log.d("DAILY_TEST", "Today = $today | LastSaved = $last")

        // If new date → force new verse
        if (today != last) {
            val newVerse = generateNewVerse()
            if (newVerse != null) {
                saveVerse(newVerse)
                Log.d("DAILY_TEST", "Generated NEW verse for date: $today")
            } else {
                Log.d("DAILY_TEST", "ERROR: Could not generate new verse")
            }
            return newVerse
        }

        // Return existing saved verse
        return loadSavedVerse() ?: run {
            // If corrupted / missing → regenerate
            val newVerse = generateNewVerse()
            if (newVerse != null) saveVerse(newVerse)
            newVerse
        }
    }

    fun generateNewVerse(): Verse? {
        return pickRandomVerse(getGenrePreference())
    }

    fun saveVerse(verse: Verse) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit().apply {
            putString("saved_date", today)
            putInt("saved_id", verse.id)
            putString("saved_text", verse.text)
            putString("saved_reference", verse.reference)
            putString("saved_genre", verse.genre)
            putString("saved_explanation", verse.explanation)
            apply()
        }

        updateWidgets(verse)
    }

    // -----------------------------------------------------------
    // WIDGET CONFIGURATION METHODS (UNCHANGED)
    // -----------------------------------------------------------

    fun saveShowVerseOnWidget(enabled: Boolean) {
        prefs.edit().putBoolean("pref_show_verse_widget", enabled).apply()
        loadSavedVerse()?.let { updateWidgets(it) }
    }

    fun isShowVerseOnWidget(): Boolean =
        prefs.getBoolean("pref_show_verse_widget", false)

    fun saveWidgetColor(color: Int) {
        prefs.edit().putInt("pref_widget_color", color).apply()
        loadSavedVerse()?.let { updateWidgets(it) }
    }

    fun getWidgetColor(): Int = prefs.getInt("pref_widget_color", Color.WHITE)

    fun saveGenrePreference(genre: String) {
        prefs.edit().putString("pref_genre", genre).apply()
        val newVerse = generateNewVerse()
        if (newVerse != null) saveVerse(newVerse)
    }

    fun getGenrePreference(): String =
        prefs.getString("pref_genre", "All") ?: "All"

    fun isDarkMode(): Boolean =
        prefs.getBoolean("pref_dark_mode", false)

    fun saveDarkMode(enabled: Boolean) =
        prefs.edit().putBoolean("pref_dark_mode", enabled).apply()

    // -----------------------------------------------------------
    // DATA LOADING & RANDOM PICK
    // -----------------------------------------------------------

    private fun pickRandomVerse(genreFilter: String): Verse? {
        val jsonString = loadJSONFromAsset() ?: return null
        val candidates = ArrayList<Verse>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val vGenre = obj.getString("genre")

                if (genreFilter == "All" ||
                    vGenre.equals(genreFilter, ignoreCase = true)
                ) {
                    candidates.add(
                        Verse(
                            obj.getInt("id"),
                            obj.getString("text"),
                            obj.getString("reference"),
                            vGenre,
                            obj.getString("explanation")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (candidates.isNotEmpty()) candidates.random() else null
    }

    fun getVerseById(id: Int): Verse? {
        val jsonString = loadJSONFromAsset() ?: return null

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getInt("id") == id) {
                    return Verse(
                        id = id,
                        text = obj.getString("text"),
                        reference = obj.getString("reference"),
                        genre = obj.getString("genre"),
                        explanation = obj.getString("explanation")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun loadJSONFromAsset(): String? {
        return try {
            val inputStream = context.assets.open("verses.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (ex: IOException) {
            null
        }
    }

    // -----------------------------------------------------------
    // WIDGET UPDATE
    // -----------------------------------------------------------

    fun updateWidgets(verse: Verse) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(context, DailyVerseWidget::class.java)
        )

        val bgColor = getWidgetColor()
        val luminance =
            (0.299 * Color.red(bgColor) +
                    0.587 * Color.green(bgColor) +
                    0.114 * Color.blue(bgColor)) / 255

        val isDark = luminance < 0.5

        val textColor = if (isDark) Color.WHITE else Color.parseColor("#333333")
        val titleColor = if (isDark) Color.parseColor("#818CF8") else Color.parseColor("#5C6BC0")
        val promptColor = if (isDark) Color.parseColor("#CCCCCC") else Color.parseColor("#888888")

        val showVerse = isShowVerseOnWidget()
        val contentText = if (showVerse) "\"${verse.text}\"" else verse.explanation
        val promptText = if (showVerse) "Tap to reveal meaning →" else "Tap to reveal verse →"

        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.daily_verse_widget)

            views.setTextViewText(R.id.widgetContent, contentText)
            views.setTextViewText(R.id.widgetPrompt, promptText)

            views.setInt(R.id.widgetRoot, "setBackgroundColor", bgColor)
            views.setTextColor(R.id.widgetContent, textColor)
            views.setTextColor(R.id.widgetTitle, titleColor)
            views.setTextColor(R.id.widgetPrompt, promptColor)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widgetContent, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetTitle, pendingIntent)

            appWidgetManager.updateAppWidget(id, views)
        }
    }
}