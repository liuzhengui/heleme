package com.zhengui.waterreminder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zhengui.waterreminder.data.dao.PersonTypeDao
import com.zhengui.waterreminder.data.dao.ReminderTimeDao
import com.zhengui.waterreminder.data.dao.WaterRecordDao
import com.zhengui.waterreminder.data.entity.PersonType
import com.zhengui.waterreminder.data.entity.ReminderTimeEntity
import com.zhengui.waterreminder.data.entity.WaterRecord

@Database(
    entities = [WaterRecord::class, PersonType::class, ReminderTimeEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun waterRecordDao(): WaterRecordDao
    abstract fun personTypeDao(): PersonTypeDao
    abstract fun reminderTimeDao(): ReminderTimeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `person_types` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `dailyGoalMl` INTEGER NOT NULL,
                        `defaultAmountMl` INTEGER NOT NULL,
                        `reminderIntervalMin` INTEGER NOT NULL,
                        `notificationStartHour` INTEGER NOT NULL DEFAULT 8,
                        `notificationStartMinute` INTEGER NOT NULL DEFAULT 0,
                        `notificationEndHour` INTEGER NOT NULL DEFAULT 21,
                        `notificationEndMinute` INTEGER NOT NULL DEFAULT 0,
                        `isPreset` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reminder_times` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL DEFAULT 1,
                        `amountMl` INTEGER NOT NULL DEFAULT 200,
                        `label` TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v4 added indices for performance; handled by Room entity annotations
                // No structural changes needed beyond what Room auto-generates
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "water_reminder_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
