package com.zhengui.waterreminder.ui.record

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SwipeToDeleteCallback(
    private val onDelete: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val deletePaint = Paint().apply {
        color = 0xFFF44336.toInt() // Red
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt() // White
        textSize = 36f
        isAntiAlias = true
    }

    override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onDelete(viewHolder.adapterPosition)
    }

    override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dx: Float, dy: Float, actionState: Int, isCurrentlyActive: Boolean) {
        val itemView = vh.itemView
        if (dx < 0) {
            val bg = RectF(itemView.width + dx, itemView.top.toFloat(), itemView.width.toFloat(), itemView.bottom.toFloat())
            c.drawRect(bg, deletePaint)
            c.drawText("删除", bg.centerX() - textPaint.measureText("删除") / 2, bg.centerY() + textPaint.textSize / 2, textPaint)
        }
        super.onChildDraw(c, rv, vh, dx, dy, actionState, isCurrentlyActive)
    }
}
