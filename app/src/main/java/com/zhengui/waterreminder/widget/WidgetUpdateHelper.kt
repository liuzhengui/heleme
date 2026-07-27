package com.zhengui.waterreminder.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdateHelper {
    fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val smallProvider = ComponentName(context, WaterWidgetProvider::class.java)
        val largeProvider = ComponentName(context, WaterWidgetLargeProvider::class.java)

        try {
            val smallIds = manager.getAppWidgetIds(smallProvider)
            if (smallIds.isNotEmpty()) {
                WaterWidgetProvider.updateWidgets(context, manager, smallIds)
            }
        } catch (_: Exception) {}

        try {
            val largeIds = manager.getAppWidgetIds(largeProvider)
            if (largeIds.isNotEmpty()) {
                WaterWidgetLargeProvider.updateWidgets(context, manager, largeIds)
            }
        } catch (_: Exception) {}
    }
}
