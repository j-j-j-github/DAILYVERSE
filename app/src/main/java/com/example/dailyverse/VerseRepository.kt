package com.example.dailyverse

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VerseRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("DailyVersePrefs", Context.MODE_PRIVATE)

    // --- Dark mode ---
    fun saveDarkMode(enabled: Boolean) = prefs.edit().putBoolean("pref_dark_mode", enabled).apply()
    fun isDarkMode(): Boolean = prefs.getBoolean("pref_dark_mode", false)

    // --- Genre preference ---
    fun saveGenrePreference(genre: String) {
        prefs.edit().putString("pref_genre", genre).apply()
        forceNewVerse()
    }
    fun getGenrePreference(): String = prefs.getString("pref_genre", "All") ?: "All"

    // --- Saved verse ID ---
    fun getSavedId(): Int = prefs.getInt("saved_id", -1)

    // --- Daily verse logic ---
    fun getDailyVerse(): Verse? {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastSavedDate = prefs.getString("saved_date", "")

        return if (todayDate != lastSavedDate) {
            forceNewVerse()
        } else {
            val id = getSavedId()
            if (id == -1) pickRandomVerse("All")
            else getVerseById(id)
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
    fun forceNewVerse(): Verse? {
        val newVerse = pickRandomVerse(getGenrePreference())
        newVerse?.let {
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            saveVerse(it, todayDate)
        }
        return newVerse
    }

    private fun saveVerse(verse: Verse, date: String) {
        prefs.edit().apply {
            putString("saved_date", date)
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
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.daily_verse_widget)
            views.setTextViewText(R.id.widgetContent, verse.explanation)
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