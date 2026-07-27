package com.zhengui.waterreminder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zhengui.waterreminder.service.AlarmCheckWorker
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager

/**
 * 监听屏幕解锁事件，在解锁时重新调度提醒闹钟。
 *
 * 国产 ROM 在用户锁屏期间或划掉应用后，可能会清理应用进程和闹钟。
 * 用户每次解锁手机是一个自然的恢复点，此时尝试重新调度闹钟，
 * 可以在不打扰用户的情况下提高提醒到达率。
 */
class ScreenUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenUnlockReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        if (!PreferenceManager.isReminderEnabled(context)) {
            Log.d(TAG, "提醒总开关已关闭，跳过重新调度")
            return
        }

        Log.d(TAG, "屏幕解锁，检查并重新调度提醒")
        ReminderScheduler.scheduleNextReminder(context)
        ReminderScheduler.scheduleAllReminders(context)
        AlarmCheckWorker.enqueue(context)
    }
}
