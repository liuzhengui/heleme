package com.zhengui.waterreminder.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zhengui.waterreminder.data.entity.WaterRecord
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.Charsets

object CsvExporter {

    suspend fun export(context: Context, records: List<WaterRecord>, typeNames: Map<Long, String>): Uri? {
        if (records.isEmpty()) return null

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val file = File(context.cacheDir, "water_records_export.csv")

        BufferedWriter(OutputStreamWriter(file.outputStream(), Charsets.UTF_8)).use { writer ->
            // 写入 UTF-8 BOM，确保 Excel 正确识别中文
            writer.write('\uFEFF'.toString())

            // 写入表头
            writer.write(csvLine("日期", "时间", "饮水量(ml)", "人员类型"))
            writer.newLine()

            // 写入数据行
            for (record in records) {
                val date = dateFormat.format(Date(record.drinkTime))
                val time = timeFormat.format(Date(record.drinkTime))
                val amount = record.amountMl.toString()
                val personType = typeNames[record.personTypeId] ?: "未知"

                writer.write(csvLine(date, time, amount, personType))
                writer.newLine()
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", file)
    }

    /**
     * 将字段用双引号包裹并转义内部引号，确保 CSV 格式正确
     */
    private fun csvLine(vararg fields: String): String {
        return fields.joinToString(",") { field ->
            val escaped = field.replace("\"", "\"\"")
            "\"$escaped\""
        }
    }
}
