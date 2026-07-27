package com.zhengui.waterreminder.ui.persontype

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.TimePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.data.entity.PersonType
import com.zhengui.waterreminder.databinding.ActivityPersonTypeEditBinding
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager
import kotlinx.coroutines.launch

class PersonTypeEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonTypeEditBinding
    private lateinit var viewModel: PersonTypeViewModel
    private var editTypeId: Long? = null
    private var existingType: PersonType? = null
    private var startHour = 8
    private var startMinute = 0
    private var endHour = 21
    private var endMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonTypeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)

        viewModel = ViewModelProvider(this)[PersonTypeViewModel::class.java]
        binding.btnBack.setOnClickListener { finish() }

        editTypeId = intent.getLongExtra("person_type_id", -1L).takeIf { it > 0 }
        binding.tvTitle.text = if (editTypeId == null) "新建人群类型" else "编辑人群类型"
        editTypeId?.let { id -> loadExistingType(id) }

        binding.btnPickStart.setOnClickListener { showTimePicker(isStart = true) }
        binding.btnPickEnd.setOnClickListener { showTimePicker(isStart = false) }
        binding.btnSave.setOnClickListener { saveType() }
    }

    private fun loadExistingType(id: Long) {
        lifecycleScope.launch {
            val type = (application as App).database.personTypeDao().getById(id)
            type?.let {
                existingType = it
                binding.etName.setText(it.name)
                binding.etDailyGoal.setText(it.dailyGoalMl.toString())
                binding.etDefaultAmount.setText(it.defaultAmountMl.toString())
                binding.etInterval.setText(it.reminderIntervalMin.toString())
                startHour = it.notificationStartHour
                startMinute = it.notificationStartMinute
                endHour = it.notificationEndHour
                endMinute = it.notificationEndMinute
                updateStartTimeText()
                updateEndTimeText()
                if (it.isPreset) binding.etName.isEnabled = false
            }
        }
    }

    private fun saveType() {
        val name = binding.etName.text.toString().trim()
        val dailyGoal = binding.etDailyGoal.text.toString().toIntOrNull()
        val defaultAmount = binding.etDefaultAmount.text.toString().toIntOrNull()
        val interval = binding.etInterval.text.toString().toIntOrNull()

        if (name.isBlank() || dailyGoal == null || defaultAmount == null || interval == null) {
            Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
            return
        }

        val current = existingType
        if (current != null) {
            viewModel.update(
                current.copy(
                    name = name,
                    dailyGoalMl = dailyGoal,
                    defaultAmountMl = defaultAmount,
                    reminderIntervalMin = interval,
                    notificationStartHour = startHour,
                    notificationStartMinute = startMinute,
                    notificationEndHour = endHour,
                    notificationEndMinute = endMinute
                )
            )
            // 如果编辑的是当前激活的类型且提醒已开启，从当前时间重新调度
            rescheduleIfActive(current.id)
        } else {
            viewModel.insert(
                PersonType(
                    name = name,
                    dailyGoalMl = dailyGoal,
                    defaultAmountMl = defaultAmount,
                    reminderIntervalMin = interval,
                    notificationStartHour = startHour,
                    notificationStartMinute = startMinute,
                    notificationEndHour = endHour,
                    notificationEndMinute = endMinute,
                    isPreset = false
                )
            )
        }
        finish()
    }

    /**
     * 如果编辑的是当前激活类型且提醒已开启，清除上次喝水时间并从现在起按新间隔调度
     */
    private fun rescheduleIfActive(typeId: Long) {
        val activeId = PreferenceManager.getCurrentTypeId(this)
        if (typeId != activeId) return
        if (!PreferenceManager.isReminderEnabled(this)) return

        // 清除上次喝水时间，让 scheduleNextReminder 从当前时间 + 新间隔计算
        ReminderScheduler.setLastDrinkTime(this, 0L)
        ReminderScheduler.cancelReminder(this)
        ReminderScheduler.cancelSmallCycle(this)
        ReminderScheduler.scheduleNextReminder(this)
    }

    private fun showTimePicker(isStart: Boolean) {
        val hour = if (isStart) startHour else endHour
        val minute = if (isStart) startMinute else endMinute
        TimePickerDialog(this, { _: TimePicker, h: Int, m: Int ->
            if (isStart) {
                startHour = h
                startMinute = m
                updateStartTimeText()
            } else {
                endHour = h
                endMinute = m
                updateEndTimeText()
            }
        }, hour, minute, true).show()
    }

    private fun updateStartTimeText() {
        binding.tvStartTime.text = String.format("%02d:%02d", startHour, startMinute)
    }

    private fun updateEndTimeText() {
        binding.tvEndTime.text = String.format("%02d:%02d", endHour, endMinute)
    }
}
