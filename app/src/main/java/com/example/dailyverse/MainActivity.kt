package com.example.dailyverse

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.Data
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

    // Create a local receiver for "Instant" testing feedback while app is in background
    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // When date changes manually, refresh immediately
            loadInitialVerse()
        }
    }

    private val verseHistory = mutableListOf<Verse>()
    private var historyIndex = -1

    private val genres = arrayOf(
        "All", "Encouragement & Hope", "Wisdom & Guidance",
        "Love & Relationships", "Faith & Trust",
        "Strength & Courage", "Gratitude & Praise"
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) scheduleNotificationsAndAlarms()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        repo = VerseRepository(this)

        if (repo.isDarkMode()) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_bottom_bar)
        applyBottomBarInsets()
        applyHeaderInsets()

        loadInitialVerse()
        checkAndScheduleTasks()
        registerTimeChangeReceiver() // Start listening for time changes

        val splashContainer = findViewById<View>(R.id.splashContainer)
        splashContainer.postDelayed({
            splashContainer.animate().alpha(0f).setDuration(500)
                .withEndAction { splashContainer.visibility = View.GONE }.start()
        }, 2000)

        setupListeners()
    }

    // Force update when coming back from Settings
    override fun onResume() {
        super.onResume()
        loadInitialVerse()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(timeChangeReceiver) } catch (e: Exception) { }
    }

    private fun registerTimeChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // Registering context-based receiver for instant feedback during testing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timeChangeReceiver, filter)
        }
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener { showSettingsBottomSheet() }
        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener { shareVerse() }
        findViewById<ImageView>(R.id.btnPrev).setOnClickListener {
            if (historyIndex > 0) {
                historyIndex--
                val prevVerse = verseHistory[historyIndex]
                displayVerse(prevVerse)
                repo.saveVerse(prevVerse)
            } else Toast.makeText(this, "No previous verses", Toast.LENGTH_SHORT).show()
        }
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

    private fun loadInitialVerse() {
        val daily = repo.getDailyVerse()
        if (daily != null) {
            addToHistory(daily)
            displayVerse(daily)
            // CRITICAL: Always push to widget when app logic runs
            repo.updateWidgets(daily)
        }
    }

    private fun addToHistory(verse: Verse) {
        while (verseHistory.size > historyIndex + 1) verseHistory.removeAt(verseHistory.size - 1)
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

    private fun checkAndScheduleTasks() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                scheduleNotificationsAndAlarms()
            else requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            scheduleNotificationsAndAlarms()
        }
    }

    private fun scheduleNotificationsAndAlarms() {
        scheduleMidnightWidgetUpdate()
        scheduleMorningNotification()
    }

    private fun scheduleMidnightWidgetUpdate() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, WidgetUpdateReceiver::class.java).apply {
            action = "com.example.dailyverse.MIDNIGHT_UPDATE"
        }

        // Remove old alarm to prevent conflicts
        val oldPendingIntent = PendingIntent.getBroadcast(this, 1001, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (oldPendingIntent != null) alarmManager.cancel(oldPendingIntent)

        val pendingIntent = PendingIntent.getBroadcast(this, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1) // Next Midnight
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
        }
    }

    private fun scheduleMorningNotification() {
        val workManager = WorkManager.getInstance(this)
        val morningDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.HOUR_OF_DAY, 24)
        }
        val morningDiff = morningDate.timeInMillis - System.currentTimeMillis()
        val notificationRequest = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(morningDiff, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putBoolean("IS_MIDNIGHT_UPDATE", false).build())
            .addTag("daily_verse_notification")
            .build()
        workManager.enqueueUniquePeriodicWork("daily_verse_notification_work", ExistingPeriodicWorkPolicy.UPDATE, notificationRequest)
    }

    private fun showSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        dialog.setContentView(view)

        val switchDark = view.findViewById<SwitchMaterial>(R.id.switchDarkMode)
        switchDark.isChecked = repo.isDarkMode()
        switchDark.setOnCheckedChangeListener { _, isChecked ->
            repo.saveDarkMode(isChecked)
            view.postDelayed({
                AppCompatDelegate.setDefaultNightMode(if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
                dialog.dismiss()
            }, 300)
        }

        val switchWidgetContent = view.findViewById<SwitchMaterial>(R.id.switchWidgetContent)
        switchWidgetContent.isChecked = repo.isShowVerseOnWidget()
        switchWidgetContent.setOnCheckedChangeListener { _, isChecked -> repo.saveShowVerseOnWidget(isChecked) }

        val btnGenre = view.findViewById<LinearLayout>(R.id.btnSelectGenre)
        val tvCurrent = view.findViewById<TextView>(R.id.tvCurrentGenreSettings)
        tvCurrent.text = repo.getGenrePreference()
        btnGenre.setOnClickListener {
            dialog.dismiss()
            window.decorView.postDelayed({ showNiceGenreSelector() }, 150)
        }

        setupColorSelectors(view)
        setupDeveloperSection(view)
        dialog.show()
    }

    private fun setupColorSelectors(view: View) {
        val checkWhite = view.findViewById<ImageView>(R.id.checkWhite)
        val checkBlack = view.findViewById<ImageView>(R.id.checkBlack)
        val checkBlue = view.findViewById<ImageView>(R.id.checkBlue)
        val checkGreen = view.findViewById<ImageView>(R.id.checkGreen)
        val checkPurple = view.findViewById<ImageView>(R.id.checkPurple)

        val allChecks = listOf(checkWhite, checkBlack, checkBlue, checkGreen, checkPurple)
        fun updateSelection(selectedCheck: ImageView, color: Int) {
            allChecks.forEach { it.visibility = View.INVISIBLE }
            selectedCheck.visibility = View.VISIBLE
            repo.saveWidgetColor(color)
        }

        // Helper to tint the circles
        fun tintCircle(id: Int, color: Int) {
            val drawable = view.findViewById<ImageView>(id).drawable.mutate()
            if (drawable is GradientDrawable) drawable.setColor(color)
        }
        tintCircle(R.id.colorWhiteFill, Color.WHITE)
        tintCircle(R.id.colorBlackFill, Color.BLACK)
        tintCircle(R.id.colorBlueFill, Color.parseColor("#4A6CF7"))
        tintCircle(R.id.colorGreenFill, Color.parseColor("#2ECC71"))
        tintCircle(R.id.colorPurpleFill, Color.parseColor("#9B59B6"))

        view.findViewById<View>(R.id.colorWhite).setOnClickListener { updateSelection(checkWhite, Color.WHITE) }
        view.findViewById<View>(R.id.colorBlack).setOnClickListener { updateSelection(checkBlack, Color.BLACK) }
        view.findViewById<View>(R.id.colorBlue).setOnClickListener { updateSelection(checkBlue, Color.parseColor("#4A6CF7")) }
        view.findViewById<View>(R.id.colorGreen).setOnClickListener { updateSelection(checkGreen, Color.parseColor("#2ECC71")) }
        view.findViewById<View>(R.id.colorPurple).setOnClickListener { updateSelection(checkPurple, Color.parseColor("#9B59B6")) }

        when (repo.getWidgetColor()) {
            Color.WHITE -> updateSelection(checkWhite, Color.WHITE)
            Color.BLACK -> updateSelection(checkBlack, Color.BLACK)
            Color.parseColor("#4A6CF7") -> updateSelection(checkBlue, Color.parseColor("#4A6CF7"))
            Color.parseColor("#2ECC71") -> updateSelection(checkGreen, Color.parseColor("#2ECC71"))
            Color.parseColor("#9B59B6") -> updateSelection(checkPurple, Color.parseColor("#9B59B6"))
        }
    }

    private fun setupDeveloperSection(view: View) {
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
        view.findViewById<TextView>(R.id.btnBugReport).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:jeevaljollyjacob@gmail.com"); putExtra(Intent.EXTRA_SUBJECT, "Bug Report") }) } catch (e: Exception) {}
        }
        view.findViewById<TextView>(R.id.btnBuyCoffee).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.buymeacoffee.com/jeevaljollyjacob"))) }
        view.findViewById<TextView>(R.id.btnWebsite).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://j-j-j-github.github.io/MY-PORTFOLIO/"))) }
    }

    private fun showNiceGenreSelector() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_genre_selection, null)
        dialog.setContentView(view)
        val container = view.findViewById<LinearLayout>(R.id.genreListContainer)
        val currentGenre = repo.getGenrePreference()

        for (genre in genres) {
            val isSelected = genre.equals(currentGenre, ignoreCase = true)
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(48, 32, 48, 32)
                background = if (isSelected) ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_badge).also { it?.setTint(ContextCompat.getColor(this@MainActivity, R.color.brand_light)) } else ContextCompat.getDrawable(this@MainActivity, android.R.color.transparent)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) }
            }
            val tv = TextView(this).apply {
                text = genre
                textSize = 16f
                if (isSelected) { setTextColor(ContextCompat.getColor(this@MainActivity, R.color.brand_primary)); typeface = android.graphics.Typeface.DEFAULT_BOLD }
                else { setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary)); typeface = android.graphics.Typeface.DEFAULT }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            itemLayout.addView(tv)
            if (isSelected) {
                itemLayout.addView(ImageView(this).apply { setImageResource(R.drawable.ic_check); setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.brand_primary)) })
            }
            itemLayout.setOnClickListener {
                repo.saveGenrePreference(genre)
                val newVerse = repo.generateNewVerse()
                if (newVerse != null) { addToHistory(newVerse); displayVerse(newVerse); repo.saveVerse(newVerse) }
                dialog.dismiss()
            }
            container.addView(itemLayout)
        }
        view.findViewById<TextView>(R.id.btnCloseDialog).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun shareVerse() {
        currentVerse?.let {
            startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "\"${it.text}\"\n\n- ${it.reference}"); type = "text/plain" }, null))
        }
    }
}