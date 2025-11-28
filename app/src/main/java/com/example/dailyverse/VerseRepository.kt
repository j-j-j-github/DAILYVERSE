package com.example.dailyverse

import android.content.Context
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class VerseRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("DailyVersePrefs", Context.MODE_PRIVATE)

    // --- DARK MODE LOGIC (Required to fix build errors) ---
    fun saveDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("pref_dark_mode", enabled).apply()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean("pref_dark_mode", false)
    }

    // --- GENRE LOGIC ---
    fun saveGenrePreference(genre: String) {
        prefs.edit().putString("pref_genre", genre).apply()
        forceNewVerse()
    }

    fun getGenrePreference(): String {
        return prefs.getString("pref_genre", "All") ?: "All"
    }

    // --- DAILY VERSE LOGIC ---
    private fun forceNewVerse() {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val newVerse = pickRandomVerse(getGenrePreference())
        if (newVerse != null) {
            saveVerse(newVerse, todayDate)
        }
    }

    fun getDailyVerse(): Verse? {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastSavedDate = prefs.getString("saved_date", "")

        if (todayDate != lastSavedDate) {
            val newVerse = pickRandomVerse(getGenrePreference())
            if (newVerse != null) {
                saveVerse(newVerse, todayDate)
            }
            return newVerse
        } else {
            val id = prefs.getInt("saved_id", -1)
            if (id == -1) return pickRandomVerse("All")

            return Verse(
                id = id,
                text = prefs.getString("saved_text", "") ?: "",
                reference = prefs.getString("saved_reference", "") ?: "",
                genre = prefs.getString("saved_genre", "") ?: "",
                explanation = prefs.getString("saved_explanation", "") ?: ""
            )
        }
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
                    candidates.add(Verse(obj.getInt("id"), obj.getString("text"), obj.getString("reference"), vGenre, obj.getString("explanation")))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return if (candidates.isNotEmpty()) candidates.random() else null
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