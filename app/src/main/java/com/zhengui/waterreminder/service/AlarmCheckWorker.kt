package com.zhengui.waterreminder.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zhengui.waterreminder.util.PreferenceManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager 周期性 Worker：在应用存活时检查并重新调度提醒闹钟。
 *
 * 国产 ROM（ColorOS/OPPO/一加/小米等）在用户划掉应用或系统清理后，
 * 常常会取消 AlarmManager 闹钟。本 Worker 作为兜底手段，在应用仍存活时
 * 周期性确保闹钟已正确注册。
 */
class AlarmCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AlarmCheckWorker"
        private const val WORK_NAME = "alarm_check_work"

        /**
         * 注册周期性检查任务（15 分钟一次）。
         * 已注册时保持现有任务，避免重复。
         */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlarmCheckWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "已注册周期性闹钟检查任务")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "已取消周期性闹钟检查任务")
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!PreferenceManager.isReminderEnabled(context)) {
            Log.d(TAG, "喝水提醒总开关已关闭，跳过检查")
            return Result.success()
        }

        Log.d(TAG, "开始检查并重新调度闹钟")
        return try {
            ReminderScheduler.scheduleNextReminder(context)
            ReminderScheduler.scheduleAllReminders(context)
            Log.d(TAG, "闹钟检查完成，已重新调度")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "闹钟检查失败", e)
            Result.retry()
        }
    }
}
