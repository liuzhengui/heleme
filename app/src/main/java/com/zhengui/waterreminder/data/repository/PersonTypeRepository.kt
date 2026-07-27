package com.zhengui.waterreminder.data.repository

import com.zhengui.waterreminder.data.dao.PersonTypeDao
import com.zhengui.waterreminder.data.entity.PersonType
import kotlinx.coroutines.flow.Flow

class PersonTypeRepository(private val dao: PersonTypeDao) {

    fun getAll(): Flow<List<PersonType>> = dao.getAll()

    fun getAllTypesFlow(): Flow<List<PersonType>> = dao.getAll()

    suspend fun getPresetTypes(): List<PersonType> = dao.getPresetTypes()

    suspend fun getById(id: Long): PersonType? = dao.getById(id)

    suspend fun insert(personType: PersonType): Long = dao.insert(personType)

    suspend fun update(personType: PersonType) = dao.update(personType)

    suspend fun delete(personType: PersonType) = dao.delete(personType)
}
