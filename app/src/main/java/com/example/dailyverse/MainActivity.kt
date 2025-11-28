package com.example.dailyverse

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial

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

        // Force Light Mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        loadContent()

        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            showSettingsBottomSheet()
        }

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

    private fun showSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        dialog.setContentView(view)

        // 1. Dark Mode Toggle
        val switchDark = view.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        switchDark.isChecked = repo.isDarkMode()
        switchDark.setOnCheckedChangeListener { _, isChecked ->
            repo.saveDarkMode(isChecked)
            view.postDelayed({
                if (isChecked) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                dialog.dismiss()
            }, 300)
        }

        // 2. Genre Selector
        val btnGenre = view.findViewById<LinearLayout>(R.id.btnSelectGenre)
        val tvCurrent = view.findViewById<TextView>(R.id.tvCurrentGenreSettings)
        tvCurrent.text = repo.getGenrePreference()

        btnGenre.setOnClickListener {
            dialog.dismiss()
            // Slight delay for smooth transition (Sheet down -> Sheet up)
            window.decorView.postDelayed({ showNiceGenreSelector() }, 150)
        }

        // 3. About Developer
        val btnAbout = view.findViewById<LinearLayout>(R.id.btnAboutDev)
        val layoutDetails = view.findViewById<LinearLayout>(R.id.layoutDevDetails)
        val iconExpand = view.findViewById<TextView>(R.id.iconExpandDev)

        btnAbout.setOnClickListener {
            if (layoutDetails.visibility == View.VISIBLE) {
                layoutDetails.visibility = View.GONE
                iconExpand.text = "▼"
            } else {
                layoutDetails.alpha = 0f
                layoutDetails.visibility = View.VISIBLE
                layoutDetails.animate().alpha(1f).setDuration(300).start()
                iconExpand.text = "▲"
            }
        }

        dialog.show()
    }

    private fun showNiceGenreSelector() {
        // CHANGED: Use BottomSheetDialog for smooth transition
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_genre_selection, null)
        dialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.genreListContainer)
        val btnClose = view.findViewById<TextView>(R.id.btnCloseDialog)

        val currentGenre = repo.getGenrePreference()

        for (genre in genres) {
            val isSelected = genre.equals(currentGenre, ignoreCase = true)

            // Row Container
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(48, 32, 48, 32)

                // Highlight background if selected
                if (isSelected) {
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_badge)
                    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, R.color.brand_light))
                } else {
                    background = ContextCompat.getDrawable(this@MainActivity, android.R.color.transparent)
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 8, 0, 8)
                layoutParams = params
            }

            // Genre Text
            val tv = TextView(this).apply {
                text = genre
                textSize = 16f
                if (isSelected) {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.brand_primary))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                    typeface = android.graphics.Typeface.DEFAULT
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            itemLayout.addView(tv)

            // CLEANER CHECKMARK
            if (isSelected) {
                val check = ImageView(this).apply {
                    setImageResource(R.drawable.ic_check) // Using new clean icon
                    setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.brand_primary))
                }
                itemLayout.addView(check)
            }

            // Click Logic
            itemLayout.setOnClickListener {
                repo.saveGenrePreference(genre)
                loadContent() // Updates home screen pill instantly
                dialog.dismiss()
            }

            container.addView(itemLayout)
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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