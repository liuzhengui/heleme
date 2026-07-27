package com.zhengui.waterreminder.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import com.zhengui.waterreminder.R

class CircularProgressRing @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var ringColor: Int = Color.parseColor("#E5E5E5")
    private var progressColor: Int = Color.parseColor("#111111")
    private var textColor: Int = Color.parseColor("#111111")
    private var subTextColor: Int = Color.parseColor("#6B6B6B")
    private var strokeWidthPx: Float = 4f
    private var maxProgressValue: Int = 100
    private var progressValue: Int = 0
    private var displayValue: Int = 0

    private var currentProgress: Float = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val rectF = RectF()
    private var progressAnimator: ValueAnimator? = null

    init {
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.CircularProgressRing)
            ringColor = ta.getColor(R.styleable.CircularProgressRing_ringColor, ringColor)
            progressColor = ta.getColor(R.styleable.CircularProgressRing_progressColor, progressColor)
            textColor = ta.getColor(R.styleable.CircularProgressRing_centerTextColor, textColor)
            subTextColor = ta.getColor(R.styleable.CircularProgressRing_centerSubTextColor, subTextColor)
            strokeWidthPx = ta.getDimension(R.styleable.CircularProgressRing_ringStrokeWidth, strokeWidthPx)
            progressValue = ta.getInt(R.styleable.CircularProgressRing_ringProgress, 0)
            maxProgressValue = ta.getInt(R.styleable.CircularProgressRing_ringMaxProgress, 100)
            ta.recycle()
        }

        ringPaint.color = ringColor
        ringPaint.strokeWidth = strokeWidthPx
        progressPaint.color = progressColor
        progressPaint.strokeWidth = strokeWidthPx
        amountPaint.color = textColor
        subPaint.color = subTextColor

        currentProgress = progressValue.toFloat()
    }

    fun setProgress(progress: Int, animate: Boolean = true) {
        displayValue = progress
        val target = progress.coerceIn(0, maxProgressValue).toFloat()
        progressAnimator?.cancel()
        if (animate) {
            progressAnimator = ValueAnimator.ofFloat(currentProgress, target).apply {
                duration = 800
                interpolator = OvershootInterpolator(0.8f)
                addUpdateListener {
                    currentProgress = it.animatedValue as Float
                    progressValue = currentProgress.toInt()
                    invalidate()
                }
                start()
            }
        } else {
            currentProgress = target
            progressValue = progress.coerceIn(0, maxProgressValue)
            invalidate()
        }
    }

    fun setMaxProgress(max: Int) {
        maxProgressValue = max
        invalidate()
    }

    fun setCenterTextColor(color: Int) {
        textColor = color
        amountPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val size = width.coerceAtMost(height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val padding = strokeWidthPx
        val radius = size / 2f - padding

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // 背景圆环
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // 进度圆弧
        val sweepAngle = if (maxProgressValue > 0) {
            (currentProgress / maxProgressValue) * 360f
        } else 0f
        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)

        // 中心饮水量
        val amountText = displayValue.toString()
        amountPaint.textSize = radius * 0.55f
        val amountY = cy - (amountPaint.descent() + amountPaint.ascent()) / 2f - radius * 0.12f
        canvas.drawText(amountText, cx, amountY, amountPaint)

        // 单位 ml
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = subTextColor
            textSize = radius * 0.22f
            textAlign = Paint.Align.CENTER
        }
        val unitY = amountY + radius * 0.32f
        canvas.drawText("ml", cx, unitY, unitPaint)

        // 目标提示
        subPaint.textSize = radius * 0.14f
        val subY = cy + radius * 0.55f
        canvas.drawText("of $maxProgressValue ml", cx, subY, subPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        progressAnimator?.cancel()
    }
}
