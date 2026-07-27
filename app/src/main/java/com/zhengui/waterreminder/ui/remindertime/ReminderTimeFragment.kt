package com.zhengui.waterreminder.ui.remindertime

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.data.entity.ReminderTimeEntity
import com.zhengui.waterreminder.databinding.DialogAddReminderBinding
import com.zhengui.waterreminder.databinding.FragmentReminderTimeBinding
import com.zhengui.waterreminder.service.ReminderScheduler
import com.zhengui.waterreminder.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ReminderTimeFragment : Fragment() {

    private var _binding: FragmentReminderTimeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ReminderTimeAdapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReminderTimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReminderTimeAdapter(
            onToggle = { time, enabled -> toggleReminder(time, enabled) },
            onDelete = { time -> confirmDelete(time) },
            onEdit = { time -> showEditReminderDialog(time) }
        )

        binding.recyclerReminderTimes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReminderTimes.adapter = adapter
        setupSwipeToDelete()

        binding.fabAdd.setOnClickListener { showAddReminderDialog() }
        loadReminderTimes()
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            private val deletePaint = android.graphics.Paint().apply {
                color = 0xFFEF4444.toInt()
                isAntiAlias = true
            }
            private val textPaint = android.graphics.Paint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 36f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    confirmDelete(adapter.getItem(position))
                }
            }

            override fun onChildDraw(c: android.graphics.Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dx: Float, dy: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = vh.itemView
                if (dx < 0) {
                    val bg = android.graphics.RectF(itemView.width + dx, itemView.top.toFloat(), itemView.width.toFloat(), itemView.bottom.toFloat())
                    c.drawRect(bg, deletePaint)
                    c.drawText("删除", bg.centerX(), bg.centerY() + textPaint.textSize / 3, textPaint)
                }
                super.onChildDraw(c, rv, vh, dx, dy, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerReminderTimes)
    }

    private fun loadReminderTimes() {
        scope.launch {
            val dao = (requireActivity().application as App).database.reminderTimeDao()
            dao.getAll().collect { list ->
                withContext(Dispatchers.Main) {
                    if (_binding != null) {
                        adapter.submitList(list)
                        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        val showHint = list.isNotEmpty() &&
                                !PreferenceManager.hasTriedSwipeDeleteReminder(requireContext())
                        binding.tvSwipeHint.visibility = if (showHint) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun showAddReminderDialog() {
        val calendar = Calendar.getInstance()
        val dialogBinding = DialogAddReminderBinding.inflate(LayoutInflater.from(requireContext()), null, false)

        dialogBinding.timePicker.setIs24HourView(true)
        dialogBinding.timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
        dialogBinding.timePicker.minute = calendar.get(Calendar.MINUTE)

        AlertDialog.Builder(requireContext())
            .setTitle("添加提醒时间")
            .setView(dialogBinding.root)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val hour = dialogBinding.timePicker.hour
                val minute = dialogBinding.timePicker.minute
                val amountStr = dialogBinding.etAmount.text.toString().trim()
                val amountMl = amountStr.toIntOrNull()?.takeIf { it > 0 } ?: 200
                val label = dialogBinding.etLabel.text.toString().trim()
                addReminderTime(hour, minute, amountMl, label)
            }
            .show()
    }

    private fun showEditReminderDialog(time: ReminderTimeEntity) {
        val dialogBinding = DialogAddReminderBinding.inflate(LayoutInflater.from(requireContext()), null, false)

        dialogBinding.timePicker.setIs24HourView(true)
        dialogBinding.timePicker.hour = time.hour
        dialogBinding.timePicker.minute = time.minute
        dialogBinding.etAmount.setText(time.amountMl.toString())
        dialogBinding.etLabel.setText(time.label)

        AlertDialog.Builder(requireContext())
            .setTitle("编辑提醒时间")
            .setView(dialogBinding.root)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val hour = dialogBinding.timePicker.hour
                val minute = dialogBinding.timePicker.minute
                val amountStr = dialogBinding.etAmount.text.toString().trim()
                val amountMl = amountStr.toIntOrNull()?.takeIf { it > 0 } ?: 200
                val label = dialogBinding.etLabel.text.toString().trim()
                updateReminderTime(time.copy(hour = hour, minute = minute, amountMl = amountMl, label = label))
            }
            .show()
    }

    private fun addReminderTime(hour: Int, minute: Int, amountMl: Int, label: String) {
        scope.launch(Dispatchers.IO) {
            val context = _binding?.root?.context ?: return@launch
            val dao = (requireActivity().application as App).database.reminderTimeDao()
            dao.insert(ReminderTimeEntity(hour = hour, minute = minute, isEnabled = true, amountMl = amountMl, label = label))
            rescheduleIfNeeded(context)
        }
    }

    private fun updateReminderTime(time: ReminderTimeEntity) {
        scope.launch(Dispatchers.IO) {
            val context = _binding?.root?.context ?: return@launch
            val dao = (requireActivity().application as App).database.reminderTimeDao()
            dao.update(time)
            rescheduleIfNeeded(context)
        }
    }

    private fun toggleReminder(time: ReminderTimeEntity, enabled: Boolean) {
        scope.launch(Dispatchers.IO) {
            val context = _binding?.root?.context ?: return@launch
            val dao = (requireActivity().application as App).database.reminderTimeDao()
            // 关闭开关时，先取消 AlarmManager 中注册的闹钟，防止禁用后仍触发
            if (!enabled) {
                ReminderScheduler.cancelSingleFixedReminder(context, time.id)
            }
            dao.update(time.copy(isEnabled = enabled))
            rescheduleIfNeeded(context)
        }
    }

    private fun confirmDelete(time: ReminderTimeEntity) {
        PreferenceManager.setHasTriedSwipeDeleteReminder(requireContext(), true)
        val timeStr = String.format("%02d:%02d", time.hour, time.minute)
        val desc = if (time.label.isNotBlank()) "$timeStr · ${time.label}" else timeStr
        AlertDialog.Builder(requireContext())
            .setTitle("删除提醒时间")
            .setMessage("确定删除 $desc 的提醒吗？")
            .setNegativeButton("取消") { _, _ ->
                if (_binding != null) {
                    binding.tvSwipeHint.visibility = View.GONE
                }
                adapter.notifyDataSetChanged()
            }
            .setPositiveButton("删除") { _, _ -> deleteReminder(time) }
            .show()
    }

    private fun deleteReminder(time: ReminderTimeEntity) {
        scope.launch(Dispatchers.IO) {
            val context = _binding?.root?.context ?: return@launch
            val dao = (requireActivity().application as App).database.reminderTimeDao()
            // 先取消该提醒在 AlarmManager 中注册的闹钟，防止删除后仍触发
            ReminderScheduler.cancelSingleFixedReminder(context, time.id)
            dao.deleteById(time.id)
            rescheduleIfNeeded(context)
        }
    }

    private fun rescheduleIfNeeded(context: android.content.Context) {
        if (PreferenceManager.isReminderEnabled(context)) {
            ReminderScheduler.scheduleAllReminders(context)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
