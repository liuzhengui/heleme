package com.zhengui.waterreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zhengui.waterreminder.service.AlarmCheckWorker
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "收到广播: ${intent.action}，准备恢复提醒")
                if (PreferenceManager.isReminderEnabled(context)) {
                    ReminderScheduler.scheduleNextReminder(context)
                    ReminderScheduler.scheduleAllReminders(context)
                    AlarmCheckWorker.enqueue(context)
                    Log.d(TAG, "已恢复提醒调度和兜底检查任务")
                }

            }
        }
    }
}
