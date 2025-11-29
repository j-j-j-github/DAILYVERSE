package com.example.dailyverse

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.RemoteViews
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VerseRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("DailyVersePrefs", Context.MODE_PRIVATE)

    // --- Widget Content Toggle (NEW) ---
    fun saveShowVerseOnWidget(enabled: Boolean) {
        prefs.edit().putBoolean("pref_show_verse_widget", enabled).apply()
        // Refresh widget immediately
        val current = getDailyVerse()
        if (current != null) updateWidgets(current)
    }

    fun isShowVerseOnWidget(): Boolean = prefs.getBoolean("pref_show_verse_widget", false)

    // --- Widget Color Logic ---
    fun saveWidgetColor(color: Int) {
        prefs.edit().putInt("pref_widget_color", color).apply()
        val current = getDailyVerse()
        if (current != null) updateWidgets(current)
    }
    fun getWidgetColor(): Int = prefs.getInt("pref_widget_color", Color.WHITE)

    // --- Dark mode ---
    fun saveDarkMode(enabled: Boolean) = prefs.edit().putBoolean("pref_dark_mode", enabled).apply()
    fun isDarkMode(): Boolean = prefs.getBoolean("pref_dark_mode", false)

    // --- Genre preference ---
    fun saveGenrePreference(genre: String) {
        prefs.edit().putString("pref_genre", genre).apply()
        val newVerse = generateNewVerse()
        if (newVerse != null) saveVerse(newVerse)
    }
    fun getGenrePreference(): String = prefs.getString("pref_genre", "All") ?: "All"

    // --- Saved verse ID ---
    fun getSavedId(): Int = prefs.getInt("saved_id", -1)

    // --- Daily verse logic ---
    fun getDailyVerse(): Verse? {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastSavedDate = prefs.getString("saved_date", "")

        if (todayDate != lastSavedDate) {
            val newVerse = generateNewVerse()
            if (newVerse != null) saveVerse(newVerse)
            return newVerse
        } else {
            val id = getSavedId()
            return if (id == -1) {
                val newVerse = generateNewVerse()
                if (newVerse != null) saveVerse(newVerse)
                newVerse
            } else {
                getVerseById(id)
            }
        }
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
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun pickRandomVerse(genreFilter: String): Verse? {
        val jsonString = loadJSONFromAsset() ?: return null
        val candidates = ArrayList<Verse>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val vGenre = obj.getString("genre")
                if (genreFilter == "All" || vGenre.equals(genreFilter, ignoreCase = true)) {
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
        } catch (e: Exception) { e.printStackTrace() }
        return if (candidates.isNotEmpty()) candidates.random() else null
    }

    // --- Generate new verse and save ---
    fun generateNewVerse(): Verse? {
        return pickRandomVerse(getGenrePreference())
    }

    fun saveVerse(verse: Verse) {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit().apply {
            putString("saved_date", todayDate)
            putInt("saved_id", verse.id)
            putString("saved_text", verse.text)
            putString("saved_reference", verse.reference)
            putString("saved_genre", verse.genre)
            putString("saved_explanation", verse.explanation)
            apply()
        }
        updateWidgets(verse)
    }

    private fun updateWidgets(verse: Verse) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, DailyVerseWidget::class.java))

        // 1. Color Logic
        val bgColor = getWidgetColor()
        val luminance = (0.299 * Color.red(bgColor) + 0.587 * Color.green(bgColor) + 0.114 * Color.blue(bgColor)) / 255
        val isDarkBg = luminance < 0.5

        val textColor = if (isDarkBg) Color.WHITE else Color.parseColor("#333333")
        val titleColor = if (isDarkBg) Color.parseColor("#818CF8") else Color.parseColor("#5C6BC0")
        val promptColor = if (isDarkBg) Color.parseColor("#CCCCCC") else Color.parseColor("#888888")

        // 2. Content Logic (NEW)
        val showVerse = isShowVerseOnWidget()
        val contentText = if (showVerse) "\"${verse.text}\"" else verse.explanation
        val promptText = if (showVerse) "Tap to reveal meaning →" else "Tap to reveal verse →"

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.daily_verse_widget)

            // Set Content
            views.setTextViewText(R.id.widgetContent, contentText)
            views.setTextViewText(R.id.widgetPrompt, promptText)

            // Set Colors
            views.setInt(R.id.widgetRoot, "setBackgroundColor", bgColor)
            views.setTextColor(R.id.widgetContent, textColor)
            views.setTextColor(R.id.widgetTitle, titleColor)
            views.setTextColor(R.id.widgetPrompt, promptColor)

            // Set Click Intent
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

    private fun loadJSONFromAsset(): String? {
        return try {
            val inputStream = context.assets.open("verses.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (ex: IOException) { null }
    }
}