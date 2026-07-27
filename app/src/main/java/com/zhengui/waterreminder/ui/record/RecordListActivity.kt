package com.zhengui.waterreminder.ui.record

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.databinding.ActivityRecordListBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RecordListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordListBinding
    private lateinit var viewModel: RecordListViewModel
    private lateinit var dayAdapter: DayRecordAdapter
    private lateinit var monthAdapter: MonthSummaryAdapter
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    private var isBarChartMode = true
    private var goalMl = 2500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[RecordListViewModel::class.java]
        binding.btnToolbarNav.setOnClickListener { finish() }

        dayAdapter = DayRecordAdapter()
        monthAdapter = MonthSummaryAdapter(2500)
        binding.recyclerRecords.layoutManager = LinearLayoutManager(this)

        setupTabLayout()
        setupDateNavigation()
        setupSwipeToDelete()
        setupChartToggle()
        setupObservers()
        viewModel.loadData()
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        viewModel.switchToDayView()
                        binding.recyclerRecords.visibility = View.VISIBLE
                        binding.chartContainer.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.shimmerContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.VISIBLE
                    }
                    1 -> {
                        viewModel.switchToMonthView()
                        binding.recyclerRecords.visibility = View.VISIBLE
                        binding.chartContainer.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.shimmerContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.recyclerRecords.visibility = View.GONE
                        binding.chartContainer.visibility = View.VISIBLE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.shimmerContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.GONE
                        viewModel.loadChartData()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun setupDateNavigation() {
        binding.btnPrevDate.setOnClickListener { viewModel.prevDate() }
        binding.btnNextDate.setOnClickListener { viewModel.nextDate() }

        binding.tvCurrentDate.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setSelection(binding.tvCurrentDate.tag as? Long ?: System.currentTimeMillis())
                .build()
            datePicker.addOnPositiveButtonClickListener { selection ->
                Calendar.getInstance().also { cal ->
                    cal.timeInMillis = selection
                    viewModel.setDate(cal)
                }
            }
            datePicker.show(supportFragmentManager, "date_picker")
        }
    }

    private fun setupChartToggle() {
        binding.btnBarChart.setOnClickListener {
            isBarChartMode = true
            binding.barChart.visibility = View.VISIBLE
            binding.lineChart.visibility = View.GONE
            setChartButtonActive(binding.btnBarChart, binding.btnLineChart)
        }
        binding.btnLineChart.setOnClickListener {
            isBarChartMode = false
            binding.barChart.visibility = View.GONE
            binding.lineChart.visibility = View.VISIBLE
            setChartButtonActive(binding.btnLineChart, binding.btnBarChart)
        }
    }

    private fun setChartButtonActive(active: com.google.android.material.button.MaterialButton, inactive: com.google.android.material.button.MaterialButton) {
        active.setTextColor(ContextCompat.getColor(this, R.color.blue_primary))
        active.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue_light)
        inactive.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        inactive.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.transparent)
    }

    private fun setupSwipeToDelete() {
        val swipeCallback = SwipeToDeleteCallback { position ->
            val record = dayAdapter.getItemAt(position)
            viewModel.deleteRecord(record)
            Snackbar.make(binding.root, "已删除记录", Snackbar.LENGTH_LONG)
                .setAction("撤销") { viewModel.loadData() }
                .show()
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerRecords)
    }

    private fun setupObservers() {
        viewModel.currentDate.observe(this) { cal ->
            updateDateDisplay(cal)
            binding.tvCurrentDate.tag = cal.timeInMillis
        }

        viewModel.isDayView.observe(this) { isDayView ->
            if (isDayView) {
                binding.recyclerRecords.adapter = dayAdapter
                binding.tvSummaryLabel.text = "当日总饮水量"
            } else {
                binding.recyclerRecords.adapter = monthAdapter
                binding.tvSummaryLabel.text = "当月总记录"
            }
        }

        viewModel.records.observe(this) { records ->
            dayAdapter.submitList(records)
            updateEmptyState(records.isEmpty())
        }

        viewModel.monthlySummaries.observe(this) { summaries ->
            monthAdapter.submitList(summaries)
            updateEmptyState(summaries.isEmpty())
        }

        viewModel.dailyTotal.observe(this) { total ->
            binding.tvSummaryAmount.text = "${total} ml"
            updateGoalStatus(total, viewModel.dailyGoal.value ?: 2500)
        }

        viewModel.dailyGoal.observe(this) { goal ->
            goalMl = goal
            monthAdapter = MonthSummaryAdapter(goal)
            if (viewModel.isDayView.value != true) {
                binding.recyclerRecords.adapter = monthAdapter
                viewModel.monthlySummaries.value?.let { monthAdapter.submitList(it) }
            }
            updateGoalStatus(viewModel.dailyTotal.value ?: 0, goal)
        }

        viewModel.typeNames.observe(this) { names ->
            dayAdapter = DayRecordAdapter(names)
            if (viewModel.isDayView.value == true) {
                binding.recyclerRecords.adapter = dayAdapter
                viewModel.records.value?.let { dayAdapter.submitList(it) }
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                binding.shimmerContainer.visibility = View.VISIBLE
                binding.shimmerContainer.startShimmer()
                binding.recyclerRecords.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
            } else {
                binding.shimmerContainer.stopShimmer()
                binding.shimmerContainer.visibility = View.GONE
                if (binding.chartContainer.visibility != View.VISIBLE) {
                    binding.recyclerRecords.visibility = View.VISIBLE
                }
            }
        }

        viewModel.chartData.observe(this) { data ->
            setupBarChart(data)
            setupLineChart(data)
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty && viewModel.isLoading.value != true) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.recyclerRecords.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    private fun updateDateDisplay(cal: Calendar) {
        binding.tvCurrentDate.text = if (viewModel.isDayView.value == true) dayFormat.format(cal.time) else monthFormat.format(cal.time)
    }

    private fun updateGoalStatus(total: Int, goal: Int) {
        if (total >= goal) {
            binding.tvGoalStatus.text = "已达标"
            binding.tvGoalStatus.setTextColor(getColor(R.color.green_success))
        } else {
            binding.tvGoalStatus.text = "未达标(目标 ${goal}ml)"
            binding.tvGoalStatus.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun styleChartAxis(chart: BarChart) {
        chart.apply {
            setBackgroundColor(Color.WHITE)
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setScaleEnabled(false)
            setDoubleTapToZoomEnabled(false)
            description.isEnabled = false
            legend.textColor = ContextCompat.getColor(this@RecordListActivity, R.color.text_secondary)
            legend.textSize = 12f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = ContextCompat.getColor(this@RecordListActivity, R.color.divider)
                textColor = ContextCompat.getColor(this@RecordListActivity, R.color.text_secondary)
                textSize = 11f
                granularity = 1f
                setAvoidFirstLastClipping(true)
            }

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@RecordListActivity, R.color.gray)
                gridLineWidth = 0.5f
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(this@RecordListActivity, R.color.text_secondary)
                textSize = 11f
                setDrawZeroLine(true)
                zeroLineColor = ContextCompat.getColor(this@RecordListActivity, R.color.divider)
            }

            axisRight.isEnabled = false
        }
    }

    private fun styleChartAxis(chart: LineChart) {
        chart.apply {
            setBackgroundColor(Color.WHITE)
            setDrawGridBackground(false)
            setPinchZoom(false)
            setScaleEnabled(false)
            setDoubleTapToZoomEnabled(false)
            description.isEnabled = false
            legend.textColor = ContextCompat.getColor(this@RecordListActivity, R.color.text_secondary)
            legend.textSize = 12f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = ContextCompat.getColor(this@RecordListActivity, R.color.divider)
                textColor = ContextCompat.getColor(this@RecordListActivity, R.color.text_secondary)
                textSize = 11f
                granularity = 1f
                setAvoidFirstLastClipping(true)
            }

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@RecordListActivity, R.color.gray)
                gridLineWidth = 0.5f
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(this@RecordListActivity, R.color.text_secondary)
                textSize = 11f
            }

            axisRight.isEnabled = false
        }
    }

    private fun setupBarChart(summaries: List<com.zhengui.waterreminder.data.dao.WaterRecordDao.DailySummary>) {
        if (summaries.isEmpty()) {
            binding.barChart.clear()
            binding.barChart.setNoDataText("暂无数据")
            return
        }

        styleChartAxis(binding.barChart)

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        var maxValue = 0f

        summaries.forEachIndexed { index, summary ->
            entries.add(BarEntry(index.toFloat(), summary.total.toFloat()))
            labels.add(summary.day.substring(5))
            if (summary.total > maxValue) maxValue = summary.total.toFloat()
        }

        // Add goal limit line
        val goalLine = LimitLine(goalMl.toFloat(), "目标 ${goalMl}ml").apply {
            lineWidth = 1.5f
            lineColor = ContextCompat.getColor(this@RecordListActivity, R.color.green_success)
            textColor = ContextCompat.getColor(this@RecordListActivity, R.color.green_success)
            textSize = 11f
            enableDashedLine(8f, 4f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        binding.barChart.axisLeft.removeAllLimitLines()
        binding.barChart.axisLeft.addLimitLine(goalLine)

        val dataSet = BarDataSet(entries, "饮水量(ml)").apply {
            color = ContextCompat.getColor(this@RecordListActivity, R.color.blue_primary)
            valueTextColor = ContextCompat.getColor(this@RecordListActivity, R.color.text_secondary)
            valueTextSize = 10f
            setDrawValues(true)
            // toolbar highlight
            isHighlightEnabled = true
            highLightColor = ContextCompat.getColor(this@RecordListActivity, R.color.blue_primary)
            highLightAlpha = 40
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry?): String {
                    return if (barEntry != null && barEntry.y > 0) "${barEntry.y.toInt()}" else ""
                }
            }
        }

        binding.barChart.apply {
            visibility = View.VISIBLE
            data = BarData(dataSet)
            data.barWidth = 0.6f
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            barData.setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}"
                }
            })
            setFitBars(true)
            animateY(600)
            invalidate()
        }

        // Always keep line chart hidden on initial chart data load, bar chart visible
        if (isBarChartMode) {
            binding.barChart.visibility = View.VISIBLE
            binding.lineChart.visibility = View.GONE
        }
    }

    private fun setupLineChart(summaries: List<com.zhengui.waterreminder.data.dao.WaterRecordDao.DailySummary>) {
        if (summaries.isEmpty()) {
            binding.lineChart.clear()
            binding.lineChart.setNoDataText("暂无数据")
            return
        }

        styleChartAxis(binding.lineChart)

        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        summaries.forEachIndexed { index, summary ->
            entries.add(Entry(index.toFloat(), summary.total.toFloat()))
            labels.add(summary.day.substring(5))
        }

        // Add goal limit line
        val goalLine = LimitLine(goalMl.toFloat(), "目标 ${goalMl}ml").apply {
            lineWidth = 1.5f
            lineColor = ContextCompat.getColor(this@RecordListActivity, R.color.green_success)
            textColor = ContextCompat.getColor(this@RecordListActivity, R.color.green_success)
            textSize = 11f
            enableDashedLine(8f, 4f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        binding.lineChart.axisLeft.removeAllLimitLines()
        binding.lineChart.axisLeft.addLimitLine(goalLine)

        val dataSet = LineDataSet(entries, "饮水量(ml)").apply {
            color = ContextCompat.getColor(this@RecordListActivity, R.color.blue_primary)
            lineWidth = 2.5f
            circleRadius = 4f
            circleHoleRadius = 2.5f
            setCircleColor(ContextCompat.getColor(this@RecordListActivity, R.color.blue_primary))
            circleHoleColor = Color.WHITE
            valueTextColor = ContextCompat.getColor(this@RecordListActivity, R.color.text_secondary)
            valueTextSize = 10f
            setDrawValues(true)
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(this@RecordListActivity, R.drawable.chart_fill_blue)
            setDrawCircles(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawHighlightIndicators(true)
            highLightColor = ContextCompat.getColor(this@RecordListActivity, R.color.blue_primary)
            valueFormatter = object : ValueFormatter() {
                override fun getPointLabel(entry: Entry?): String {
                    return if (entry != null && entry.y > 0) "${entry.y.toInt()}" else ""
                }
            }
        }

        binding.lineChart.apply {
            data = LineData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            setVisibleXRangeMaximum(15f)
            moveViewToX(entries.size.toFloat())
            animateX(600)
            invalidate()
        }
    }
}
