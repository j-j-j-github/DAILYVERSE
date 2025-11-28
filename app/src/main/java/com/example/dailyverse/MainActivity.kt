package com.example.dailyverse

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

        // Apply saved dark mode setting
        if (repo.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        loadContent()

        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            showSettingsBottomSheet()
        }

        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener {
            shareVerse()
        }
    }

    // ---------------------------------------------
    // Load SAME verse as widget
    // ---------------------------------------------
    private fun loadContent() {
        // Get the last saved ID, if any
        val savedId = repo.getSavedId()

        currentVerse = if (savedId != -1) {
            repo.getVerseById(savedId)
        } else {
            repo.getDailyVerse()
        }

        currentVerse?.let { verse ->
            findViewById<TextView>(R.id.tvVerseText).text = "\"${verse.text}\""
            findViewById<TextView>(R.id.tvReference).text = "- ${verse.reference}"
            findViewById<TextView>(R.id.tvGenreBadge).text = verse.genre.uppercase()
            findViewById<TextView>(R.id.tvMeaning).text = verse.explanation
        }
    }

    private fun showSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        dialog.setContentView(view)

        // --- Dark Mode Toggle ---
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

        // --- Genre Selector ---
        val btnGenre = view.findViewById<LinearLayout>(R.id.btnSelectGenre)
        val tvCurrent = view.findViewById<TextView>(R.id.tvCurrentGenreSettings)
        tvCurrent.text = repo.getGenrePreference()

        btnGenre.setOnClickListener {
            dialog.dismiss()
            window.decorView.postDelayed({ showNiceGenreSelector() }, 150)
        }

        // --- About Developer Toggle ---
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

        // --- Developer Buttons ---
        view.findViewById<TextView>(R.id.btnBugReport).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:jeevaljollyjacob@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Daily Verse App - Bug Report")
            }
            startActivity(intent)
        }

        view.findViewById<TextView>(R.id.btnBuyCoffee).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/jeevaljollyjacob")))
        }

        view.findViewById<TextView>(R.id.btnWebsite).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://j-j-j-github.github.io/MY-PORTFOLIO/")))
        }

        dialog.show()
    }

    private fun showNiceGenreSelector() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_genre_selection, null)
        dialog.setContentView(view)

        val container = view.findViewById<LinearLayout>(R.id.genreListContainer)
        val btnClose = view.findViewById<TextView>(R.id.btnCloseDialog)

        val currentGenre = repo.getGenrePreference()

        for (genre in genres) {
            val isSelected = genre.equals(currentGenre, ignoreCase = true)

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(48, 32, 48, 32)
                background = if (isSelected)
                    ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_badge)
                else
                    ContextCompat.getDrawable(this@MainActivity, android.R.color.transparent)
            }

            val tv = TextView(this).apply {
                text = genre
                textSize = 16f
                setTextColor(
                    if (isSelected)
                        ContextCompat.getColor(this@MainActivity, R.color.brand_primary)
                    else
                        ContextCompat.getColor(this@MainActivity, R.color.text_primary)
                )
                typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD
                else android.graphics.Typeface.DEFAULT
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            itemLayout.addView(tv)

            if (isSelected) {
                val check = ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.brand_primary))
                }
                itemLayout.addView(check)
            }

            itemLayout.setOnClickListener {
                repo.saveGenrePreference(genre)
                loadContent()
                dialog.dismiss()
            }

            container.addView(itemLayout)
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun shareVerse() {
        currentVerse?.let {
            val shareText = "\"${it.text}\"\n\n- ${it.reference}\n\nShared via Daily Verse App"
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(sendIntent, null))
        }
    }
}