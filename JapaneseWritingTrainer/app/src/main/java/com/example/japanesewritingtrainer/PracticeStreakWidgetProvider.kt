package com.example.japanesewritingtrainer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Compact 2x1 widget showing only the current streak and due-card count. */
class PracticeStreakWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isNotEmpty()) {
            updateWidgets(context, appWidgetManager, appWidgetIds, goAsync())
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, PracticeStreakWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) updateWidgets(context, manager, ids, goAsync())
            }
        }
    }

    private fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
        pendingResult: BroadcastReceiver.PendingResult
    ) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val logs = PracticeLogRepository(appContext).getAll()
                val progress = CardProgressRepository(appContext).getAll()
                val now = System.currentTimeMillis()
                val dueCount = progress.count { it.isDue(now) }
                val streak = buildPracticeStreakWidgetState(logs, now).currentStreakDays

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val views = RemoteViews(
                    context.packageName,
                    R.layout.widget_practice_streak_wide
                ).apply {
                    setTextViewText(
                        R.id.widget_current_streak,
                        context.getString(R.string.widget_current_streak, streak)
                    )
                    setTextViewText(
                        R.id.widget_due_count,
                        context.getString(R.string.widget_due_count, dueCount)
                    )
                    setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                    setContentDescription(
                        R.id.widget_root,
                        context.getString(
                            R.string.widget_accessibility_description,
                            dueCount,
                            streak
                        )
                    )
                }

                widgetIds.forEach { widgetId ->
                    manager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REFRESH =
            "com.example.japanesewritingtrainer.action.REFRESH_STREAK_WIDGET"

        /** Request a refresh only after the corresponding database write has succeeded. */
        fun requestUpdate(context: Context) {
            context.applicationContext.sendBroadcast(
                Intent(context.applicationContext, PracticeStreakWidgetProvider::class.java)
                    .setAction(ACTION_REFRESH)
            )
        }
    }
}