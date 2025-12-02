package com.example.dailyverse

import android.app.AlarmManager
import android.app.Dialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
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

    // Guard to prevent double tutorial launches
    private var isTutorialTriggered = false

    // Runnable for splash logic so we can cancel it if needed
    private val splashRunnable = Runnable {
        val splashContainer = findViewById<View>(R.id.splashContainer)
        // Safety check if view is gone or null
        if (splashContainer == null) return@Runnable

        splashContainer.animate()
            .alpha(0f)
            .setDuration(500)
            .withEndAction {
                splashContainer.visibility = View.GONE

                // FIX: Check boolean guard and Activity state
                // This ensures tutorial runs ONLY once per session
                if (repo.isFirstRun() && !isTutorialTriggered) {
                    if (!isFinishing && !isDestroyed) {
                        isTutorialTriggered = true
                        showTutorial()
                    }
                }
            }
            .start()
    }

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
        // 1. Init Repo and Apply Theme BEFORE super.onCreate
        // This prevents the system "Dark Mode" splash from flashing if the app is set to "Light Mode"
        repo = VerseRepository(this)

        if (repo.isDarkMode()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg_bottom_bar)
        applyBottomBarInsets()
        applyHeaderInsets()

        loadInitialVerse()
        checkAndScheduleTasks()
        registerTimeChangeReceiver()

        // --- SPLASH SCREEN LOGIC ---
        showSplash()

        setupListeners()
    }

    private fun showSplash() {
        val splashContainer = findViewById<View>(R.id.splashContainer)

        // Prevent Android from restoring the "GONE" state automatically
        // This ensures splash shows even after theme change recreation
        splashContainer.isSaveEnabled = false

        splashContainer.visibility = View.VISIBLE
        splashContainer.alpha = 1f

        // Use OnPreDrawListener to ensure UI is ready, then post the Safe Runnable
        splashContainer.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (splashContainer.viewTreeObserver.isAlive) {
                        splashContainer.viewTreeObserver.removeOnPreDrawListener(this)
                    }

                    // Cancel any existing callbacks to ensure we don't double-queue
                    splashContainer.removeCallbacks(splashRunnable)

                    // Schedule the fade out using the safe runnable
                    splashContainer.postDelayed(splashRunnable, 2000)

                    return true
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        loadInitialVerse()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up callbacks to prevent memory leaks or zombie dialogs
        findViewById<View>(R.id.splashContainer)?.removeCallbacks(splashRunnable)
        try { unregisterReceiver(timeChangeReceiver) } catch (e: Exception) { }
    }

    private fun registerTimeChangeReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
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
        val oldPendingIntent = PendingIntent.getBroadcast(this, 1001, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (oldPendingIntent != null) alarmManager.cancel(oldPendingIntent)
        val pendingIntent = PendingIntent.getBroadcast(this, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
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

    // ────────────────────────────────────────────────
    // TUTORIAL LOGIC
    // ────────────────────────────────────────────────

    private data class TutorialStep(val title: String, val desc: String, val iconRes: Int)

    private fun showTutorial() {
        val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_tutorial)

        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setDimAmount(0.85f)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        val steps = listOf(
            TutorialStep("Welcome to\nDaily Verse!", "Discover daily inspiration with verses tailored for you. Start your day with hope and wisdom.", R.drawable.ic_sparkles),
            TutorialStep("Home Screen Widgets", "Add the Daily Verse widget to your home screen to see new verses instantly without opening the app.", R.drawable.ic_widgets_outline),
            TutorialStep("Customize Categories", "Go to Settings to select specific topics like 'Hope', 'Faith', or 'Wisdom' that match your needs.", R.drawable.ic_category_filter),
            TutorialStep("Your Style", "Switch between Light & Dark mode and pick your favorite widget color in Settings.", R.drawable.ic_palette_outline),
            TutorialStep("You're all set!", "You are ready to explore.\nI hope this app brings a little light to your day.", R.drawable.ic_check)
        )

        var currentStepIndex = 0

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTutorialTitle)
        val tvDesc = dialog.findViewById<TextView>(R.id.tvTutorialDesc)
        val btnNext = dialog.findViewById<Button>(R.id.btnNext)
        val btnSkip = dialog.findViewById<TextView>(R.id.btnSkip)
        val containerIndicators = dialog.findViewById<LinearLayout>(R.id.layoutIndicators)
        val imgIcon = dialog.findViewById<ImageView>(R.id.imgTutorialIcon)
        val card = dialog.findViewById<CardView>(R.id.cardTutorial)

        // Prevent layout jumping
        tvTitle.setLines(2)
        tvDesc.setLines(5)

        val isDark = repo.isDarkMode()
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            if (isDark) intArrayOf(Color.parseColor("#2C2C2C"), Color.parseColor("#121212"))
            else intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#F5F7FA"))
        )
        gradientDrawable.cornerRadius = 28f * resources.displayMetrics.density

        (card.getChildAt(0) as? LinearLayout)?.background = gradientDrawable
        card.setCardBackgroundColor(if (isDark) Color.parseColor("#2C2C2C") else Color.WHITE)

        if (isDark) {
            tvTitle.setTextColor(Color.WHITE)
            tvDesc.setTextColor(Color.parseColor("#B0B0B0"))
            btnSkip.setTextColor(Color.parseColor("#909090"))
        } else {
            tvTitle.setTextColor(Color.BLACK)
            tvDesc.setTextColor(Color.parseColor("#757575"))
            btnSkip.setTextColor(Color.parseColor("#757575"))
        }

        btnSkip.background = null
        btnSkip.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().alpha(0.5f).setDuration(100).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().alpha(1f).setDuration(100).start()
            }
            false
        }

        fun updateUI() {
            val step = steps[currentStepIndex]
            tvTitle.text = step.title
            tvDesc.text = step.desc
            imgIcon.setImageResource(step.iconRes)

            imgIcon.animate().cancel()
            imgIcon.scaleX = 1f; imgIcon.scaleY = 1f; imgIcon.alpha = 1f; imgIcon.translationY = 0f; imgIcon.rotation = 0f; imgIcon.translationX = 0f

            when (currentStepIndex) {
                0 -> {
                    imgIcon.scaleX = 0.5f; imgIcon.scaleY = 0.5f; imgIcon.alpha = 0f
                    imgIcon.animate().scaleX(1.1f).scaleY(1.1f).alpha(1f).setDuration(500).withEndAction {
                        imgIcon.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    }.start()
                }
                1 -> {
                    imgIcon.translationX = -50f; imgIcon.alpha = 0f
                    imgIcon.animate().translationX(0f).alpha(1f).setDuration(400).setInterpolator(OvershootInterpolator()).start()
                }
                2 -> {
                    imgIcon.translationY = 50f; imgIcon.alpha = 0f
                    imgIcon.animate().translationY(0f).alpha(1f).setDuration(500).setInterpolator(DecelerateInterpolator()).start()
                }
                3 -> {
                    imgIcon.rotation = -90f; imgIcon.alpha = 0f
                    imgIcon.animate().rotation(0f).alpha(1f).setDuration(600).setInterpolator(OvershootInterpolator()).start()
                }
                4 -> {
                    imgIcon.scaleX = 0f; imgIcon.scaleY = 0f
                    imgIcon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(400).setInterpolator(OvershootInterpolator()).withEndAction {
                        imgIcon.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    }.start()
                }
            }

            val params = btnNext.layoutParams as RelativeLayout.LayoutParams

            if (currentStepIndex == steps.size - 1) {
                btnNext.text = "Get Started"
                btnSkip.visibility = View.INVISIBLE

                params.removeRule(RelativeLayout.ALIGN_PARENT_END)
                params.addRule(RelativeLayout.CENTER_HORIZONTAL)
            } else {
                btnNext.text = "Next"
                btnSkip.visibility = View.VISIBLE

                params.removeRule(RelativeLayout.CENTER_HORIZONTAL)
                params.addRule(RelativeLayout.ALIGN_PARENT_END)
            }
            btnNext.layoutParams = params

            containerIndicators.removeAllViews()
            for (i in steps.indices) {
                val dot = View(this)
                val width = if (i == currentStepIndex) 32 else 12
                val height = 12
                val paramsDot = LinearLayout.LayoutParams(width, height)
                paramsDot.marginEnd = 12
                dot.layoutParams = paramsDot

                val drawable = GradientDrawable()
                drawable.shape = GradientDrawable.RECTANGLE
                drawable.cornerRadius = 20f

                if (i == currentStepIndex) {
                    drawable.setColor(ContextCompat.getColor(this, R.color.brand_primary))
                } else {
                    drawable.setColor(if(isDark) Color.parseColor("#424242") else Color.parseColor("#E0E0E0"))
                }
                dot.background = drawable
                containerIndicators.addView(dot)
            }

            card.alpha = 0.8f
            card.animate().alpha(1f).setDuration(200).start()
        }

        btnNext.setOnClickListener {
            if (currentStepIndex < steps.size - 1) {
                currentStepIndex++
                updateUI()
            } else {
                repo.setFirstRunCompleted()
                dialog.dismiss()
            }
        }

        btnSkip.setOnClickListener {
            repo.setFirstRunCompleted()
            dialog.dismiss()
        }

        updateUI()

        card.alpha = 0f
        dialog.show()
        card.animate().alpha(1f).setDuration(500).start()
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

        view.findViewById<LinearLayout>(R.id.btnAppTutorial).setOnClickListener {
            dialog.dismiss()
            window.decorView.postDelayed({ showTutorial() }, 200)
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