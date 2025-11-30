package com.example.dailyverse

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews

class DailyVerseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {

        val repo = VerseRepository(context)
        val verse = repo.loadSavedVerse()    // READ ONLY — Worker generates
            ?: repo.getDailyVerse()          // fallback if nothing saved

        for (appWidgetId in appWidgetIds) {

            val views = RemoteViews(context.packageName, R.layout.daily_verse_widget)

            // Determine background & text colors
            val bgColor = repo.getWidgetColor()

            val luminance = (0.299 * Color.red(bgColor) +
                    0.587 * Color.green(bgColor) +
                    0.114 * Color.blue(bgColor)) / 255
            val isDarkBg = luminance < 0.5

            val textColor = if (isDarkBg) Color.WHITE else Color.parseColor("#333333")
            val titleColor = if (isDarkBg) Color.parseColor("#818CF8") else Color.parseColor("#5C6BC0")
            val promptColor = if (isDarkBg) Color.parseColor("#CCCCCC") else Color.parseColor("#888888")

            val showVerse = repo.isShowVerseOnWidget()

            val contentText = if (showVerse)
                "\"${verse?.text ?: ""}\""
            else
                verse?.explanation ?: ""

            val promptText = if (showVerse)
                "Tap to reveal meaning →"
            else
                "Tap to reveal verse →"

            // Apply colors & content
            views.setInt(R.id.widgetRoot, "setBackgroundColor", bgColor)
            views.setTextViewText(R.id.widgetContent, contentText)
            views.setTextColor(R.id.widgetContent, textColor)
            views.setTextViewText(R.id.widgetTitle, verse?.reference ?: "")
            views.setTextColor(R.id.widgetTitle, titleColor)
            views.setTextViewText(R.id.widgetPrompt, promptText)
            views.setTextColor(R.id.widgetPrompt, promptColor)

            // Open main activity when tapped
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widgetContent, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetTitle, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetPrompt, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DailyVerseWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, ids)
        }
    }
}