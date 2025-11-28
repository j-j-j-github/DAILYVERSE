package com.example.dailyverse

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Implementation of App Widget functionality.
 */
class DailyVerseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    // 1. Initialize the Repository to get data
    val repo = VerseRepository(context)
    val verse = repo.getDailyVerse()

    // 2. Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.daily_verse_widget)

    if (verse != null) {
        // 3. THE HOOK: Display the MEANING (Explanation) in the widget
        // We use the ID 'widgetContent' which matches your XML
        views.setTextViewText(R.id.widgetContent, verse.explanation)
    } else {
        views.setTextViewText(R.id.widgetContent, "Open app to set up daily verses.")
    }

    // 4. Create an Intent to launch MainActivity when clicked
    val intent = Intent(context, MainActivity::class.java)
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // 5. Attach the click listener to the main content text
    // (Matches the android:id="@+id/widgetContent" in your XML)
    views.setOnClickPendingIntent(R.id.widgetContent, pendingIntent)

    // Optional: Also attach to the title so it's easier to hit
    views.setOnClickPendingIntent(R.id.widgetTitle, pendingIntent)

    // 6. Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}