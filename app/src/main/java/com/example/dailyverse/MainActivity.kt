package com.example.dailyverse

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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

    // --- HISTORY LOGIC ---
    private val verseHistory = mutableListOf<Verse>()
    private var historyIndex = -1

    private val genres = arrayOf(
        "All", "Encouragement & Hope", "Wisdom & Guidance",
        "Love & Relationships", "Faith & Trust",
        "Strength & Courage", "Gratitude & Praise"
    )

    // --- Permission Launcher ---
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scheduleDailyNotification()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Window Setup for Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        repo = VerseRepository(this)

        // 2. Apply Theme
        if (repo.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // Force Nav Bar Color
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_bottom_bar)

        // 3. Apply Insets (Padding)
        applyBottomBarInsets()
        applyHeaderInsets()

        // 4. Initial Logic
        loadInitialVerse()
        checkAndScheduleNotifications()

        // 5. Splash Fade Logic
        val splashContainer = findViewById<View>(R.id.splashContainer)
        splashContainer.postDelayed({
            splashContainer.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction {
                    splashContainer.visibility = View.GONE
                }
                .start()
        }, 2000)

        // --- LISTENERS ---

        findViewById<ImageView>(R.id.btnSettings).setOnClickListener { showSettingsBottomSheet() }
        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener { shareVerse() }

        // LEFT ARROW
        findViewById<ImageView>(R.id.btnPrev).setOnClickListener {
            if (historyIndex > 0) {
                historyIndex--
                val prevVerse = verseHistory[historyIndex]
                displayVerse(prevVerse)
                repo.saveVerse(prevVerse)
            } else {
                Toast.makeText(this, "No previous verses", Toast.LENGTH_SHORT).show()
            }
        }

        // RIGHT ARROW
        findViewById<ImageView>(R.id.btnNext).setOnClickListener {
            if (historyIndex < verseHistory.size - 1) {
                historyIndex++
                val nextHistory = verseHistory[historyIndex]
                displayVerse(nextHistory)
                repo.saveVerse(nextHistory)
            } else {
                val newVerse = repo.generateNewVerse()
                if (newVerse != null) {
                    addToHistory(newVerse)
                    displayVerse(newVerse)
                    repo.saveVerse(newVerse)
                }
            }
        }
    }

    // --- INSET HELPERS ---

    private fun applyBottomBarInsets() {
        val bottomBar = findViewById<View>(R.id.bottomBar)
        val originalPaddingBottom = bottomBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, originalPaddingBottom + b)
            ins
        }
    }

    private fun applyHeaderInsets() {
        val header = findViewById<View>(R.id.header)
        val originalPaddingTop = header.paddingTop

        ViewCompat.setOnApplyWindowInsetsListener(header) { v, ins ->
            val statusBarHeight = ins.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, originalPaddingTop + statusBarHeight, v.paddingRight, v.paddingBottom)
            ins
        }
    }

    // --- VERSE LOGIC ---

    private fun loadInitialVerse() {
        val daily = repo.getDailyVerse()
        if (daily != null) {
            addToHistory(daily)
            displayVerse(daily)
        }
    }

    private fun addToHistory(verse: Verse) {
        while (verseHistory.size > historyIndex + 1) {
            verseHistory.removeAt(verseHistory.size - 1)
        }
        verseHistory.add(verse)
        historyIndex = verseHistory.size - 1
    }

    private fun displayVerse(verse: Verse) {
        currentVerse = verse
        findViewById<TextView>(R.id.tvVerseText).text = "\"${verse.text}\""
        findViewById<TextView>(R.id.tvReference).text = "- ${verse.reference}"
        findViewById<TextView>(R.id.tvGenreBadge).text = verse.genre.uppercase()
        findViewById<TextView>(R.id.tvMeaning).text = verse.explanation
    }

    // --- NOTIFICATIONS ---

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

    private fun scheduleDailyNotification() {
        val workManager = WorkManager.getInstance(this)
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        // Schedule for 8:00 AM
        dueDate.set(Calendar.HOUR_OF_DAY, 8)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .addTag("daily_verse_notification")
            .build()

        workManager.enqueueUniquePeriodicWork(
            "daily_verse_notification_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyWorkRequest
        )
    }

    // ────────────────────────────────────────────────
    // ⭐ SETTINGS BOTTOM SHEET
    // ────────────────────────────────────────────────

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
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
                dialog.dismiss()
            }, 300)
        }

        // --- WIDGET CONTENT TOGGLE (NEW) ---
        val switchWidgetContent = view.findViewById<SwitchMaterial>(R.id.switchWidgetContent)
        // Set initial state from repo
        switchWidgetContent.isChecked = repo.isShowVerseOnWidget()

        // Save change immediately when toggled
        switchWidgetContent.setOnCheckedChangeListener { _, isChecked ->
            repo.saveShowVerseOnWidget(isChecked)
        }

        // --- Genre Selector ---
        val btnGenre = view.findViewById<LinearLayout>(R.id.btnSelectGenre)
        val tvCurrent = view.findViewById<TextView>(R.id.tvCurrentGenreSettings)
        tvCurrent.text = repo.getGenrePreference()

        btnGenre.setOnClickListener {
            dialog.dismiss()
            window.decorView.postDelayed({ showNiceGenreSelector() }, 150)
        }

        // --- COLOR SELECTOR LOGIC ---

        val containerWhite = view.findViewById<FrameLayout>(R.id.colorWhite)
        val containerBlack = view.findViewById<FrameLayout>(R.id.colorBlack)
        val containerBlue = view.findViewById<FrameLayout>(R.id.colorBlue)
        val containerGreen = view.findViewById<FrameLayout>(R.id.colorGreen)
        val containerPurple = view.findViewById<FrameLayout>(R.id.colorPurple)

        val fillWhite = view.findViewById<ImageView>(R.id.colorWhiteFill)
        val fillBlack = view.findViewById<ImageView>(R.id.colorBlackFill)
        val fillBlue = view.findViewById<ImageView>(R.id.colorBlueFill)
        val fillGreen = view.findViewById<ImageView>(R.id.colorGreenFill)
        val fillPurple = view.findViewById<ImageView>(R.id.colorPurpleFill)

        val checkWhite = view.findViewById<ImageView>(R.id.checkWhite)
        val checkBlack = view.findViewById<ImageView>(R.id.checkBlack)
        val checkBlue = view.findViewById<ImageView>(R.id.checkBlue)
        val checkGreen = view.findViewById<ImageView>(R.id.checkGreen)
        val checkPurple = view.findViewById<ImageView>(R.id.checkPurple)

        // Helper to Set Colors directly on Drawable
        fun setCircleColor(view: ImageView, color: Int) {
            val drawable = view.drawable.mutate()
            if (drawable is GradientDrawable) {
                drawable.setColor(color)
            }
        }
        setCircleColor(fillWhite, Color.WHITE)
        setCircleColor(fillBlack, Color.BLACK)
        setCircleColor(fillBlue, Color.parseColor("#4A6CF7"))
        setCircleColor(fillGreen, Color.parseColor("#2ECC71"))
        setCircleColor(fillPurple, Color.parseColor("#9B59B6"))

        val allChecks = listOf(checkWhite, checkBlack, checkBlue, checkGreen, checkPurple)

        fun updateSelection(selectedCheck: ImageView, color: Int) {
            allChecks.forEach { it.visibility = View.INVISIBLE }
            selectedCheck.visibility = View.VISIBLE
            repo.saveWidgetColor(color)
        }

        containerWhite.setOnClickListener { updateSelection(checkWhite, Color.WHITE) }
        containerBlack.setOnClickListener { updateSelection(checkBlack, Color.BLACK) }
        containerBlue.setOnClickListener { updateSelection(checkBlue, Color.parseColor("#4A6CF7")) }
        containerGreen.setOnClickListener { updateSelection(checkGreen, Color.parseColor("#2ECC71")) }
        containerPurple.setOnClickListener { updateSelection(checkPurple, Color.parseColor("#9B59B6")) }

        val savedColor = repo.getWidgetColor()
        when (savedColor) {
            Color.WHITE -> updateSelection(checkWhite, Color.WHITE)
            Color.BLACK -> updateSelection(checkBlack, Color.BLACK)
            Color.parseColor("#4A6CF7") -> updateSelection(checkBlue, Color.parseColor("#4A6CF7"))
            Color.parseColor("#2ECC71") -> updateSelection(checkGreen, Color.parseColor("#2ECC71"))
            Color.parseColor("#9B59B6") -> updateSelection(checkPurple, Color.parseColor("#9B59B6"))
            else -> { /* none */ }
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
                val newVerse = repo.generateNewVerse()
                if (newVerse != null) {
                    addToHistory(newVerse)
                    displayVerse(newVerse)
                    repo.saveVerse(newVerse)
                }
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