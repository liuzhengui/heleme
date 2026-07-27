package com.zhengui.waterreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 临时测试用 Receiver，导出后可通过 adb broadcast 触发
 * 测试完成后需要删除
 * 用法: adb shell am broadcast -n com.zhengui.waterreminder/.receiver.TestReceiver --es test_action <action>
 */
class TestReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TestReceiver"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val action = intent.getStringExtra("test_action") ?: "schedule_now"
        Log.d(TAG, "收到测试广播: action=$action")

        scope.launch {
            try {
                when (action) {
                    "schedule_now" -> handleScheduleNow(context)
                    "interval_now" -> handleIntervalNow(context)
                    "fixed_now" -> handleFixedNow(context)
                    "drink" -> handleDrink(context)
                    "all" -> {
                        handleScheduleNow(context)
                        handleIntervalNow(context)
                        handleFixedNow(context)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "测试操作失败: $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleScheduleNow(context: Context) {
        ReminderScheduler.setLastDrinkTime(context, 0L)
        ReminderScheduler.cancelReminder(context)
        ReminderScheduler.cancelSmallCycle(context)
        ReminderScheduler.cancelAllReminders(context)
        ReminderScheduler.scheduleNextReminder(context)
        ReminderScheduler.scheduleAllReminders(context)
        Log.d(TAG, "已重新调度所有提醒（last_drink_time=0）")
    }

    private suspend fun handleIntervalNow(context: Context) {
        val db = (context.applicationContext as App).database
        val typeId = PreferenceManager.getCurrentTypeId(context)
        val type = db.personTypeDao().getById(typeId)
        val intervalMin = type?.reminderIntervalMin ?: 90

        val triggerTime = System.currentTimeMillis() - (intervalMin - 1) * 60 * 1000L
        ReminderScheduler.setLastDrinkTime(context, triggerTime)
        ReminderScheduler.cancelReminder(context)
        ReminderScheduler.cancelSmallCycle(context)
        ReminderScheduler.scheduleNextReminder(context)
        Log.d(TAG, "间隔提醒将在约1分钟后触发 (interval=${intervalMin}min)")
    }

    private fun handleFixedNow(context: Context) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 1)
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        // 使用 requestCode = 9999 避免和已有的固定时间提醒冲突
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("suggested_amount", 200)
            putExtra("reminder_time_id", 9999L)
            putExtra("reminder_hour", hour)
            putExtra("reminder_minute", minute)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 9999, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        // 委托给 scheduleExactAlarm 的逻辑（手动调度）
        val triggerTime = cal.timeInMillis
        val triggerDate = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(triggerTime))
        Log.d(TAG, "固定时间提醒将在1分钟后触发: $triggerDate, hour=$hour, minute=$minute")

        val canExact = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            val showIntent = android.app.PendingIntent.getActivity(
                context, -3,
                Intent(context, com.zhengui.waterreminder.ui.MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                android.app.AlarmManager.AlarmClockInfo(triggerTime, showIntent),
                pendingIntent
            )
        }
    }

    private fun handleDrink(context: Context) {
        val now = System.currentTimeMillis()
        ReminderScheduler.setLastDrinkTime(context, now)
        ReminderScheduler.cancelSmallCycle(context)
        ReminderScheduler.scheduleNextReminder(context)
        Log.d(TAG, "模拟喝水打卡，已重新调度间隔提醒")
    }
}
