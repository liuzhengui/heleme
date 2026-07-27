package com.zhengui.waterreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.notification.FullscreenReminderActivity
import com.zhengui.waterreminder.notification.NotificationHelper
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReminderReceiver"
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "ReminderReceiver 协程异常", e)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    override fun onReceive(context: Context, intent: Intent) {
        val now = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        Log.i(TAG, "========== onReceive 触发 at $now ==========")
        Log.i(TAG, "Intent extras: suggested_amount=${intent.getIntExtra("suggested_amount", -1)}, is_interval=${intent.getBooleanExtra("is_interval_reminder", false)}, is_small_cycle=${intent.getBooleanExtra("is_small_cycle_reminder", false)}, reminder_time_id=${intent.getLongExtra("reminder_time_id", -1)}, hour=${intent.getIntExtra("reminder_hour", -1)}, minute=${intent.getIntExtra("reminder_minute", -1)}")
        val isReminderEnabled = PreferenceManager.isReminderEnabled(context)
        Log.i(TAG, "提醒总开关: $isReminderEnabled")

        if (!isReminderEnabled) {
            Log.i(TAG, "提醒总开关已关闭，忽略本次提醒触发")
            return
        }

        val pendingResult = goAsync()

        val suggestedAmount = intent.getIntExtra("suggested_amount", 200)
        val isIntervalReminder = intent.getBooleanExtra("is_interval_reminder", false)
        val isSmallCycle = intent.getBooleanExtra("is_small_cycle_reminder", false)

        when {
            isSmallCycle -> handleSmallCycle(context, suggestedAmount, pendingResult)
            isIntervalReminder -> handleIntervalReminder(context, suggestedAmount, pendingResult)
            else -> handleFixedTimeReminder(context, suggestedAmount, intent, pendingResult)
        }
    }

    /**
     * 小周期检查：判断用户是否已在提醒后喝了水
     */
    private fun handleSmallCycle(context: Context, suggestedAmount: Int, pendingResult: PendingResult) {
        val lastDrinkTime = ReminderScheduler.getLastDrinkTime(context)
        val lastTriggerTime = ReminderScheduler.getLastIntervalTriggerTime(context)
        Log.i(TAG, "小周期检查: lastDrinkTime=$lastDrinkTime, lastTriggerTime=$lastTriggerTime, drink > trigger = ${lastDrinkTime > lastTriggerTime}")

        if (lastDrinkTime > lastTriggerTime) {
            // 用户已喝水，停止小周期
            Log.i(TAG, "小周期: 用户已喝水，停止小周期")
            pendingResult.finish()
        } else {
            // 用户还没喝水，先检查时段再决定是否弹通知
            if (PreferenceManager.isReminderEnabled(context)) {
                scope.launch {
                    try {
                        val inPeriod = isInNotificationPeriod(context)
                        Log.i(TAG, "小周期: 提醒开启，通知时段内=$inPeriod")
                        if (inPeriod) {
                            // 在通知时段内，弹通知并继续小周期
                            val notificationHelper = NotificationHelper(context)
                            notificationHelper.createChannel()
                            notificationHelper.showSmallCycleReminderNotification(suggestedAmount)
                            launchFullScreenReminder(context, suggestedAmount, FullscreenReminderActivity.TYPE_SMALL_CYCLE)
                            Log.i(TAG, "小周期: 弹通知，继续调度5分钟后小周期")
                            ReminderScheduler.scheduleSmallCycle(context, suggestedAmount)
                        }
                        // 不在时段内，不弹通知，小周期自然停止
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                // 提醒已关闭，仍弹最后一次通知告知用户
                Log.i(TAG, "小周期: 提醒已关闭，弹最后一次通知")
                try {
                    val notificationHelper = NotificationHelper(context)
                    notificationHelper.createChannel()
                    notificationHelper.showSmallCycleReminderNotification(suggestedAmount)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    /**
     * 间隔提醒（大周期）：发通知、重置基准时间、调度下次提醒、启动小周期
     */
    private fun handleIntervalReminder(context: Context, suggestedAmount: Int, pendingResult: PendingResult) {
        Log.i(TAG, "间隔提醒处理: suggestedAmount=$suggestedAmount")
        scope.launch {
            try {
                val inPeriod = isInNotificationPeriod(context)
                Log.i(TAG, "间隔提醒: 通知时段内=$inPeriod")

                if (inPeriod) {
                    val notificationHelper = NotificationHelper(context)
                    notificationHelper.createChannel()
                    notificationHelper.showIntervalReminderNotification(suggestedAmount)
                    launchFullScreenReminder(context, suggestedAmount, FullscreenReminderActivity.TYPE_INTERVAL)
                    Log.i(TAG, "间隔提醒: 已弹通知")
                }

                if (PreferenceManager.isReminderEnabled(context)) {
                    // 不修改 lastDrinkTime，保持用户真实的喝水记录
                    // scheduleNextReminder 内部会根据 lastDrinkTime 或当前时间自动计算
                    ReminderScheduler.scheduleNextReminder(context)
                    Log.i(TAG, "间隔提醒: 已调度下次间隔")

                    if (inPeriod) {
                        ReminderScheduler.onIntervalReminderTriggered(context, suggestedAmount)
                        Log.i(TAG, "间隔提醒: 已启动小周期")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 固定时间点提醒：发通知并重新调度到明天
     * 使用自定义标签区分周期提醒
     */
    private fun handleFixedTimeReminder(context: Context, suggestedAmount: Int, intent: Intent, pendingResult: PendingResult) {
        val reminderTimeId = intent.getLongExtra("reminder_time_id", -1)
        val hour = intent.getIntExtra("reminder_hour", -1)
        val minute = intent.getIntExtra("reminder_minute", -1)
        val label = intent.getStringExtra("reminder_label") ?: ""
        Log.i(TAG, "固定时间提醒处理: id=$reminderTimeId, time=$hour:$minute, amount=$suggestedAmount, label=$label")

        scope.launch {
            try {
                // 检查该提醒是否仍存在于数据库中（用户可能已删除）
                val db = (context.applicationContext as App).database
                val stillExists = if (reminderTimeId > 0) {
                    db.reminderTimeDao().getById(reminderTimeId) != null
                } else false

                if (!stillExists && reminderTimeId > 0) {
                    Log.i(TAG, "固定时间提醒: id=$reminderTimeId 已在数据库中删除，忽略本次触发")
                    return@launch
                }

                val notificationHelper = NotificationHelper(context)
                notificationHelper.createChannel()
                notificationHelper.showFixedReminderNotification(label, suggestedAmount)
                launchFullScreenReminder(context, suggestedAmount, FullscreenReminderActivity.TYPE_FIXED)
                Log.i(TAG, "固定时间提醒: 已弹通知 (label=$label)")

                if (reminderTimeId > 0 && hour >= 0 && minute >= 0 && PreferenceManager.isReminderEnabled(context)) {
                    try {
                        ReminderScheduler.scheduleFixedReminderToTomorrow(context, reminderTimeId, hour, minute)
                        Log.i(TAG, "固定时间提醒: 已调度到明天 $hour:$minute")
                    } catch (e: Exception) {
                        Log.e(TAG, "固定时间提醒调度明天失败，回退全量调度", e)
                        ReminderScheduler.scheduleAllReminders(context)
                    }
                } else {
                    Log.i(TAG, "固定时间提醒: 跳过重新调度 (reminderTimeId=$reminderTimeId, hour=$hour, minute=$minute, enabled=${PreferenceManager.isReminderEnabled(context)})")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 检查当前时间是否在通知时段内
     */
    private suspend fun isInNotificationPeriod(context: Context): Boolean {
        val db = (context.applicationContext as App).database
        val typeId = PreferenceManager.getCurrentTypeId(context)
        val type = db.personTypeDao().getById(typeId)

        val startHour = type?.notificationStartHour ?: 8
        val startMinute = type?.notificationStartMinute ?: 0
        val endHour = type?.notificationEndHour ?: 21
        val endMinute = type?.notificationEndMinute ?: 0

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    /**
     * 直接启动全屏提醒Activity，不依赖通知的fullScreenIntent（国产ROM常拦截）
     */
    private fun launchFullScreenReminder(context: Context, amount: Int, reminderType: String) {
        if (!PreferenceManager.isFullscreenReminderEnabled(context)) {
            Log.i(TAG, "全屏提醒已关闭，跳过启动全屏提醒Activity")
            return
        }
        try {
            val intent = Intent(context, FullscreenReminderActivity::class.java).apply {
                putExtra(FullscreenReminderActivity.EXTRA_REMINDER_TYPE, reminderType)
                putExtra(FullscreenReminderActivity.EXTRA_AMOUNT, amount)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "已启动全屏提醒: type=$reminderType, amount=$amount")
        } catch (e: Exception) {
            Log.e(TAG, "启动全屏提醒失败", e)
        }
    }
}
