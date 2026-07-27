package com.zhengui.waterreminder.notification

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.ActivityFullscreenReminderBinding
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FullscreenReminderActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FullscreenReminder"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"
        const val EXTRA_AMOUNT = "extra_amount"
        const val TYPE_INTERVAL = "interval"
        const val TYPE_SMALL_CYCLE = "small_cycle"
        const val TYPE_FIXED = "fixed"
    }

    private val random = java.util.Random()
    private lateinit var binding: ActivityFullscreenReminderBinding
    private val cheerGifResIds = intArrayOf(
        R.raw.cheer_gif_1,
        R.raw.cheer_gif_2,
        R.raw.cheer_gif_3,
        R.raw.cheer_gif_4,
        R.raw.cheer_gif_5
    )

    private val lockScreenTitles = arrayOf(
        "该喝水了",
        "饮水时间到",
        "休息一下，喝杯水",
        "补充水分",
        "保持活力",
        "该补水了",
        "喝杯水再继续",
        "记得喝水"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PreferenceManager.isFullscreenReminderEnabled(this)) {
            finish()
            return
        }

        turnOnScreen()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityFullscreenReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gifResId = cheerGifResIds[random.nextInt(cheerGifResIds.size)]
        Glide.with(this)
            .asGif()
            .load(gifResId)
            .into(binding.ivCheerGif)

        val amount = intent.getIntExtra(EXTRA_AMOUNT, 200)
        val title = lockScreenTitles[random.nextInt(lockScreenTitles.size)]

        binding.tvReminderTitle.text = title
        binding.tvReminderAmount.text = "建议饮用 ${amount}ml"

        binding.btnDrinkNow.setOnClickListener {
            Log.i(TAG, "▶ 用户点击「喝了」按钮")
            CoroutineScope(Dispatchers.IO).launch {
                val typeId = PreferenceManager.getCurrentTypeId(this@FullscreenReminderActivity)
                val db = (application as com.zhengui.waterreminder.App).database
                val type = db.personTypeDao().getById(typeId)
                val drinkAmount = type?.defaultAmountMl ?: amount
                val drinkTime = System.currentTimeMillis()
                db.waterRecordDao().insert(
                    com.zhengui.waterreminder.data.entity.WaterRecord(
                        drinkTime = drinkTime,
                        amountMl = drinkAmount,
                        personTypeId = typeId
                    )
                )
                Log.i(TAG, "已插入饮水记录: ${drinkAmount}ml")

                // 立即同步设置最后饮水时间并取消小周期闹钟，防止小周期在设置完成前触发
                ReminderScheduler.setLastDrinkTime(this@FullscreenReminderActivity, drinkTime)
                ReminderScheduler.cancelSmallCycle(this@FullscreenReminderActivity)

                // 检查是否达标
                val todayStart = getTodayStartMs()
                val dailyTotal = db.waterRecordDao().getDailyTotal(todayStart, System.currentTimeMillis()) ?: 0
                val goalMl = type?.dailyGoalMl ?: 2000
                val didReachGoal = dailyTotal >= goalMl
                Log.i(TAG, "达标检查: dailyTotal=${dailyTotal}ml, goal=${goalMl}ml, didReachGoal=$didReachGoal")

                runOnUiThread {
                    if (didReachGoal) {
                        // 达标后仍需调度下次提醒（明天开始时间），否则闹钟链断裂
                        if (PreferenceManager.isReminderEnabled(this@FullscreenReminderActivity)) {
                            Log.i(TAG, "达标但仍调度下次提醒")
                            CoroutineScope(Dispatchers.IO).launch {
                                ReminderScheduler.scheduleNextReminder(this@FullscreenReminderActivity)
                            }
                        }
                        showGoalCelebration()
                    } else {
                        if (PreferenceManager.isReminderEnabled(this@FullscreenReminderActivity)) {
                            Log.i(TAG, "未达标, 调度下次提醒")
                            CoroutineScope(Dispatchers.IO).launch {
                                ReminderScheduler.scheduleNextReminder(this@FullscreenReminderActivity)
                            }
                        }
                        finish()
                    }
                }

            }
        }

        binding.btnDismiss.setOnClickListener {
            Log.i(TAG, "▶ 用户点击「稍后」按钮, 不调度新提醒")
            finish()
        }
    }

    private fun showGoalCelebration() {
        binding.tvGoalCheer.text = "今日目标已达成"
        binding.tvGoalCheer.visibility = android.view.View.VISIBLE
        binding.tvReminderTitle.text = "目标达成"
        binding.tvReminderAmount.text = "今日饮水量已达到设定目标"
        binding.btnDrinkNow.text = "完成"
        binding.btnDismiss.text = "继续记录"

        binding.btnDrinkNow.setOnClickListener {
            finish()
        }
        binding.btnDismiss.setOnClickListener {
            finish()
        }
    }

    private fun getTodayStartMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    override fun onDestroy() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(NotificationHelper.REMINDER_NOTIFICATION_ID)
        super.onDestroy()
    }

    private fun turnOnScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WaterReminder:FullscreenWakeLock"
        )
        wakeLock.acquire(3000)
    }
}
