package com.zhengui.waterreminder.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.zhengui.waterreminder.data.entity.WaterRecord

@Dao
interface WaterRecordDao {

    @Insert
    suspend fun insert(record: WaterRecord): Long

    @Query("SELECT * FROM water_records WHERE drinkTime >= :dayStart AND drinkTime < :dayEnd ORDER BY drinkTime DESC")
    suspend fun getByDate(dayStart: Long, dayEnd: Long): List<WaterRecord>

    @Query("SELECT SUM(amountMl) FROM water_records WHERE drinkTime >= :dayStart AND drinkTime < :dayEnd")
    suspend fun getDailyTotal(dayStart: Long, dayEnd: Long): Int?

    @Query("SELECT DATE(drinkTime / 1000, 'unixepoch') AS day, SUM(amountMl) AS total FROM water_records WHERE drinkTime >= :monthStart AND drinkTime < :monthEnd GROUP BY day ORDER BY day")
    suspend fun getMonthlySummary(monthStart: Long, monthEnd: Long): List<DailySummary>

    @Query("DELETE FROM water_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(record: WaterRecord)

    data class DailySummary(
        val day: String,
        val total: Int
    )
}
