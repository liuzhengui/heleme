package com.zhengui.waterreminder.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.ActivityMainBinding
import com.zhengui.waterreminder.notification.FullscreenReminderActivity
import com.zhengui.waterreminder.notification.NotificationHelper

import com.zhengui.waterreminder.service.AlarmCheckWorker
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager
import com.zhengui.waterreminder.ui.home.HomeFragment
import com.zhengui.waterreminder.ui.persontype.PersonTypeListFragment
import com.zhengui.waterreminder.ui.record.RecordListFragment
import com.zhengui.waterreminder.ui.remindertime.ReminderTimeFragment


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var showingHome = true
    private var pendingAutoStartSwitch: SwitchMaterial? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper(this).createChannel()
        setupNavigationDrawer()
        setupBottomNavigation()
        setupToolbarMenu()
        setupBackHandling()

        if (savedInstanceState == null) {
            showHome()
        } else {
            configureToolbarForHome()
        }
    }

    private fun setupBottomNavigation() {
        binding.navHome.setOnClickListener { showHome() }
        binding.navRecords.setOnClickListener { showSection("喝水记录", RecordListFragment()) }
        binding.navReminder.setOnClickListener { showSection("提醒时间", ReminderTimeFragment()) }
        binding.navTypes.setOnClickListener { showSection("人群类型", PersonTypeListFragment()) }
    }

    private fun setupToolbarMenu() {
        binding.btnToolbarAction.setOnClickListener {
            if (showingHome) {
                // 首页时弹出人群选择弹窗，而非跳转到全屏页面
                val homeFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? HomeFragment
                homeFragment?.showPersonTypeSelector()
            } else {
                showSection("人群类型", PersonTypeListFragment())
            }
        }
    }

    private val dailyQuotes = arrayOf(
        "每一天都是崭新的开始",
        "水是生命之源，记得喝水哦",
        "喝水的你，一定很美",
        "保持水分，保持活力",
        "一杯水，一份健康",
        "你今天喝水了吗？",
        "喝水是最简单的养生",
        "点滴之间，润物无声",
        "好好喝水，好好生活",
        "水润万物，也润你",
        "生活很忙，别忘了喝水",
        "一杯温水，温暖一天",
        "喝水打卡，健康加分",
        "每一天，从一杯水开始",
        "你是自己健康的第一责任人"
    )

    private fun setupNavigationDrawer() {
        // 每日随机一言
        val quote = dailyQuotes[(System.currentTimeMillis() % dailyQuotes.size).toInt()]
        binding.drawerContent.tvDailyQuote.text = quote

        binding.drawerContent.navHome.setOnClickListener {
            showHome()
            closeDrawer()
        }
        binding.drawerContent.navRecords.setOnClickListener {
            showSection("喝水记录", RecordListFragment())
            closeDrawer()
        }
        binding.drawerContent.navReminderTimes.setOnClickListener {
            showSection("提醒时间", ReminderTimeFragment())
            closeDrawer()
        }
        binding.drawerContent.navPersonTypes.setOnClickListener {
            showSection("人群类型", PersonTypeListFragment())
            closeDrawer()
        }
        binding.drawerContent.navAbout.setOnClickListener {
            closeDrawer()
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
        }
        binding.drawerContent.navPreview.setOnClickListener {
            closeDrawer()
            if (PreferenceManager.isFullscreenReminderEnabled(this@MainActivity)) {
                startActivity(Intent(this@MainActivity, FullscreenReminderActivity::class.java))
            } else {
                Toast.makeText(this@MainActivity, "全屏提醒已关闭，可在侧边栏开启", Toast.LENGTH_SHORT).show()
            }
        }

        setupSwitches()

        // 首次用户引导提示
        val tvSetupGuide = binding.drawerContent.tvSetupGuide
        if (!PreferenceManager.hasSeenSetupGuide(this) && !PreferenceManager.isReminderEnabled(this)) {
            tvSetupGuide.visibility = View.VISIBLE
        } else {
            tvSetupGuide.visibility = View.GONE
        }

        // 点击「自动启动」行弹窗说明
        binding.drawerContent.layoutAutoStart.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("自动启动")
                .setMessage("开启后，手机重启时应用可自动恢复喝水提醒调度。\n\n请前往 设置 → 应用 → 自启动，找到「喝了么」并允许。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun setupSwitches() {
        binding.drawerContent.drawerSwitchReminder.apply {
            isChecked = PreferenceManager.isReminderEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    checkNotificationPermissionAndStart()
                } else {
                    stopReminder()
                }
            }
        }

        binding.drawerContent.drawerSwitchAutoStart.apply {
            // 开关表示用户意愿，不再直接绑定系统权限状态
            isChecked = PreferenceManager.isAutoStartEnabled(this@MainActivity)
            pendingAutoStartSwitch = this
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    PreferenceManager.setAutoStartEnabled(this@MainActivity, true)
                    requestAutoStartPermissions(this)
                } else {
                    PreferenceManager.setAutoStartEnabled(this@MainActivity, false)
                    Toast.makeText(this@MainActivity, "已关闭自动启动", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.drawerContent.drawerSwitchFullscreen.apply {
            isChecked = PreferenceManager.isFullscreenReminderEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                PreferenceManager.setFullscreenReminderEnabled(this@MainActivity, isChecked)
                val msg = if (isChecked) "全屏提醒已开启" else "全屏提醒已关闭，仅保留通知栏提醒"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        binding.drawerContent.drawerSwitchEncouragement.apply {
            isChecked = PreferenceManager.isEncouragementEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                PreferenceManager.setEncouragementEnabled(this@MainActivity, isChecked)
                val msg = if (isChecked) "喝水鼓励已开启" else "喝水鼓励已关闭"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }
        }

        // 连续提醒（小周期循环提醒）开关：默认开启
        binding.drawerContent.drawerSwitchSmallCycle.apply {
            isChecked = PreferenceManager.isSmallCycleEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                PreferenceManager.setSmallCycleEnabled(this@MainActivity, isChecked)
                val msg = if (isChecked) "连续提醒已开启" else "连续提醒已关闭"
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                if (!isChecked) {
                    // 关闭时立即取消已有小周期闹钟，避免继续循环提醒
                    ReminderScheduler.cancelSmallCycle(this@MainActivity)
                }
            }
        }

        // 点击「连续提醒」行弹窗说明含义
        binding.drawerContent.layoutSmallCycle.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("连续提醒")
                .setMessage("大周期提醒后，如果一直没有喝水打卡，每隔 5 分钟会再次提醒，直到打卡喝水或当日结束。\n\n关闭后，仅保留按间隔的大周期提醒，不再循环提醒。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun checkNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        } else {
            startReminder()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startReminder()
            } else {
                Toast.makeText(this, "需要通知权限才能开启提醒", Toast.LENGTH_SHORT).show()
                binding.drawerContent.drawerSwitchReminder.isChecked = false
            }
        }
    }

    private fun startReminder() {
        PreferenceManager.setReminderEnabled(this, true)
        // 先取消所有残留闹钟，防止重复或冲突
        ReminderScheduler.cancelReminder(this)
        ReminderScheduler.cancelSmallCycle(this)
        ReminderScheduler.cancelAllReminders(this)
        // 然后重新调度
        ReminderScheduler.scheduleNextReminder(this)
        ReminderScheduler.scheduleAllReminders(this)
        // 注册 WorkManager 兜底检查任务
        AlarmCheckWorker.enqueue(this)
        Toast.makeText(this, "喝水提醒已开启", Toast.LENGTH_SHORT).show()

        // 标记引导已看过
        PreferenceManager.setHasSeenSetupGuide(this, true)
        binding.drawerContent.tvSetupGuide.visibility = View.GONE
    }

    private fun stopReminder() {
        PreferenceManager.setReminderEnabled(this, false)
        ReminderScheduler.cancelReminder(this)
        ReminderScheduler.cancelSmallCycle(this)
        ReminderScheduler.cancelAllReminders(this)
        AlarmCheckWorker.cancel(this)
        Toast.makeText(this, "喝水提醒已关闭", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("BatteryLife")
    private fun requestAutoStartPermissions(autoStartSwitch: SwitchMaterial) {
        // 统一通过 enableAutoStart 处理：有权限则提示已开启，无权限则弹出引导弹窗
        enableAutoStart(autoStartSwitch)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_BATTERY_OPTIMIZATION) {
            val autoStartSwitch = pendingAutoStartSwitch
            if (autoStartSwitch != null) {
                // 检查用户是否真的授权了电池优化
                if (isIgnoringBatteryOptimizations()) {
                    enableAutoStart(autoStartSwitch)
                } else {
                    // 用户拒绝了或未授权，把开关设回 false
                    autoStartSwitch.isChecked = false
                    PreferenceManager.setAutoStartEnabled(this, false)
                    Toast.makeText(this, "需要允许电池优化才能开启自动启动", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // 从系统设置返回或应用从后台恢复时，若提醒已开启则重新调度闹钟，
        // 防止 OEM 系统清理导致闹钟被取消后无法恢复。
        if (PreferenceManager.isReminderEnabled(this)) {
            ReminderScheduler.scheduleNextReminder(this)
            ReminderScheduler.scheduleAllReminders(this)
            AlarmCheckWorker.enqueue(this)
        }
    }

    private fun enableAutoStart(autoStartSwitch: SwitchMaterial) {
        // 用户已表达开启意愿，直接显示引导弹窗让用户去系统设置手动允许
        showAutoStartGuideDialog(autoStartSwitch)
    }

    private fun showAutoStartGuideDialog(autoStartSwitch: SwitchMaterial) {
        val message = """
            为了确保喝水提醒能正常工作，请在系统设置中允许本应用自启动。

            路径：设置 → 应用 → 自启动 → 找到「喝了么」→ 允许

            注意：Android 系统限制，应用无法自动检测你是否已开启，请在设置中确认后返回即可。
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("开启自启动权限")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                openAutoStartSettings()
            }
            .setNegativeButton("取消") { _, _ ->
                // 用户取消，恢复开关为关闭状态
                autoStartSwitch.isChecked = false
                PreferenceManager.setAutoStartEnabled(this, false)
                Toast.makeText(this, "未开启自动启动，提醒可能无法稳定触发", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 直接跳转到系统设置主界面，由用户自行前往「设置 → 应用 → 自启动」允许本应用。
     */
    private fun openAutoStartSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开设置，请手动进入系统设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    closeDrawer()
                } else if (!showingHome) {
                    showHome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun showHome() {
        showingHome = true
        updateBottomNavSelection(R.id.navHome)
        switchFragment(HomeFragment())
        configureToolbarForHome()
    }

    private fun showSection(title: String, fragment: Fragment) {
        showingHome = false
        switchFragment(fragment)
        updateBottomNavSelection(null)
        configureToolbarForChild(title)
        closeDrawer()
    }

    private fun updateBottomNavSelection(selectedId: Int?) {
        val items = listOf(R.id.navHome, R.id.navRecords, R.id.navReminder, R.id.navTypes)
        items.forEach { id ->
            val layout = findViewById<android.widget.LinearLayout>(id) ?: return@forEach
            val isSelected = id == selectedId
            val color = if (isSelected) Color.parseColor("#111111") else Color.parseColor("#6B6B6B")
            (layout.getChildAt(0) as? android.widget.ImageView)?.drawable?.setTint(color)
            (layout.getChildAt(1) as? android.widget.TextView)?.setTextColor(color)
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun configureToolbarForHome() {
        binding.tvToolbarTitle.text = getString(R.string.app_name)
        binding.btnToolbarNav.visibility = View.VISIBLE
        binding.btnToolbarNav.setImageResource(R.drawable.ic_menu)
        binding.btnToolbarNav.contentDescription = getString(R.string.open_drawer)
        binding.btnToolbarNav.setOnClickListener { openDrawer() }
        binding.btnToolbarAction.visibility = View.VISIBLE
    }

    private fun configureToolbarForChild(title: String) {
        binding.tvToolbarTitle.text = title
        binding.btnToolbarNav.visibility = View.GONE
        binding.btnToolbarAction.visibility = View.GONE
    }

    fun openDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    companion object {
        private const val REQUEST_CODE_BATTERY_OPTIMIZATION = 200
    }
}
