package com.zhengui.waterreminder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.receiver.ReminderReceiver
import com.zhengui.waterreminder.ui.MainActivity
import com.zhengui.waterreminder.util.PreferenceManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "协程异常", e)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    /**
     * 调度/取消操作共用同一把锁，避免多个协程同时读写 AlarmManager 和数据库时出现竞争
     *（例如 startReminder 中 cancel 与 schedule 并发执行导致闹钟被误取消）
     */
    private val mutex = Mutex()

    private const val PREFS_NAME = "water_reminder_prefs"
    private const val KEY_LAST_DRINK_TIME = "last_drink_time"
    private const val REQUEST_CODE_INTERVAL = 0
    private const val REQUEST_CODE_SMALL_CYCLE = -1
    private const val REQUEST_CODE_ALARM_CLOCK = -2
    private const val KEY_LAST_INTERVAL_TRIGGER_TIME = "last_interval_trigger_time"
    private const val KEY_NEXT_REMINDER_TIME = "next_reminder_time"
    const val SMALL_CYCLE_MINUTES = 5

    private fun isReminderEnabled(context: Context): Boolean =
        PreferenceManager.isReminderEnabled(context)

    private fun fmtTime(ms: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))

    /**
     * 调度间隔提醒（基于开始时间或打卡时间）
     * - 首次提醒：从类型的开始时间（如 9:00）算起
     * - 如果已过开始时间但还没到结束时间：从当前时间 + 间隔
     * - 打卡后调用时：从打卡时刻 + 间隔
     */
    fun scheduleNextReminder(context: Context, forceReschedule: Boolean = false) {
        if (!isReminderEnabled(context)) {
            Log.i(TAG, "提醒总开关已关闭，跳过调度间隔提醒")
            return
        }
        Log.i(TAG, "▶ scheduleNextReminder 被调用(force=$forceReschedule), 调用栈: ${Thread.currentThread().stackTrace.slice(1..5).joinToString(" ← ") { "${it.className.substringAfterLast(".")}.${it.methodName}" }}")
        scope.launch {
            mutex.withLock {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val now = System.currentTimeMillis()

                // 兜底恢复场景（解锁/开机/Worker/onResume 等）：若已排定未来的大周期闹钟，
                // 保持原时间重新注册即可，绝不能重算为 now+interval。
                // 否则每次解锁屏幕都会把大周期推迟到「解锁时刻+interval」，
                // 用户频繁解锁时大周期永远不触发 → 关闭小周期后表现为「不再提醒」。
                if (!forceReschedule) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val nextPlanned = prefs.getLong(KEY_NEXT_REMINDER_TIME, 0L)
                    if (nextPlanned > now) {
                        val db = (context.applicationContext as App).database
                        val typeId = PreferenceManager.getCurrentTypeId(context)
                        val type = db.personTypeDao().getById(typeId)
                        val defaultAmount = type?.defaultAmountMl ?: 200
                        Log.i(TAG, "存在未来计划 ${fmtTime(nextPlanned)}，保持原时间重新注册（不推迟）")
                        scheduleAlarm(context, nextPlanned, defaultAmount)
                        PreferenceManager.setReminderEnabled(context, true)
                        return@withLock
                    }
                }

                // 先取消旧的间隔闹钟，防止重复调度（在同一锁内原子执行，避免竞态条件）
                val cancelIntent = Intent(context, ReminderReceiver::class.java)
                val cancelPendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_INTERVAL,
                    cancelIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(cancelPendingIntent)
                Log.i(TAG, "已取消旧的间隔提醒闹钟")

                val db = (context.applicationContext as App).database
                val typeId = PreferenceManager.getCurrentTypeId(context)
                val type = db.personTypeDao().getById(typeId)
                val intervalMin = type?.reminderIntervalMin ?: 120
                val defaultAmount = type?.defaultAmountMl ?: 200

                val startHour = type?.notificationStartHour ?: 8
                val startMinute = type?.notificationStartMinute ?: 0
                val endHour = type?.notificationEndHour ?: 21
                val endMinute = type?.notificationEndMinute ?: 0

                val triggerTime: Long

                // 获取上次打卡时间
                val lastDrinkTime = getLastDrinkTime(context)

                if (lastDrinkTime > 0) {
                    // 有打卡记录：从打卡时间 + 间隔
                    triggerTime = lastDrinkTime + intervalMin * 60 * 1000L
                    Log.i(TAG, "调度间隔提醒: lastDrinkTime=${fmtTime(lastDrinkTime)}, interval=${intervalMin}min, rawTriggerTime=${fmtTime(triggerTime)}")
                } else {
                    // 没有打卡记录：从今天的开始时间算起
                    val startCalendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val startTime = startCalendar.timeInMillis

                    triggerTime = if (now < startTime) {
                        // 还没到开始时间，从开始时间算
                        startTime
                    } else {
                        // 已过开始时间，从当前时间 + 间隔
                        now + intervalMin * 60 * 1000L
                    }
                    Log.i(TAG, "调度间隔提醒: 无打卡记录, now=${fmtTime(now)}, interval=${intervalMin}min, rawTriggerTime=${fmtTime(triggerTime)}")
                }

                Log.i(TAG, "调度间隔提醒: 时段=${startHour}:${"%02d".format(startMinute)}-${endHour}:${"%02d".format(endMinute)}")

                // 检查触发时间是否早于今天的开始时间
                val startCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, startHour)
                    set(Calendar.MINUTE, startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startTime = startCalendar.timeInMillis

                // 钳位：triggerTime 不能早于开始时间
                var effectiveTriggerTime = if (triggerTime < startTime) startTime else triggerTime
                Log.i(TAG, "钳位后: effectiveTriggerTime=${fmtTime(effectiveTriggerTime)} (startTime=${fmtTime(startTime)})")

                // 安全兜底：确保触发时间在未来，防止无限循环
                if (effectiveTriggerTime <= now) {
                    val oldEffective = effectiveTriggerTime
                    effectiveTriggerTime = now + intervalMin * 60 * 1000L
                    Log.i(TAG, "安全兜底: effectiveTriggerTime(${fmtTime(oldEffective)}) <= now(${fmtTime(now)}), 调整为 now+interval=${fmtTime(effectiveTriggerTime)}")
                }

                // 检查触发时间是否超出今天的结束时间
                val endCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, endHour)
                    set(Calendar.MINUTE, endMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endTime = endCalendar.timeInMillis

                if (effectiveTriggerTime > endTime) {
                    // 已超出结束时间，调度到明天的开始时间
                    val tomorrowStart = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    Log.i(TAG, "超出结束时间(${fmtTime(endTime)}), 调度到明天: ${fmtTime(tomorrowStart.timeInMillis)}")
                    scheduleAlarm(context, tomorrowStart.timeInMillis, defaultAmount)
                } else {
                    Log.i(TAG, "★ 最终调度大周期闹钟 → ${fmtTime(effectiveTriggerTime)} (距今${(effectiveTriggerTime - now) / 1000}s)")
                    scheduleAlarm(context, effectiveTriggerTime, defaultAmount)
                }

                PreferenceManager.setReminderEnabled(context, true)
            } catch (e: Exception) {
                Log.e(TAG, "调度间隔提醒失败", e)
            }
            }
        }
    }

    /**
     * 用户喝水打卡后重新调度提醒
     * 从打卡时刻 + 间隔分钟
     */
    fun scheduleAfterDrink(context: Context) {
        if (!isReminderEnabled(context)) {
            Log.i(TAG, "提醒总开关已关闭，跳过打卡后重新调度")
            return
        }
        val now = System.currentTimeMillis()
        Log.i(TAG, "▶ scheduleAfterDrink 被调用, now=${fmtTime(now)}")
        setLastDrinkTime(context, now)
        cancelSmallCycle(context)
        // 打卡后必须强制重算（忽略已有的旧计划时间），从打卡时刻 + 间隔
        scheduleNextReminder(context, forceReschedule = true)
    }

    /**
     * 记录间隔提醒触发时间，并启动小周期检查
     */
    fun onIntervalReminderTriggered(context: Context, suggestedAmount: Int = 200) {
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_INTERVAL_TRIGGER_TIME, now).apply()
        Log.i(TAG, "▶ onIntervalReminderTriggered, triggerTime=${fmtTime(now)}")

        // 连续提醒（小周期）开关：关闭时不再进入 5 分钟循环提醒
        if (!PreferenceManager.isSmallCycleEnabled(context)) {
            Log.i(TAG, "连续提醒开关已关闭，跳过小周期循环提醒")
            return
        }
        Log.i(TAG, "启动小周期")
        scheduleSmallCycle(context, suggestedAmount)
    }

    /**
     * 调度小周期检查闹钟（5分钟后）
     * 使用 setAlarmClock 确保最高优先级，防止被 OEM 系统丢弃
     */
    fun scheduleSmallCycle(context: Context, suggestedAmount: Int = 200) {
        if (!isReminderEnabled(context)) {
            Log.i(TAG, "提醒总开关已关闭，跳过调度小周期")
            return
        }
        if (!PreferenceManager.isSmallCycleEnabled(context)) {
            Log.i(TAG, "连续提醒开关已关闭，跳过调度小周期")
            return
        }
        scope.launch {
            mutex.withLock {
                try {
                    scheduleSmallCycleInternal(context, suggestedAmount)
                } catch (e: Exception) {
                    Log.e(TAG, "调度小周期失败", e)
                }
            }
        }
    }

    private fun scheduleSmallCycleInternal(context: Context, suggestedAmount: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("is_small_cycle_reminder", true)
            putExtra("suggested_amount", suggestedAmount)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SMALL_CYCLE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + SMALL_CYCLE_MINUTES * 60 * 1000L
        Log.i(TAG, "★ 调度小周期闹钟 → ${fmtTime(triggerTime)}，使用 setAlarmClock 确保可靠性")

        // 小周期闹钟直接使用 setAlarmClock，确保在 OEM 杀进程后仍能触发
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val showIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_ALARM_CLOCK - 1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, showIntent),
                pendingIntent
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * 取消小周期闹钟
     */
    fun cancelSmallCycle(context: Context) {
        Log.i(TAG, "▶ cancelSmallCycle 被调用")
        scope.launch {
            mutex.withLock {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_SMALL_CYCLE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                Log.i(TAG, "已取消小周期闹钟")
            }
        }
    }

    /**
     * 获取上次间隔提醒触发时间
     */
    fun getLastIntervalTriggerTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_INTERVAL_TRIGGER_TIME, 0L)
    }

    /**
     * 精确调度闹钟。
     * - Android 12+：统一使用 setAlarmClock。国产 ROM 对 setExactAndAllowWhileIdle
     *   常有 1 小时级别的批量延迟，setAlarmClock 会显示闹钟图标但触发最可靠。
     * - Android 12 以下：使用 setExactAndAllowWhileIdle，失败则回退到 set。
     */
    private fun scheduleExactAlarm(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent,
        context: Context
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "调度闹钟(setAlarmClock) → ${fmtTime(triggerTime)}")
            val showIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_ALARM_CLOCK,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, showIntent),
                pendingIntent
            )
        } else {
            Log.i(TAG, "调度闹钟(setExactAndAllowWhileIdle) → ${fmtTime(triggerTime)}")
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "setExact 失败，回退到 set", e)
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
    }

    private fun scheduleAlarm(context: Context, triggerTime: Long, defaultAmount: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("suggested_amount", defaultAmount)
            putExtra("is_interval_reminder", true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_INTERVAL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleExactAlarm(alarmManager, triggerTime, pendingIntent, context)
        // 记录本次大周期的计划触发时间，供兜底恢复（解锁/开机/Worker/onResume）保持原时间，避免被推迟
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_REMINDER_TIME, triggerTime)
            .apply()
    }

    fun cancelReminder(context: Context) {
        Log.i(TAG, "▶ cancelReminder 被调用")
        scope.launch {
            mutex.withLock {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_INTERVAL,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_NEXT_REMINDER_TIME)
                    .apply()
                Log.i(TAG, "已取消间隔提醒闹钟")
            }
        }
    }

    /**
     * 取消单个固定时间提醒的闹钟
     */
    fun cancelSingleFixedReminder(context: Context, reminderTimeId: Long) {
        Log.i(TAG, "▶ cancelSingleFixedReminder 被调用, id=$reminderTimeId")
        scope.launch {
            mutex.withLock {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reminderTimeId.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                Log.i(TAG, "已取消固定提醒闹钟: id=$reminderTimeId")
            }
        }
    }

    fun scheduleAllReminders(context: Context) {
        if (!isReminderEnabled(context)) {
            Log.i(TAG, "提醒总开关已关闭，跳过调度固定时间提醒")
            return
        }
        scope.launch {
            mutex.withLock {
            try {
                val db = (context.applicationContext as App).database
                val enabledTimes = db.reminderTimeDao().getEnabled()

                val typeId = PreferenceManager.getCurrentTypeId(context)
                val type = db.personTypeDao().getById(typeId)
                val defaultAmount = type?.defaultAmountMl ?: 200

                Log.i(TAG, "调度固定时间提醒，共 ${enabledTimes.size} 个启用项")

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                enabledTimes.forEach { reminderTime ->
                    val calendar = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, reminderTime.hour)
                        set(Calendar.MINUTE, reminderTime.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    if (calendar.timeInMillis <= System.currentTimeMillis()) {
                        calendar.add(Calendar.DAY_OF_YEAR, 1)
                    }

                    val triggerTime = calendar.timeInMillis
                    val amount = if (reminderTime.amountMl > 0) reminderTime.amountMl else defaultAmount

                    Log.i(TAG, "调度固定提醒: id=${reminderTime.id}, ${reminderTime.hour}:${"%02d".format(reminderTime.minute)} → ${fmtTime(triggerTime)}")

                    val intent = Intent(context, ReminderReceiver::class.java).apply {
                        putExtra("suggested_amount", amount)
                        putExtra("reminder_time_id", reminderTime.id)
                        putExtra("reminder_hour", reminderTime.hour)
                        putExtra("reminder_minute", reminderTime.minute)
                        putExtra("reminder_label", reminderTime.label)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        reminderTime.id.toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    scheduleExactAlarm(alarmManager, triggerTime, pendingIntent, context)
                }

                PreferenceManager.setReminderEnabled(context, true)
            } catch (e: Exception) {
                Log.e(TAG, "调度固定时间提醒失败", e)
            }
            }
        }
    }

    /**
     * 将单个固定时间点提醒重新调度到明天同一时间
     */
    fun scheduleFixedReminderToTomorrow(context: Context, reminderTimeId: Long, hour: Int, minute: Int) {
        if (!isReminderEnabled(context)) {
            Log.i(TAG, "提醒总开关已关闭，跳过调度明天固定提醒")
            return
        }
        scope.launch {
            mutex.withLock {
            try {
                val db = (context.applicationContext as App).database
                val reminderTime = db.reminderTimeDao().getById(reminderTimeId)
                val defaultAmount = reminderTime?.amountMl?.takeIf { it > 0 } ?: run {
                    val typeId = PreferenceManager.getCurrentTypeId(context)
                    val type = db.personTypeDao().getById(typeId)
                    type?.defaultAmountMl ?: 200
                }
                val label = reminderTime?.label ?: ""

                val calendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                Log.i(TAG, "调度固定提醒到明天: id=$reminderTimeId, $hour:${"%02d".format(minute)} → ${fmtTime(calendar.timeInMillis)}")

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("suggested_amount", defaultAmount)
                    putExtra("reminder_time_id", reminderTimeId)
                    putExtra("reminder_hour", hour)
                    putExtra("reminder_minute", minute)
                    putExtra("reminder_label", label)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reminderTimeId.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleExactAlarm(alarmManager, calendar.timeInMillis, pendingIntent, context)
            } catch (e: Exception) {
                Log.e(TAG, "调度明天固定提醒失败", e)
            }
            }
        }
    }

    fun cancelAllReminders(context: Context) {
        Log.i(TAG, "▶ cancelAllReminders 被调用")
        scope.launch {
            mutex.withLock {
                try {
                    val db = (context.applicationContext as App).database
                    val allTimes = db.reminderTimeDao().getEnabled()

                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                    // 取消所有固定时间提醒闹钟
                    allTimes.forEach { reminderTime ->
                        val intent = Intent(context, ReminderReceiver::class.java)
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            reminderTime.id.toInt(),
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        alarmManager.cancel(pendingIntent)
                    }

                    // 取消间隔提醒（大周期）闹钟
                    val intervalIntent = Intent(context, ReminderReceiver::class.java)
                    val intervalPendingIntent = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE_INTERVAL,
                        intervalIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(intervalPendingIntent)

                    // 取消小周期闹钟
                    val smallCycleIntent = Intent(context, ReminderReceiver::class.java)
                    val smallCyclePendingIntent = PendingIntent.getBroadcast(
                        context,
                        REQUEST_CODE_SMALL_CYCLE,
                        smallCycleIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    alarmManager.cancel(smallCyclePendingIntent)

                    // 清除大周期计划时间记录，避免兜底恢复时重新注册已取消的闹钟
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .remove(KEY_NEXT_REMINDER_TIME)
                        .apply()
                    Log.i(TAG, "已取消所有提醒闹钟，共 ${allTimes.size} 个固定时间")
                } catch (e: Exception) {
                    Log.e(TAG, "取消所有提醒失败", e)
                }
            }
        }
    }

    fun getLastDrinkTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_DRINK_TIME, 0L)
    }

    fun setLastDrinkTime(context: Context, time: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_DRINK_TIME, time).apply()
        Log.i(TAG, "setLastDrinkTime = ${fmtTime(time)}")
    }
}
