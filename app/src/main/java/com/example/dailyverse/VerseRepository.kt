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

    // --- DARK MODE LOGIC ---
    fun saveDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("pref_dark_mode", enabled).apply()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean("pref_dark_mode", false)
    }

    // --- GENRE LOGIC ---
    fun saveGenrePreference(genre: String) {
        prefs.edit().putString("pref_genre", genre).apply()
        // Force a refresh immediately when genre changes
        val newVerse = generateNewVerse()
        if (newVerse != null) {
            saveVerse(newVerse)
        }
    }

    fun getGenrePreference(): String {
        return prefs.getString("pref_genre", "All") ?: "All"
    }

    // --- DAILY VERSE LOGIC ---

    // 1. Get Verse (Check Date)
    fun getDailyVerse(): Verse? {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastSavedDate = prefs.getString("saved_date", "")

        if (todayDate != lastSavedDate) {
            // New Day: Generate and Save
            val newVerse = generateNewVerse()
            if (newVerse != null) {
                saveVerse(newVerse)
            }
            return newVerse
        } else {
            // Same Day: Load Saved
            val id = prefs.getInt("saved_id", -1)

            // If save is missing/corrupt, regenerate
            if (id == -1) {
                val newVerse = generateNewVerse()
                if (newVerse != null) saveVerse(newVerse)
                return newVerse
            }

            // Otherwise return saved verse
            // We reconstruct it from prefs to avoid re-reading JSON if possible,
            // or fetch by ID if you prefer strict consistency.
            // Here we simply reconstruct for speed:
            return Verse(
                id = id,
                text = prefs.getString("saved_text", "") ?: "",
                reference = prefs.getString("saved_reference", "") ?: "",
                genre = prefs.getString("saved_genre", "") ?: "",
                explanation = prefs.getString("saved_explanation", "") ?: ""
            )
        }
    }

    // 2. PUBLIC: Generate New Verse (Used by Next Arrow)
    fun generateNewVerse(): Verse? {
        return pickRandomVerse(getGenrePreference())
    }

    // 3. PUBLIC: Save Specific Verse (Used by Prev/Next Arrows)
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

        // Force Widget Update
        updateWidgets(verse)
    }

    // --- INTERNAL HELPERS ---

    private fun pickRandomVerse(genreFilter: String): Verse? {
        val jsonString = loadJSONFromAsset() ?: return null
        val candidates = ArrayList<Verse>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val vGenre = obj.getString("genre")
                if (genreFilter == "All" || vGenre.equals(genreFilter, ignoreCase = true)) {
                    candidates.add(Verse(obj.getInt("id"), obj.getString("text"), obj.getString("reference"), vGenre, obj.getString("explanation")))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return if (candidates.isNotEmpty()) candidates.random() else null
    }

    private fun updateWidgets(verse: Verse) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, DailyVerseWidget::class.java))

            if (ids.isEmpty()) return

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
        } catch (e: Exception) {
            e.printStackTrace()
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