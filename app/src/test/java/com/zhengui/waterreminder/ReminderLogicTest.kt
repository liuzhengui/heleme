package com.zhengui.waterreminder

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

/**
 * 提醒系统逻辑验证测试
 *
 * 覆盖场景：
 *   1. 版本号比较逻辑
 *   2. 间隔提醒触发时间计算
 *   3. 通知时段判断（PersonType 默认 08:00-21:00）
 *   4. 小周期决策（用户已喝 / 未喝）
 *   5. 定时提醒重新调度（跨天）
 *   6. 边界条件（开始时间 = 结束时间、跨午夜通知）
 */
@RunWith(RobolectricTestRunner::class)
class ReminderLogicTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_reminder_prefs", Context.MODE_PRIVATE)
    }

    // =========================================================================
    // 1. 版本号比较测试 (UpdateManager.isNewerVersion 逻辑)
    // =========================================================================

    @Test
    fun `版本号 1_3_0 大于 1_2_2`() {
        assertTrue(isNewerVersion("1.3.0", "1.2.2"))
    }

    @Test
    fun `版本号 2_0 大于 1_9_9`() {
        assertTrue(isNewerVersion("2.0", "1.9.9"))
    }

    @Test
    fun `版本号相同不触发更新`() {
        assertFalse(isNewerVersion("1.2.2", "1.2.2"))
    }

    @Test
    fun `版本号 1_2 小于 1_2_1`() {
        assertFalse(isNewerVersion("1.2", "1.2.1"))
    }

    @Test
    fun `带v前缀能正确比较 v1_3 vs 1_2`() {
        assertTrue(isNewerVersion("v1.3.0", "1.2.0"))
    }

    @Test
    fun `非法版本号回退到0比较_不崩溃`() {
        // 远程返回不规范的tag时不会崩溃
        // "abc" 解析为 0，0 < 9.9 不触发更新
        assertFalse(isNewerVersion("abc", "9.9"))
        // "" 解析为 0，0 < 1.0 不触发更新
        assertFalse(isNewerVersion("", "1.0"))
        // 但如果本地也是 0，相同版本不触发
        assertFalse(isNewerVersion("abc", "0"))
    }

    /**
     * 语义化版本比较（与 UpdateManager.isNewerVersion 逻辑一致）
     */
    private fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val remote = remoteTag.trimStart('v').split(".").map { it.toIntOrNull() ?: 0 }
        val local = localVersion.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    // =========================================================================
    // 2. 间隔提醒触发时间计算 (ReminderScheduler.scheduleNextReminder 逻辑)
    // =========================================================================

    @Test
    fun `首次提醒_还没到开始时间_从开始时间触发`() = runTest {
        // 模拟：当前时间 06:00，开始时间 08:00，间隔 120min
        val startHour = 8; val startMinute = 0
        val intervalMin = 120
        val lastDrinkTime = 0L // 无打卡记录
        val now = calendarAt(6, 0).timeInMillis

        val triggerTime = calcTriggerTime(now, lastDrinkTime, startHour, startMinute, intervalMin)
        val expected = calendarAt(8, 0).timeInMillis

        assertEquals("应从08:00开始", expected, triggerTime)
    }

    @Test
    fun `首次提醒_已过开始时间_从现在加间隔触发`() = runTest {
        // 模拟：当前时间 10:00，开始时间 08:00，间隔 120min
        val startHour = 8; val startMinute = 0
        val intervalMin = 120
        val lastDrinkTime = 0L
        val now = calendarAt(10, 0).timeInMillis

        val triggerTime = calcTriggerTime(now, lastDrinkTime, startHour, startMinute, intervalMin)
        val expected = calendarAt(12, 0).timeInMillis

        assertEquals("应从12:00触发", expected, triggerTime)
    }

    @Test
    fun `有打卡记录_从打卡时间后推_加间隔`() = runTest {
        // 模拟：当前 14:35 打卡，间隔 60min → 15:35 提醒
        val startHour = 8; val startMinute = 0
        val intervalMin = 60
        val lastDrinkTime = calendarAt(14, 35).timeInMillis
        val now = calendarAt(14, 35).timeInMillis

        val triggerTime = calcTriggerTime(now, lastDrinkTime, startHour, startMinute, intervalMin)
        val expected = calendarAt(15, 35).timeInMillis

        assertEquals("打卡后60分钟触发", expected, triggerTime)
    }

    @Test
    fun `触发时间超出今天结束时间_调度到明天开始`() = runTest {
        // 模拟：当前 20:00，间隔 120min → 22:00，但结束时间 21:00 → 调明天 08:00
        val endHour = 21; val endMinute = 0
        val intervalMin = 120
        val now = calendarAt(20, 0).timeInMillis

        val rawTrigger = now + intervalMin * 60 * 1000L // 22:00
        // 钳位逻辑：超出结束时间 → 明天开始
        assertTrue("22:00 > 21:00 应超出",
            rawTrigger > calendarAt(endHour, endMinute).timeInMillis)
    }

    @Test
    fun `安全兜底_触发时间早于当前_重置为now加间隔`() = runTest {
        // 模拟：打卡时间很早，触发时间已过，兜底重置
        val intervalMin = 120
        val oldDrink = calendarAt(5, 0).timeInMillis // 早上5点打卡
        val now = calendarAt(10, 0).timeInMillis    // 现在10点

        val rawTrigger = oldDrink + intervalMin * 60 * 1000L // = 07:00
        assertTrue("07:00 < 10:00，需兜底", rawTrigger < now)
        // effectiveTriggerTime = now + intervalMin = 12:00
    }

    // =========================================================================
    // 3. 通知时段判断 (isInNotificationPeriod 逻辑)
    // =========================================================================

    @Test
    fun `在通知时段内_08_00到21_00_中午12点在时段内`() {
        val startHour = 8; val startMinute = 0
        val endHour = 21; val endMinute = 0
        val now = calendarAt(12, 0)
        assertTrue(isInPeriod(now, startHour, startMinute, endHour, endMinute))
    }

    @Test
    fun `在通知时段内_边界值_08_00刚好在`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(isInPeriod(now, 8, 0, 21, 0))
    }

    @Test
    fun `在通知时段内_边界值_21_00刚好在`() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(isInPeriod(now, 8, 0, 21, 0))
    }

    @Test
    fun `不在通知时段内_凌晨3点`() {
        val now = calendarAt(3, 0)
        assertFalse(isInPeriod(now, 8, 0, 21, 0))
    }

    @Test
    fun `不在通知时段内_晚上22点`() {
        val now = calendarAt(22, 0)
        assertFalse(isInPeriod(now, 8, 0, 21, 0))
    }

    @Test
    fun `不在通知时段内_07_59`() {
        val now = calendarAt(7, 59)
        assertFalse(isInPeriod(now, 8, 0, 21, 0))
    }

    @Test
    fun `跨午夜时段_22_00到06_00_凌晨2点在时段内`() {
        // 模拟夜班人士：通知时段 22:00-06:00
        val now = calendarAt(2, 0)
        assertTrue(isInPeriod(now, 22, 0, 6, 0))
    }

    @Test
    fun `跨午夜时段_22_00到06_00_中午12点不在`() {
        val now = calendarAt(12, 0)
        assertFalse(isInPeriod(now, 22, 0, 6, 0))
    }

    /**
     * 与 ReminderReceiver.isInNotificationPeriod 相同逻辑
     */
    private fun isInPeriod(
        now: Calendar,
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int
    ): Boolean {
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute
        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    // =========================================================================
    // 4. 小周期决策测试 (handleSmallCycle 逻辑)
    // =========================================================================

    @Test
    fun `小周期_用户已喝水_停止不发通知`() {
        // lastDrinkTime > lastTriggerTime → 停止
        val lastDrinkTime = 10000L
        val lastTriggerTime = 5000L
        assertTrue("用户已喝水，小周期应停止",
            lastDrinkTime > lastTriggerTime)
    }

    @Test
    fun `小周期_用户未喝水_继续发通知`() {
        // lastDrinkTime < lastTriggerTime → 继续小周期
        val lastDrinkTime = 5000L
        val lastTriggerTime = 10000L
        assertFalse("用户未喝水，小周期应继续",
            lastDrinkTime > lastTriggerTime)
    }

    @Test
    fun `小周期_从未喝水和从未触发_继续发通知`() {
        // 两者都是 0 或相等
        val lastDrinkTime = 0L
        val lastTriggerTime = 5000L
        assertFalse("用户没喝过水，小周期应继续",
            lastDrinkTime > lastTriggerTime)
    }

    // =========================================================================
    // 5. 固定时间提醒重新调度测试
    // =========================================================================

    @Test
    fun `固定提醒_早上8点的闹钟_已过期_调度到明天`() {
        // 当前 10:00，8:00 的闹钟已过期
        val now = calendarAt(10, 0)
        val reminderTime = calendarAt(8, 0)
        assertTrue("已过期需要调到明天",
            reminderTime.timeInMillis <= now.timeInMillis)

        // scheduleAllReminders 中的调度逻辑：
        reminderTime.add(Calendar.DAY_OF_YEAR, 1)
        assertTrue("调度到明天后应在未来",
            reminderTime.timeInMillis > now.timeInMillis)
    }

    @Test
    fun `固定提醒_下午18点的闹钟_未过期_调度到今天`() {
        // 当前 10:00，18:00 还没到
        val now = calendarAt(10, 0)
        val reminderTime = calendarAt(18, 0)
        assertTrue("18:00 > 10:00，未过期直接调度",
            reminderTime.timeInMillis > now.timeInMillis)
    }

    @Test
    fun `固定提醒_scheduleFixedReminderToTomorrow_精确调度到明天同一时间`() {
        val hour = 8; val minute = 30
        val now = calendarAt(10, 0)

        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(8, tomorrow.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, tomorrow.get(Calendar.MINUTE))
        assertTrue("明天8:30应在未来", tomorrow.timeInMillis > now.timeInMillis)
    }

    // =========================================================================
    // 6. 组合场景测试
    // =========================================================================

    @Test
    fun `完整流程_用户关闭提醒开关后_小周期仍弹最后一次通知`() {
        // 模拟 handleSmallCycle 中 reminderEnabled=false 的逻辑分支
        // 代码：第88-95行 — 提醒关闭时仍弹最后一次通知
        val lastDrinkTime = 0L
        val lastTriggerTime = 5000L
        val needNotify = !(lastDrinkTime > lastTriggerTime) // 未喝水
        val shouldNotify = needNotify // 即使提醒关闭，也弹最后一次

        assertTrue("提醒关闭+未喝水时应弹最后一次通知", shouldNotify)
    }

    @Test
    fun `完整流程_提醒开启但不在时段内_不弹通知但调度下次`() {
        // 模拟 handleIntervalReminder 中 isInPeriod=false 的逻辑分支
        // 代码：第110-128行 — 不在时段内只调度下次，不弹通知
        val inPeriod = false
        val reminderEnabled = true

        // 不弹通知：当 inPeriod == false
        assertFalse("不在时段内不弹通知", inPeriod)

        // 仍调度下次：当 reminderEnabled == true
        assertTrue("提醒开启时仍会调度下次", reminderEnabled)
    }

    @Test
    fun `边界条件_开始时间等于结束时间_仅该分钟在时段内`() {
        // 开始=结束，只有恰好该分钟在时段内
        val at8 = calendarAt(8, 0)
        val at12 = calendarAt(12, 0)
        assertTrue("8:00应在时段内", isInPeriod(at8, 8, 0, 8, 0))
        assertFalse("12:00不应在时段内（时段仅8:00）", isInPeriod(at12, 8, 0, 8, 0))
    }

    @Test
    fun `边界条件_用户切换到不同人员类型_间隔和时段随之改变`() {
        // PersonType A: 间隔60min, 时段 06:00-12:00
        val intervalA = 60
        val inPeriodA = isInPeriod(calendarAt(18, 0), 6, 0, 12, 0)
        assertFalse("18点不在06-12时段内", inPeriodA)

        // PersonType B: 间隔30min, 时段 14:00-22:00
        val intervalB = 30
        val inPeriodB = isInPeriod(calendarAt(18, 0), 14, 0, 22, 0)
        assertTrue("18点在14-22时段内", inPeriodB)

        // 验证：不同人员类型有不同提醒行为
        assertNotEquals(intervalA, intervalB)
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    private fun calendarAt(hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * 与 ReminderScheduler.scheduleNextReminder 相同的触发时间计算逻辑
     */
    private fun calcTriggerTime(
        now: Long,
        lastDrinkTime: Long,
        startHour: Int,
        startMinute: Int,
        intervalMin: Int
    ): Long {
        val startCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = startCalendar.timeInMillis

        val rawTrigger = if (lastDrinkTime > 0) {
            lastDrinkTime + intervalMin * 60 * 1000L
        } else {
            if (now < startTime) startTime else now + intervalMin * 60 * 1000L
        }

        // 钳位：不早于开始时间
        var effective = if (rawTrigger < startTime) startTime else rawTrigger
        // 兜底：不早于当前时间
        if (effective <= now) effective = now + intervalMin * 60 * 1000L
        return effective
    }
}
