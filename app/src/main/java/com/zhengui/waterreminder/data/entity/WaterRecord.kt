package com.zhengui.waterreminder.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "water_records",
    foreignKeys = [ForeignKey(
        entity = PersonType::class,
        parentColumns = ["id"],
        childColumns = ["personTypeId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("personTypeId"), Index("drinkTime")]
)
data class WaterRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val drinkTime: Long,
    val amountMl: Int,
    val personTypeId: Long?
)
