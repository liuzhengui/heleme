package com.zhengui.waterreminder.ui.record

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.data.entity.WaterRecord
import java.text.SimpleDateFormat
import java.util.Locale

class DayRecordAdapter(
    private var typeNames: Map<Long, String> = emptyMap()
) : RecyclerView.Adapter<DayRecordAdapter.ViewHolder>() {

    private var items: List<WaterRecord> = emptyList()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submitList(list: List<WaterRecord>) {
        items = list
        notifyDataSetChanged()
    }

    fun updateTypeNames(names: Map<Long, String>) {
        typeNames = names
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_drink_timeline, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun getItemAt(position: Int): WaterRecord = items[position]

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvDesc)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)

        fun bind(record: WaterRecord) {
            tvTime.text = timeFormat.format(record.drinkTime)
            tvDesc.text = record.personTypeId?.let { typeNames[it] } ?: "喝水"
            tvAmount.text = "${record.amountMl}ml"
        }
    }
}