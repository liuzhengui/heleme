package com.zhengui.waterreminder.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.data.dao.WaterRecordDao
import com.zhengui.waterreminder.data.entity.WaterRecord
import com.zhengui.waterreminder.data.repository.PersonTypeRepository
import com.zhengui.waterreminder.data.repository.WaterRecordRepository
import com.zhengui.waterreminder.util.PreferenceManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RecordListViewModel(application: Application) : AndroidViewModel(application) {

    private val waterRecordRepo = WaterRecordRepository((application as App).database.waterRecordDao())
    private val personTypeRepo = PersonTypeRepository((application as App).database.personTypeDao())

    private val _records = MutableLiveData<List<WaterRecord>>(emptyList())
    val records: LiveData<List<WaterRecord>> = _records

    private val _monthlySummaries = MutableLiveData<List<WaterRecordDao.DailySummary>>(emptyList())
    val monthlySummaries: LiveData<List<WaterRecordDao.DailySummary>> = _monthlySummaries

    private val _dailyTotal = MutableLiveData(0)
    val dailyTotal: LiveData<Int> = _dailyTotal

    private val _dailyGoal = MutableLiveData(2500)
    val dailyGoal: LiveData<Int> = _dailyGoal

    private val _currentDate = MutableLiveData(Calendar.getInstance())
    val currentDate: LiveData<Calendar> = _currentDate

    private val _isDayView = MutableLiveData(true)
    val isDayView: LiveData<Boolean> = _isDayView

    private val _typeNames = MutableLiveData<Map<Long, String>>(emptyMap())
    val typeNames: LiveData<Map<Long, String>> = _typeNames

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _chartData = MutableLiveData<List<WaterRecordDao.DailySummary>>(emptyList())
    val chartData: LiveData<List<WaterRecordDao.DailySummary>> = _chartData

    private val _statistics = MutableLiveData<StatisticsResult?>()
    val statistics: LiveData<StatisticsResult?> = _statistics

    fun switchToDayView() {
        _isDayView.value = true
        loadDayData()
    }

    fun switchToMonthView() {
        _isDayView.value = false
        loadMonthData()
    }

    fun prevDate() {
        val cal = _currentDate.value ?: Calendar.getInstance()
        if (_isDayView.value == true) cal.add(Calendar.DAY_OF_MONTH, -1) else cal.add(Calendar.MONTH, -1)
        _currentDate.value = cal
        loadData()
    }

    fun nextDate() {
        val cal = _currentDate.value ?: Calendar.getInstance()
        if (_isDayView.value == true) cal.add(Calendar.DAY_OF_MONTH, 1) else cal.add(Calendar.MONTH, 1)
        _currentDate.value = cal
        loadData()
    }

    fun setDate(cal: Calendar) {
        _currentDate.value = cal
        loadData()
    }

    fun loadData() {
        if (_isDayView.value == true) loadDayData() else loadMonthData()
    }

    fun loadChartData() {
        viewModelScope.launch {
            val cal = _currentDate.value ?: return@launch
            _chartData.value = waterRecordRepo.getMonthlySummary(getMonthStart(cal), getMonthEnd(cal))
        }
    }

    private fun loadDayData() {
        viewModelScope.launch {
            _isLoading.value = true
            val cal = _currentDate.value ?: return@launch
            val dayStart = getDayStart(cal)
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L

            _records.value = waterRecordRepo.getByDate(dayStart, dayEnd)
            _dailyTotal.value = waterRecordRepo.getDailyTotal(dayStart, dayEnd)

            val typeId = PreferenceManager.getCurrentTypeId(getApplication())
            val type = personTypeRepo.getById(typeId)
            _dailyGoal.value = type?.dailyGoalMl ?: 2500
            loadTypeNames()
            _isLoading.value = false
        }
    }

    private fun loadMonthData() {
        viewModelScope.launch {
            _isLoading.value = true
            val cal = _currentDate.value ?: return@launch
            val monthStart = getMonthStart(cal)
            val monthEnd = getMonthEnd(cal)
            _monthlySummaries.value = waterRecordRepo.getMonthlySummary(monthStart, monthEnd)
            _dailyTotal.value = waterRecordRepo.getDailyTotal(monthStart, monthEnd) ?: 0

            val typeId = PreferenceManager.getCurrentTypeId(getApplication())
            val type = personTypeRepo.getById(typeId)
            _dailyGoal.value = type?.dailyGoalMl ?: 2500
            _isLoading.value = false
        }
    }

    private suspend fun loadTypeNames() {
        val records = _records.value ?: return
        val typeIds = records.mapNotNull { it.personTypeId }.distinct()
        val nameMap = mutableMapOf<Long, String>()
        for (id in typeIds) {
            personTypeRepo.getById(id)?.let { nameMap[id] = it.name }
        }
        _typeNames.value = nameMap
    }

    fun deleteRecord(record: WaterRecord) {
        viewModelScope.launch {
            waterRecordRepo.delete(record)
            loadData()
        }
    }

    private fun getDayStart(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    private fun getMonthStart(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.set(Calendar.DAY_OF_MONTH, 1)
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    private fun getMonthEnd(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.add(Calendar.MONTH, 1)
        copy.set(Calendar.DAY_OF_MONTH, 1)
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    fun getPersonTypeName(id: Long?, callback: (String) -> Unit) {
        viewModelScope.launch {
            if (id == null) {
                callback("未知")
                return@launch
            }
            callback(personTypeRepo.getById(id)?.name ?: "未知")
        }
    }

    fun loadStatistics() {
        viewModelScope.launch {
            val cal = Calendar.getInstance()
            val weekStart = getWeekStart(cal)
            val weekEnd = weekStart + 7 * 24 * 60 * 60 * 1000L

            val summaries = waterRecordRepo.getMonthlySummary(weekStart, weekEnd)
            val typeId = PreferenceManager.getCurrentTypeId(getApplication())
            val type = personTypeRepo.getById(typeId)
            val goal = type?.dailyGoalMl ?: 2500

            if (summaries.isEmpty()) {
                _statistics.value = null
                return@launch
            }

            val records = waterRecordRepo.getByDate(weekStart, weekEnd)
            val totalAmount = summaries.sumOf { it.total }
            val drinkCount = records.size
            val dailyAverage = totalAmount / summaries.size
            val achievedDays = summaries.count { it.total >= goal }
            val achievementRate = achievedDays.toFloat() / summaries.size

            val maxSummary = summaries.maxByOrNull { it.total }
            val (peakStartHour, peakEndHour) = calculatePeakHours(records)
            val longestStreak = calculateLongestStreak(summaries, goal)
            val avgIntervalMinutes = calculateAverageInterval(records)
            val comparisonText = calculateWeekComparison(totalAmount, weekStart)
            val weekRange = formatWeekRange(weekStart, weekEnd)

            _statistics.value = StatisticsResult(
                weekRange = weekRange,
                dailyAverage = dailyAverage,
                achievementRate = achievementRate,
                totalAmount = totalAmount,
                drinkCount = drinkCount,
                maxDay = maxSummary?.day,
                maxAmount = maxSummary?.total ?: 0,
                peakStartHour = peakStartHour,
                peakEndHour = peakEndHour,
                longestStreak = longestStreak,
                avgIntervalMinutes = avgIntervalMinutes,
                weekSummaries = summaries,
                comparisonText = comparisonText
            )
        }
    }

    private fun calculatePeakHours(records: List<WaterRecord>): Pair<Int, Int> {
        if (records.isEmpty()) return Pair(14, 16)
        val buckets = IntArray(12)
        for (record in records) {
            val hour = getHour(record.drinkTime)
            val bucket = hour / 2
            if (bucket in buckets.indices) {
                buckets[bucket] += record.amountMl
            }
        }
        val maxBucket = buckets.indices.maxByOrNull { buckets[it] } ?: 7
        return Pair(maxBucket * 2, (maxBucket + 1) * 2)
    }

    private fun calculateLongestStreak(summaries: List<WaterRecordDao.DailySummary>, goal: Int): Int {
        var maxStreak = 0
        var currentStreak = 0
        for (summary in summaries) {
            if (summary.total >= goal) {
                currentStreak++
                if (currentStreak > maxStreak) maxStreak = currentStreak
            } else {
                currentStreak = 0
            }
        }
        return maxStreak
    }

    private fun calculateAverageInterval(records: List<WaterRecord>): Int {
        if (records.size < 2) return 0
        val sorted = records.sortedBy { it.drinkTime }
        var totalDiff = 0L
        for (i in 1 until sorted.size) {
            totalDiff += sorted[i].drinkTime - sorted[i - 1].drinkTime
        }
        return (totalDiff / (records.size - 1) / 60_000).toInt()
    }

    private suspend fun calculateWeekComparison(currentTotal: Int, currentWeekStart: Long): String? {
        val lastWeekStart = currentWeekStart - 7 * 24 * 60 * 60 * 1000L
        val lastWeekEnd = currentWeekStart
        val lastSummaries = waterRecordRepo.getMonthlySummary(lastWeekStart, lastWeekEnd)
        if (lastSummaries.isEmpty()) return null
        val lastTotal = lastSummaries.sumOf { it.total }
        val diffLiters = (currentTotal - lastTotal) / 1000.0
        val diffText = String.format(Locale.getDefault(), "%.1f", kotlin.math.abs(diffLiters))
        return when {
            diffLiters > 0.05 -> "比上周多喝 ${diffText} L，继续保持。"
            diffLiters < -0.05 -> "比上周少喝 ${diffText} L，加油。"
            else -> "与上周持平，保持稳定。"
        }
    }

    private fun formatWeekRange(weekStart: Long, weekEnd: Long): String {
        val sdf = SimpleDateFormat("MMM dd", Locale.ENGLISH)
        return "${sdf.format(Date(weekStart)).uppercase(Locale.getDefault())} - ${sdf.format(Date(weekEnd - 1)).uppercase(Locale.getDefault())}"
    }

    private fun getHour(timestamp: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return c.get(Calendar.HOUR_OF_DAY)
    }

    private fun getWeekStart(cal: Calendar): Long {
        val copy = cal.clone() as Calendar
        copy.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }
}

data class StatisticsResult(
    val weekRange: String,
    val dailyAverage: Int,
    val achievementRate: Float,
    val totalAmount: Int,
    val drinkCount: Int,
    val maxDay: String?,
    val maxAmount: Int,
    val peakStartHour: Int,
    val peakEndHour: Int,
    val longestStreak: Int,
    val avgIntervalMinutes: Int,
    val weekSummaries: List<WaterRecordDao.DailySummary>,
    val comparisonText: String?
)
