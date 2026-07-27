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

class WaterWidgetLargeProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    companion object {
        private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private fun getDrinkPendingIntent(context: Context, widgetId: Int, amount: Int): PendingIntent {
            val intent = Intent(context, WidgetDrinkReceiver::class.java).apply {
                putExtra("drink_amount", amount)
            }
            return PendingIntent.getBroadcast(context, widgetId * 100 + amount, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

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
                    val records = db.waterRecordDao().getByDate(dayStart, dayEnd)
                    val drinkCount = records.size
                    val type = db.personTypeDao().getById(typeId)
                    val goal = type?.dailyGoalMl ?: 2000
                    val percent = if (goal > 0) (todayTotal * 100 / goal).coerceAtMost(100) else 0

                    for (id in ids) {
                        val views = RemoteViews(context.packageName, R.layout.widget_large)
                        views.setTextViewText(R.id.tvTitle, "今日喝水")
                        views.setTextViewText(R.id.tvAmount, "${todayTotal}ml / ${goal}ml")
                        views.setTextViewText(R.id.tvPercent, "${percent}%")
                        views.setTextViewText(R.id.tvCount, "今日已喝 ${drinkCount} 次")
                        views.setInt(R.id.progressBar, "setProgress", percent)
                        views.setInt(R.id.progressBar, "setMax", 100)

                        views.setOnClickPendingIntent(R.id.btnDrink150, getDrinkPendingIntent(context, id, 150))
                        views.setOnClickPendingIntent(R.id.btnDrink200, getDrinkPendingIntent(context, id, 200))
                        views.setOnClickPendingIntent(R.id.btnDrink250, getDrinkPendingIntent(context, id, 250))

                        val openIntent = Intent(context, MainActivity::class.java)
                        val pi = PendingIntent.getActivity(context, id.toInt(), openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                        views.setOnClickPendingIntent(R.id.widgetRoot, pi)

                        manager.updateAppWidget(id, views)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WaterWidgetL", "更新大组件失败", e)
                }
            }
        }
    }
}
