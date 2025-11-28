package com.example.dailyverse

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class DailyVerseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repo = VerseRepository(context)
        val verse = repo.getDailyVerse()

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.daily_verse_widget)
            if (verse != null) {
                views.setTextViewText(R.id.widgetContent, verse.explanation)
            } else {
                views.setTextViewText(R.id.widgetContent, "Open app to set up daily verses.")
            }

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetContent, pendingIntent)
            views.setOnClickPendingIntent(R.id.widgetTitle, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}