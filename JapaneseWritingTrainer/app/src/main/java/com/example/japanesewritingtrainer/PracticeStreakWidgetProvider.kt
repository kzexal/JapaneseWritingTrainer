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

/** Read-only launcher widget backed by the same PracticeLog streak calculation as Statistics. */
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
                val logs = PracticeLogRepository(context.applicationContext).getAll()
                val state = buildPracticeStreakWidgetState(logs)
                val views = RemoteViews(context.packageName, R.layout.widget_practice_streak).apply {
                    setTextViewText(
                        R.id.widget_current_streak,
                        context.getString(R.string.widget_current_streak, state.currentStreakDays)
                    )
                    setTextViewText(
                        R.id.widget_today_status,
                        context.getString(
                            if (state.practicedToday) {
                                R.string.widget_studied_today
                            } else {
                                R.string.widget_not_studied_today
                            }
                        )
                    )
                    setTextViewText(
                        R.id.widget_longest_streak,
                        context.getString(R.string.widget_longest_streak, state.longestStreakDays)
                    )
                }
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                widgetIds.forEach { manager.updateAppWidget(it, views) }
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
