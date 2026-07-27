package com.zhengui.waterreminder.data.repository

import com.zhengui.waterreminder.data.dao.WaterRecordDao
import com.zhengui.waterreminder.data.entity.WaterRecord

class WaterRecordRepository(private val dao: WaterRecordDao) {

    suspend fun insert(amountMl: Int, personTypeId: Long?) {
        dao.insert(WaterRecord(drinkTime = System.currentTimeMillis(), amountMl = amountMl, personTypeId = personTypeId))
    }

    suspend fun getByDate(dayStart: Long, dayEnd: Long): List<WaterRecord> {
        return dao.getByDate(dayStart, dayEnd)
    }

    suspend fun getDailyTotal(dayStart: Long, dayEnd: Long): Int {
        return dao.getDailyTotal(dayStart, dayEnd) ?: 0
    }

    suspend fun getMonthlySummary(monthStart: Long, monthEnd: Long): List<WaterRecordDao.DailySummary> {
        return dao.getMonthlySummary(monthStart, monthEnd)
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }

    suspend fun delete(record: WaterRecord) {
        dao.delete(record)
    }
}
