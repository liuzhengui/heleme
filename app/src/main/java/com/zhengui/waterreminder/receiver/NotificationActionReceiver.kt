package com.zhengui.waterreminder.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.data.entity.WaterRecord
import com.zhengui.waterreminder.notification.NotificationHelper
import com.zhengui.waterreminder.util.PreferenceManager
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotifActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val drinkAmount = intent.getIntExtra("drink_amount", 200)
        val now = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        Log.i(TAG, "========== onReceive at $now, drinkAmount=${drinkAmount}ml ==========")

        // 使用 goAsync 保证协程在 onReceive 返回后仍有机会执行完毕，
        // 避免进程被系统回收时打卡记录丢失、闹钟调度中断
        val pendingResult = goAsync()

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val currentTypeId = PreferenceManager.getCurrentTypeId(context)
                val db = (context.applicationContext as App).database
                val drinkTime = System.currentTimeMillis()
                db.waterRecordDao().insert(
                    WaterRecord(
                        drinkTime = drinkTime,
                        amountMl = drinkAmount,
                        personTypeId = currentTypeId
                    )
                )
                Log.i(TAG, "已插入饮水记录: ${drinkAmount}ml at ${SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(drinkTime))}")

                // 立即同步更新最后饮水时间并取消小周期闹钟，防止小周期继续触发
                ReminderScheduler.setLastDrinkTime(context, drinkTime)
                ReminderScheduler.cancelSmallCycle(context)

                // 重新调度下一次主提醒
                if (PreferenceManager.isReminderEnabled(context)) {
                    Log.i(TAG, "提醒开关开启, 调用 scheduleNextReminder")
                    ReminderScheduler.scheduleNextReminder(context, forceReschedule = true)
                } else {
                    Log.i(TAG, "提醒开关关闭, 跳过 scheduleNextReminder")
                }

                withContext(Dispatchers.Main) {
                    WidgetUpdateHelper.updateAllWidgets(context)
                    Toast.makeText(context, "已记录 ${drinkAmount}ml", Toast.LENGTH_SHORT).show()
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NotificationHelper.REMINDER_NOTIFICATION_ID)
                    Log.i(TAG, "已取消通知栏提醒通知")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
