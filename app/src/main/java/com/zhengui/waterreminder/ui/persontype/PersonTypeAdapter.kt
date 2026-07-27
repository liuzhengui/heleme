package com.zhengui.waterreminder.ui.persontype

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.data.entity.PersonType
import com.zhengui.waterreminder.databinding.ItemPersonTypeBinding

class PersonTypeAdapter(
    private var currentTypeId: Long,
    private val onSelect: (PersonType) -> Unit,
    private val onEdit: (PersonType) -> Unit,
    private val onDelete: (PersonType) -> Unit
) : RecyclerView.Adapter<PersonTypeAdapter.ViewHolder>() {

    private var items: List<PersonType> = emptyList()

    fun submitList(list: List<PersonType>) {
        items = list
        notifyDataSetChanged()
    }

    fun updateCurrentTypeId(id: Long) {
        currentTypeId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPersonTypeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemPersonTypeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(type: PersonType) {
            binding.tvName.text = type.name
            binding.tvDailyGoal.text = "日目标 ${type.dailyGoalMl}ml"
            binding.tvDefaultAmount.text = "单次 ${type.defaultAmountMl}ml"
            val hours = type.reminderIntervalMin / 60
            val mins = type.reminderIntervalMin % 60
            binding.tvInterval.text = if (hours > 0 && mins > 0) {
                "间隔 ${hours}h${mins}min"
            } else if (hours > 0) {
                "间隔 ${hours}h"
            } else {
                "间隔 ${mins}min"
            }

            val isSelected = type.id == currentTypeId
            binding.tvSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.btnSelect.visibility = if (isSelected) View.GONE else View.VISIBLE
            binding.btnSelect.setOnClickListener { onSelect(type) }

            binding.btnEdit.setOnClickListener { onEdit(type) }
            binding.btnDelete.text = if (type.isPreset) "预设" else "删除"
            binding.btnDelete.isEnabled = !type.isPreset
            binding.btnDelete.setOnClickListener { if (!type.isPreset) onDelete(type) }
            binding.btnDelete.setTextColor(
                if (type.isPreset) itemView.context.getColor(R.color.gray) else itemView.context.getColor(R.color.text_primary)
            )
        }
    }
}
