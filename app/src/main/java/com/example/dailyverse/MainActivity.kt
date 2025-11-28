package com.example.dailyverse

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var repo: VerseRepository
    private var currentVerse: Verse? = null

    private val genres = arrayOf(
        "All", "Encouragement & Hope", "Wisdom & Guidance",
        "Love & Relationships", "Faith & Trust",
        "Strength & Courage", "Gratitude & Praise"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = VerseRepository(this)
        loadContent()

        // Settings Button -> Opens Simple Selection Dialog
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            showGenreSelector()
        }

        // Share Button -> Opens System Share Sheet
        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener {
            shareVerse()
        }
    }

    private fun loadContent() {
        currentVerse = repo.getDailyVerse()
        if (currentVerse != null) {
            findViewById<TextView>(R.id.tvVerseText).text = "\"${currentVerse!!.text}\""
            findViewById<TextView>(R.id.tvReference).text = "- ${currentVerse!!.reference}"
            findViewById<TextView>(R.id.tvGenreBadge).text = currentVerse!!.genre.uppercase()
            findViewById<TextView>(R.id.tvMeaning).text = currentVerse!!.explanation
        }
    }

    private fun showGenreSelector() {
        val currentGenre = repo.getGenrePreference()
        var checkedItem = genres.indexOfFirst { it.equals(currentGenre, ignoreCase = true) }
        if (checkedItem < 0) checkedItem = 0

        AlertDialog.Builder(this)
            .setTitle("Select Category")
            .setSingleChoiceItems(genres, checkedItem) { dialog, which ->
                val selected = genres[which]
                repo.saveGenrePreference(selected)
                loadContent()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareVerse() {
        if (currentVerse == null) return
        val shareText = "\"${currentVerse!!.text}\"\n\n- ${currentVerse!!.reference}\n\nShared via Daily Verse App"
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, null))
    }
}