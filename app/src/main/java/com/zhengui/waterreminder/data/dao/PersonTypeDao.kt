package com.zhengui.waterreminder.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.zhengui.waterreminder.data.entity.PersonType
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonTypeDao {

    @Query("SELECT * FROM person_types ORDER BY isPreset DESC, id ASC")
    fun getAll(): Flow<List<PersonType>>

    @Query("SELECT * FROM person_types WHERE isPreset = 1")
    suspend fun getPresetTypes(): List<PersonType>

    @Query("SELECT * FROM person_types WHERE id = :id")
    suspend fun getById(id: Long): PersonType?

    @Insert
    suspend fun insert(personType: PersonType): Long

    @Update
    suspend fun update(personType: PersonType)

    @Delete
    suspend fun delete(personType: PersonType)
}
