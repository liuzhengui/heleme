package com.zhengui.waterreminder.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zhengui.waterreminder.data.entity.ReminderTimeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderTimeDao {

    @Query("SELECT * FROM reminder_times ORDER BY hour ASC, minute ASC")
    fun getAll(): Flow<List<ReminderTimeEntity>>

    @Query("SELECT * FROM reminder_times WHERE isEnabled = 1 ORDER BY hour ASC, minute ASC")
    suspend fun getEnabled(): List<ReminderTimeEntity>

    @Insert
    suspend fun insert(time: ReminderTimeEntity): Long

    @Delete
    suspend fun delete(time: ReminderTimeEntity)

    @Query("DELETE FROM reminder_times WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM reminder_times WHERE id = :id")
    suspend fun getById(id: Long): ReminderTimeEntity?

    @Update
    suspend fun update(time: ReminderTimeEntity)
}
