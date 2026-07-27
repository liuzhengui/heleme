package com.zhengui.waterreminder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "person_types")
data class PersonType(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dailyGoalMl: Int,
    val defaultAmountMl: Int,
    val reminderIntervalMin: Int,
    val notificationStartHour: Int = 8,
    val notificationStartMinute: Int = 0,
    val notificationEndHour: Int = 21,
    val notificationEndMinute: Int = 0,
    val isPreset: Boolean = false
)
