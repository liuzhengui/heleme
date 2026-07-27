package com.zhengui.waterreminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.ui.MainActivity
import kotlinx.coroutines.*

class WaterWidgetProvider : AppWidgetProvider() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    companion object {
        private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            widgetScope.launch {
                try {
                    val db = (context.applicationContext as App).database
                    val prefs = context.getSharedPreferences("water_reminder_prefs", Context.MODE_PRIVATE)
                    val typeId = prefs.getLong("current_type_id", 1L)

                    val now = java.util.Calendar.getInstance()
                    val dayStart = now.apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    val dayEnd = dayStart + 24 * 60 * 60 * 1000L

                    val todayTotal = db.waterRecordDao().getDailyTotal(dayStart, dayEnd) ?: 0
                    val type = db.personTypeDao().getById(typeId)
                    val goal = type?.dailyGoalMl ?: 2000
                    val percent = if (goal > 0) (todayTotal * 100 / goal).coerceAtMost(100) else 0

                    for (id in ids) {
                        val views = RemoteViews(context.packageName, R.layout.widget_small)
                        views.setTextViewText(R.id.tvPercent, "${percent}%")
                        views.setTextViewText(R.id.tvProgress, "已喝 ${todayTotal}ml / 目标 ${goal}ml")

                        // ProgressBar: use setInt
                        views.setInt(R.id.progressBar, "setProgress", percent)
                        views.setInt(R.id.progressBar, "setMax", 100)

                        // Click opens app
                        val intent = Intent(context, MainActivity::class.java)
                        val pi = PendingIntent.getActivity(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(R.id.widgetRoot, pi)

                        manager.updateAppWidget(id, views)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WaterWidget", "更新小组件失败", e)
                }
            }
        }
    }
}
