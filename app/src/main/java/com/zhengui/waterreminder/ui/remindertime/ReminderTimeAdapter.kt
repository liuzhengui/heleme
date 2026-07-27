package com.zhengui.waterreminder.ui.remindertime

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zhengui.waterreminder.data.entity.ReminderTimeEntity
import com.zhengui.waterreminder.databinding.ItemReminderTimeBinding

class ReminderTimeAdapter(
    private val onToggle: (ReminderTimeEntity, Boolean) -> Unit,
    private val onDelete: (ReminderTimeEntity) -> Unit,
    private val onEdit: (ReminderTimeEntity) -> Unit
) : RecyclerView.Adapter<ReminderTimeAdapter.ViewHolder>() {

    private var items: List<ReminderTimeEntity> = emptyList()

    fun getItem(position: Int): ReminderTimeEntity = items[position]

    fun submitList(list: List<ReminderTimeEntity>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderTimeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemReminderTimeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ReminderTimeEntity) {
            binding.tvTime.text = String.format("%02d:%02d", item.hour, item.minute)
            binding.tvAmount.text = "${item.amountMl}ml"

            if (item.label.isNotBlank()) {
                binding.tvLabel.visibility = android.view.View.VISIBLE
                binding.tvLabel.text = item.label
            } else {
                binding.tvLabel.visibility = android.view.View.GONE
            }

            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = item.isEnabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked -> onToggle(item, isChecked) }

            binding.btnEdit.setOnClickListener { onEdit(item) }
        }
    }
}
