package com.zhengui.waterreminder.ui.record

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zhengui.waterreminder.data.dao.WaterRecordDao
import com.zhengui.waterreminder.databinding.ItemMonthlySummaryBinding

class MonthSummaryAdapter(private val dailyGoal: Int) :
    RecyclerView.Adapter<MonthSummaryAdapter.ViewHolder>() {

    private var items: List<WaterRecordDao.DailySummary> = emptyList()

    fun submitList(list: List<WaterRecordDao.DailySummary>) {
        items = list
        notifyDataSetChanged()
    }

    fun updateGoal(goal: Int) {
        // goal might change, but we just re-notify
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMonthlySummaryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemMonthlySummaryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(summary: WaterRecordDao.DailySummary) {
            binding.tvDay.text = summary.day
            binding.tvTotalAmount.text = "${summary.total} ml"
            binding.ivCheck.visibility = if (summary.total >= dailyGoal) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }
}
