package com.example.japanesewritingtrainer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Responsive, read-only launcher widget backed by the app's existing repositories. */
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
                val state = buildPracticeStreakWidgetState(logs, now)
                val zone = ZoneId.systemDefault()
                val practicedDates = logs.map {
                    Instant.ofEpochMilli(it.timestampEpochMillis).atZone(zone).toLocalDate()
                }.toSet()
                val today = LocalDate.now(zone)
                val hourOfDay = java.time.ZonedDateTime.now(zone).hour
                val streakAtRisk = hourOfDay >= 18 && !state.practicedToday

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                fun createViews(layoutId: Int, includeDots: Boolean): RemoteViews =
                    RemoteViews(context.packageName, layoutId).apply {
                        setTextViewText(
                            R.id.widget_due_count,
                            context.getString(
                                if (dueCount == 0 && !state.practicedToday) {
                                    R.string.widget_new_cards_today
                                } else {
                                    R.string.widget_due_count
                                },
                                dueCount
                            )
                        )
                        setTextViewText(
                            R.id.widget_current_streak,
                            context.getString(R.string.widget_current_streak, state.currentStreakDays)
                        )
                        if (layoutId != R.layout.widget_practice_streak_small) {
                            setTextViewText(
                                R.id.widget_today_status,
                                context.getString(
                                    if (state.practicedToday) R.string.widget_studied_today
                                    else if (streakAtRisk) R.string.widget_streak_at_risk
                                    else if (dueCount == 0) R.string.widget_new_cards_today
                                    else R.string.widget_not_studied_today
                                )
                            )
                        )
                        if (streakAtRisk) {
                            setTextColor(R.id.widget_current_streak, context.getColor(R.color.widget_streak_warning))
                        }
                        }
                        if (layoutId == R.layout.widget_practice_streak_medium) {
                            setTextViewText(
                                R.id.widget_longest_streak,
                                context.getString(R.string.widget_longest_streak, state.longestStreakDays)
                            )
                            if (includeDots) {
                                for (offset in 6 downTo 0) {
                                    val date = today.minusDays(offset.toLong())
                                    val viewId = when (offset) {
                                        6 -> R.id.widget_day_1
                                        5 -> R.id.widget_day_2
                                        4 -> R.id.widget_day_3
                                        3 -> R.id.widget_day_4
                                        2 -> R.id.widget_day_5
                                        1 -> R.id.widget_day_6
                                        else -> R.id.widget_day_7
                                    }
                                    setTextColor(
                                        viewId,
                                        context.getColor(
                                            if (date in practicedDates) R.color.widget_day_done
                                            else R.color.widget_day_empty
                                        )
                                    )
                                    setContentDescription(
                                        viewId,
                                        context.getString(
                                            if (date in practicedDates) R.string.widget_day_done
                                            else R.string.widget_day_empty
                                        )
                                    )
                                }
                            }
                        }
                        setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)
                        setContentDescription(
                            R.id.widget_root,
                            context.getString(
                                R.string.widget_accessibility_description,
                                dueCount,
                                state.currentStreakDays
                            )
                        )
                    }

                widgetIds.forEach { widgetId ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val responsiveViews = mapOf(
                            SizeF(110f, 110f) to createViews(
                                R.layout.widget_practice_streak_small, false
                            ),
                            SizeF(250f, 110f) to createViews(
                                R.layout.widget_practice_streak_medium, true
                            ),
                            SizeF(250f, 55f) to createViews(
                                R.layout.widget_practice_streak_wide, false
                            )
                        )
                        manager.updateAppWidget(widgetId, RemoteViews(responsiveViews))
                    } else {
                        manager.updateAppWidget(
                            widgetId,
                            createViews(R.layout.widget_practice_streak_medium, true)
                        )
                    }
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
