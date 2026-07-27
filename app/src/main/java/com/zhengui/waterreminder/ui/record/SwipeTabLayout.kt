package com.zhengui.waterreminder.ui.record

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * 包裹内容区域的自定义 LinearLayout，通过 onInterceptTouchEvent 拦截水平快速滑动，
 * 用于左右滑动切换 TabLayout 的 Tab 页。继承 LinearLayout 以保持原垂直布局行为。
 */
class SwipeTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val diffX = e2.x - e1.x
            // 仅当水平位移和速度足够大，且水平分量明显大于垂直分量时，才判定为水平滑动
            if (abs(diffX) > 80 && abs(velocityX) > 200 && abs(velocityX) > abs(velocityY) * 1.5f) {
                if (diffX < 0) {
                    onSwipeLeft?.invoke()
                    return true
                } else {
                    onSwipeRight?.invoke()
                    return true
                }
            }
            return false
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 先让 GestureDetector 分析触摸事件；若检测为水平滑动，后续事件将不会传给子 View
        val intercepted = gestureDetector.onTouchEvent(ev)
        return intercepted || super.onInterceptTouchEvent(ev)
    }
}
