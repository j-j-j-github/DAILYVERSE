package com.example.dailyverse

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var repo: VerseRepository
    private var currentVerse: Verse? = null

    private val genres = arrayOf(
        "All", "Encouragement & Hope", "Wisdom & Guidance",
        "Love & Relationships", "Faith & Trust",
        "Strength & Courage", "Gratitude & Praise"
    )

    // --- 1. Permission Launcher for Android 13+ ---
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scheduleDailyNotification()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = VerseRepository(this)

        // Apply Theme
        if (repo.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        loadContent()

        // Check Permissions & Schedule Alarm
        checkAndScheduleNotifications()

        // Settings Button
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            showSettingsBottomSheet()
        }

        // Share Button
        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener {
            shareVerse()
        }
    }

    // --- 2. Check Permissions ---
    private fun checkAndScheduleNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) {
                scheduleDailyNotification()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            scheduleDailyNotification()
        }
    }

    // --- 3. FINAL SCHEDULING LOGIC (8:00 AM DAILY) ---
    private fun scheduleDailyNotification() {
        val workManager = WorkManager.getInstance(this)

        // Calculate time until 8:00 AM
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        dueDate.set(Calendar.HOUR_OF_DAY, 8) // 8 AM
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)

        // If 8 AM has already passed today, schedule for tomorrow
        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

        // Create the Periodic Request
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .addTag("daily_verse_notification")
            .build()

        // Enqueue Unique Work (Prevents duplicate alarms if app is opened multiple times)
        workManager.enqueueUniquePeriodicWork(
            "daily_verse_notification_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWorkRequest
        )
    }

    // --- 4. UI & Logic ---

    private fun loadContent() {
        currentVerse = repo.getDailyVerse()
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

        // Dark Mode
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

        // Genre Selector
        val btnGenre = view.findViewById<LinearLayout>(R.id.btnSelectGenre)
        val tvCurrent = view.findViewById<TextView>(R.id.tvCurrentGenreSettings)
        tvCurrent.text = repo.getGenrePreference()

        btnGenre.setOnClickListener {
            dialog.dismiss()
            window.decorView.postDelayed({ showNiceGenreSelector() }, 150)
        }

        // Developer Section
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

        // Links
        view.findViewById<TextView>(R.id.btnBugReport).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:jeevaljollyjacob@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Daily Verse App - Bug Report")
            }
            try { startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
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