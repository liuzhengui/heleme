package com.zhengui.waterreminder.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.zhengui.waterreminder.App
import com.zhengui.waterreminder.data.entity.WaterRecord
import kotlinx.coroutines.*

class WidgetDrinkReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val amount = intent.getIntExtra("drink_amount", 200)
        val pendingResult = goAsync()
        scope.launch {
            try {
                val db = (context.applicationContext as App).database
                val prefs = context.getSharedPreferences("water_reminder_prefs", Context.MODE_PRIVATE)
                val typeId = prefs.getLong("current_type_id", 1L)
                db.waterRecordDao().insert(WaterRecord(drinkTime = System.currentTimeMillis(), amountMl = amount, personTypeId = typeId))
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "已记录 ${amount}ml", Toast.LENGTH_SHORT).show()
                    WidgetUpdateHelper.updateAllWidgets(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
