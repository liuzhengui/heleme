package com.zhengui.waterreminder.ui.record

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.zhengui.waterreminder.R
import com.zhengui.waterreminder.data.dao.WaterRecordDao
import com.zhengui.waterreminder.databinding.FragmentRecordListBinding
import com.zhengui.waterreminder.util.dpToPx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RecordListFragment : Fragment() {

    private var _binding: FragmentRecordListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RecordListViewModel
    private lateinit var dayAdapter: DayRecordAdapter
    private lateinit var monthAdapter: MonthSummaryAdapter
    private val dayFormat = SimpleDateFormat("yyyy · MM · dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy · MM", Locale.getDefault())
    private var isBarChartMode = true
    private var goalMl = 2500

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        _binding = FragmentRecordListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[RecordListViewModel::class.java]

        dayAdapter = DayRecordAdapter()
        monthAdapter = MonthSummaryAdapter(2500)
        binding.recyclerRecords.layoutManager = LinearLayoutManager(requireContext())

        setupCalendarGrid()
        setupTabLayout()
        setupDateNavigation()
        setupChartToggle()
        setupObservers()
        viewModel.loadData()

        // 左右滑动切换 Tab
        binding.swipeContent.onSwipeLeft = {
            val currentTab = binding.tabLayout.selectedTabPosition
            if (currentTab < 3) {
                binding.tabLayout.getTabAt(currentTab + 1)?.select()
            }
        }
        binding.swipeContent.onSwipeRight = {
            val currentTab = binding.tabLayout.selectedTabPosition
            if (currentTab > 0) {
                binding.tabLayout.getTabAt(currentTab - 1)?.select()
            }
        }
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                when (tab.position) {
                    0 -> {
                        viewModel.switchToDayView()
                        binding.datePickerContainer.visibility = View.VISIBLE
                        binding.tvRecordCount.visibility = View.VISIBLE
                        binding.recyclerRecords.visibility = View.VISIBLE
                        binding.monthContainer.visibility = View.GONE
                        binding.chartContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.VISIBLE
                        binding.statisticsContainer.visibility = View.GONE
                        updateSummaryLabel(true)
                        updateRecordCount(viewModel.records.value?.size ?: 0)
                    }
                    1 -> {
                        viewModel.switchToMonthView()
                        binding.datePickerContainer.visibility = View.GONE
                        binding.tvRecordCount.visibility = View.GONE
                        binding.recyclerRecords.visibility = View.GONE
                        binding.monthContainer.visibility = View.VISIBLE
                        binding.chartContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.VISIBLE
                        binding.statisticsContainer.visibility = View.GONE
                        updateSummaryLabel(false)
                        renderMonthView()
                    }
                    2 -> {
                        binding.datePickerContainer.visibility = View.GONE
                        binding.tvRecordCount.visibility = View.GONE
                        binding.recyclerRecords.visibility = View.GONE
                        binding.monthContainer.visibility = View.GONE
                        binding.chartContainer.visibility = View.VISIBLE
                        binding.cardSummary.visibility = View.GONE
                        binding.statisticsContainer.visibility = View.GONE
                        viewModel.loadChartData()
                    }
                    3 -> {
                        binding.datePickerContainer.visibility = View.GONE
                        binding.tvRecordCount.visibility = View.GONE
                        binding.recyclerRecords.visibility = View.GONE
                        binding.monthContainer.visibility = View.GONE
                        binding.chartContainer.visibility = View.GONE
                        binding.cardSummary.visibility = View.GONE
                        binding.statisticsContainer.visibility = View.VISIBLE
                        viewModel.loadStatistics()
                    }
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
    }

    private fun updateSummaryLabel(isDayView: Boolean) {
        if (isDayView) {
            binding.tvSummaryLabel.text = "Daily Total"
            binding.tvSummaryUnit.text = "ml / ${goalMl}ml"
            binding.tvGoalStatus.visibility = View.VISIBLE
            binding.monthChipContainer.visibility = View.GONE
        } else {
            binding.tvSummaryLabel.text = "Monthly Total"
            binding.tvSummaryUnit.text = "升"
            binding.tvGoalStatus.visibility = View.GONE
            binding.monthChipContainer.visibility = View.VISIBLE
        }
    }

    private fun setupDateNavigation() {
        binding.btnPrevDate.setOnClickListener { viewModel.prevDate() }
        binding.btnNextDate.setOnClickListener { viewModel.nextDate() }
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
        setChartButtonActive(binding.btnBarChart, binding.btnLineChart)
    }

    private fun setChartButtonActive(active: com.google.android.material.button.MaterialButton, inactive: com.google.android.material.button.MaterialButton) {
        active.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        active.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.text_primary)
        active.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.text_primary)
        inactive.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        inactive.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.white)
        inactive.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.text_primary)
    }

    private fun setupObservers() {
        viewModel.currentDate.observe(viewLifecycleOwner) { cal -> updateDateDisplay(cal) }

        viewModel.isDayView.observe(viewLifecycleOwner) { isDayView ->
            binding.tvGoalStatus.visibility = if (isDayView) View.VISIBLE else View.GONE
            if (isDayView) {
                binding.recyclerRecords.adapter = dayAdapter
            } else {
                binding.recyclerRecords.adapter = monthAdapter
            }
        }

        viewModel.records.observe(viewLifecycleOwner) { records ->
            dayAdapter.submitList(records)
            if (viewModel.isDayView.value == true) {
                updateRecordCount(records.size)
            }
        }
        viewModel.monthlySummaries.observe(viewLifecycleOwner) {
            if (viewModel.isDayView.value != true) {
                renderMonthView()
            }
        }

        viewModel.typeNames.observe(viewLifecycleOwner) { names ->
            dayAdapter.updateTypeNames(names)
        }

        viewModel.dailyTotal.observe(viewLifecycleOwner) { total ->
            if (viewModel.isDayView.value == true) {
                binding.tvSummaryAmount.text = total.toString()
                updateGoalStatus(total, viewModel.dailyGoal.value ?: 2500)
            } else {
                binding.tvSummaryAmount.text = String.format(Locale.getDefault(), "%.1f", total / 1000.0)
            }
        }

        viewModel.dailyGoal.observe(viewLifecycleOwner) { goal ->
            goalMl = goal
            monthAdapter = MonthSummaryAdapter(goal)
            if (viewModel.isDayView.value != true) {
                binding.recyclerRecords.adapter = monthAdapter
                viewModel.monthlySummaries.value?.let { monthAdapter.submitList(it) }
            }
            updateSummaryLabel(viewModel.isDayView.value == true)
            updateGoalStatus(viewModel.dailyTotal.value ?: 0, goal)
        }

        viewModel.chartData.observe(viewLifecycleOwner) { data ->
            setupBarChart(data)
            setupLineChart(data)
        }

        viewModel.statistics.observe(viewLifecycleOwner) { stats ->
            if (stats == null) {
                binding.tvStatEmpty.visibility = View.VISIBLE
                binding.statDataContainer.visibility = View.GONE
                return@observe
            }
            binding.tvStatEmpty.visibility = View.GONE
            binding.statDataContainer.visibility = View.VISIBLE

            binding.tvStatWeekRange.text = stats.weekRange
            binding.tvStatDailyAverage.text = stats.dailyAverage.toString()
            binding.tvStatAchievementRate.text = (stats.achievementRate * 100).toInt().toString()
            binding.tvStatTotalAmount.text = String.format(Locale.getDefault(), "%.1f", stats.totalAmount / 1000.0)
            binding.tvStatDrinkCount.text = stats.drinkCount.toString()

            renderWeekTrendBars(stats.weekSummaries, goalMl)

            binding.tvStatPeakPeriod.text = String.format(Locale.getDefault(), "%02d:00 - %02d:00", stats.peakStartHour, stats.peakEndHour)
            binding.tvStatBestDay.text = "${formatDayOfWeek(stats.maxDay)} · ${stats.maxAmount}ml"
            binding.tvStatLongestStreak.text = "${stats.longestStreak} 天"
            binding.tvStatAverageInterval.text = formatInterval(stats.avgIntervalMinutes)

            if (stats.comparisonText != null) {
                binding.tvStatComparison.text = stats.comparisonText
                binding.tvStatComparison.visibility = View.VISIBLE
            } else {
                binding.tvStatComparison.visibility = View.GONE
            }
        }
    }

    private fun updateDateDisplay(cal: Calendar) {
        binding.tvCurrentDate.text = if (viewModel.isDayView.value == true) dayFormat.format(cal.time) else monthFormat.format(cal.time)
    }

    private fun updateGoalStatus(total: Int, goal: Int) {
        binding.tvGoalStatus.text = if (total >= goal) "已达标" else "未达标"
        binding.tvGoalStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
    }

    private fun updateRecordCount(count: Int) {
        binding.tvRecordCount.text = "$count Records"
    }

    // ===== Month Calendar =====

    private val calendarCells = mutableListOf<LinearLayout>()
    private val monthTitleFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)

    private fun setupCalendarGrid() {
        val grid = binding.calendarGrid
        grid.removeAllViews()
        calendarCells.clear()

        val cellSize = resources.displayMetrics.widthPixels / 7 - (8.dpToPx())

        repeat(42) {
            val cell = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = (cellSize * 1.15f).toInt()
                    setMargins(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
                }
                setPadding(4.dpToPx(), 6.dpToPx(), 4.dpToPx(), 6.dpToPx())
                setBackgroundResource(R.drawable.bg_calendar_normal)

                addView(TextView(context).apply {
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                })
                addView(TextView(context).apply {
                    textSize = 10f
                    gravity = android.view.Gravity.CENTER
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2.dpToPx() }
                })
            }
            grid.addView(cell)
            calendarCells.add(cell)
        }
    }

    private fun renderMonthView() {
        val cal = viewModel.currentDate.value ?: Calendar.getInstance()
        binding.tvMonthTitle.text = monthTitleFormat.format(cal.time).uppercase()

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)

        val firstDayCal = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat
        val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val prevMonthCal = (firstDayCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
        val daysInPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val summaryMap = viewModel.monthlySummaries.value?.associateBy { it.day } ?: emptyMap()
        val goal = viewModel.dailyGoal.value ?: 2500

        var achievedDays = 0
        var totalAmount = 0
        var maxAmount = 0
        var minAmount = Int.MAX_VALUE
        var recordDays = 0

        calendarCells.forEachIndexed { index, cell ->
            val dayIndex = index - (firstDayOfWeek - 1)
            val dateTv = cell.getChildAt(0) as TextView
            val amountTv = cell.getChildAt(1) as TextView

            when {
                dayIndex < 0 -> {
                    // 上月
                    val day = daysInPrevMonth + dayIndex + 1
                    dateTv.text = day.toString()
                    dateTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                    amountTv.text = ""
                    cell.setBackgroundResource(R.drawable.bg_calendar_normal)
                }
                dayIndex >= daysInMonth -> {
                    // 下月
                    val day = dayIndex - daysInMonth + 1
                    dateTv.text = day.toString()
                    dateTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_hint))
                    amountTv.text = ""
                    cell.setBackgroundResource(R.drawable.bg_calendar_normal)
                }
                else -> {
                    // 当月
                    val day = dayIndex + 1
                    dateTv.text = day.toString()
                    val dayStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
                    val summary = summaryMap[dayStr]
                    val amount = summary?.total ?: 0

                    if (amount > 0) {
                        recordDays++
                        totalAmount += amount
                        if (amount > maxAmount) maxAmount = amount
                        if (amount < minAmount) minAmount = amount
                        if (amount >= goal) achievedDays++
                    }

                    amountTv.text = if (amount > 0) "$amount" else ""

                    if (amount >= goal) {
                        cell.setBackgroundResource(R.drawable.bg_calendar_achieved)
                        dateTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                        amountTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    } else if (amount > 0) {
                        cell.setBackgroundResource(R.drawable.bg_calendar_normal)
                        dateTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                        amountTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    } else {
                        cell.setBackgroundResource(R.drawable.bg_calendar_normal)
                        dateTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                        amountTv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    }
                }
            }
        }

        // 更新汇总卡片 chips
        binding.tvAchievedDays.text = "达标 ${achievedDays} 天"
        val avg = if (recordDays > 0) totalAmount / recordDays else 0
        binding.tvDailyAverage.text = "日均 ${avg} ml"

        // 更新底部统计
        binding.tvMonthTotal.text = String.format(Locale.getDefault(), "%.1f L", totalAmount / 1000.0)
        val rate = if (daysInMonth > 0) (achievedDays * 100 / daysInMonth) else 0
        binding.tvMonthRate.text = "$rate%"
        binding.tvMonthMax.text = if (maxAmount > 0) "$maxAmount ml" else "0 ml"
        binding.tvMonthMin.text = if (minAmount != Int.MAX_VALUE) "$minAmount ml" else "0 ml"
    }

    // ===== Chart Styling =====

    private fun styleBarChart(chart: BarChart) {
        chart.apply {
            setBackgroundColor(Color.WHITE)
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setScaleEnabled(false)
            setDoubleTapToZoomEnabled(false)
            description.isEnabled = false
            legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            legend.textSize = 12f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.divider)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 11f
                granularity = 1f
                setAvoidFirstLastClipping(true)
            }

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.gray)
                gridLineWidth = 0.5f
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 11f
                setDrawZeroLine(true)
                zeroLineColor = ContextCompat.getColor(requireContext(), R.color.divider)
            }

            axisRight.isEnabled = false
        }
    }

    private fun styleLineChart(chart: LineChart) {
        chart.apply {
            setBackgroundColor(Color.WHITE)
            setDrawGridBackground(false)
            setPinchZoom(false)
            setScaleEnabled(false)
            setDoubleTapToZoomEnabled(false)
            description.isEnabled = false
            legend.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            legend.textSize = 12f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(true)
                axisLineColor = ContextCompat.getColor(requireContext(), R.color.divider)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
                textSize = 11f
                granularity = 1f
                setAvoidFirstLastClipping(true)
            }

            axisLeft.apply {
                axisMinimum = 0f
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(requireContext(), R.color.gray)
                gridLineWidth = 0.5f
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
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

        styleBarChart(binding.barChart)

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        summaries.forEachIndexed { index, summary ->
            entries.add(BarEntry(index.toFloat(), summary.total.toFloat()))
            labels.add(summary.day.substring(5))
        }

        val goalLine = LimitLine(goalMl.toFloat(), "目标 ${goalMl}ml").apply {
            lineWidth = 1.5f
            lineColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            textSize = 11f
            enableDashedLine(8f, 4f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        binding.barChart.axisLeft.removeAllLimitLines()
        binding.barChart.axisLeft.addLimitLine(goalLine)

        val dataSet = BarDataSet(entries, "饮水量(ml)").apply {
            color = ContextCompat.getColor(requireContext(), R.color.text_primary)
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 10f
            setDrawValues(true)
            isHighlightEnabled = true
            highLightColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
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
                override fun getFormattedValue(value: Float): String = "${value.toInt()}"
            })
            setFitBars(true)
            animateY(600)
            invalidate()
        }

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

        styleLineChart(binding.lineChart)

        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        summaries.forEachIndexed { index, summary ->
            entries.add(Entry(index.toFloat(), summary.total.toFloat()))
            labels.add(summary.day.substring(5))
        }

        val goalLine = LimitLine(goalMl.toFloat(), "目标 ${goalMl}ml").apply {
            lineWidth = 1.5f
            lineColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            textSize = 11f
            enableDashedLine(8f, 4f, 0f)
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        binding.lineChart.axisLeft.removeAllLimitLines()
        binding.lineChart.axisLeft.addLimitLine(goalLine)

        val dataSet = LineDataSet(entries, "饮水量(ml)").apply {
            color = ContextCompat.getColor(requireContext(), R.color.text_primary)
            lineWidth = 2.5f
            circleRadius = 4f
            circleHoleRadius = 2.5f
            setCircleColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            circleHoleColor = Color.WHITE
            valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            valueTextSize = 10f
            setDrawValues(true)
            setDrawFilled(true)
            fillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.chart_fill_black)
            setDrawCircles(true)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawHighlightIndicators(true)
            highLightColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
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

    private fun renderWeekTrendBars(summaries: List<WaterRecordDao.DailySummary>, goal: Int) {
        val bars = binding.weekTrendBars
        if (bars.childCount == 0 || summaries.isEmpty()) return
        val maxAmount = summaries.maxOfOrNull { it.total } ?: 0
        val minHeight = 4.dpToPx()
        val maxHeight = 72.dpToPx()

        for (i in 0 until minOf(bars.childCount, summaries.size)) {
            val bar = bars.getChildAt(i)
            val summary = summaries[i]
            val ratio = if (maxAmount > 0) summary.total.toFloat() / maxAmount else 0f
            val height = (minHeight + (maxHeight - minHeight) * ratio).toInt()
            bar.layoutParams = bar.layoutParams.apply { this.height = height }
            val colorRes = if (summary.total >= goal) R.color.text_primary else R.color.gray
            bar.setBackgroundColor(ContextCompat.getColor(requireContext(), colorRes))
        }
    }

    private fun formatDayOfWeek(dayString: String?): String {
        if (dayString == null) return "-"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dayString) ?: return "-"
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            "-"
        }
    }

    private fun formatInterval(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "$hours 小时 $mins 分" else "$mins 分"
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
