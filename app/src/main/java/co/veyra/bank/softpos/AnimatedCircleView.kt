package co.veyra.bank.softpos

import co.veyra.bank.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom view that draws an animated circle stroke that progressively draws around the shape
 */
class AnimatedCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f // Doubled from 2f
        isAntiAlias = true
        color = 0xFFF44336.toInt() // Default red, will be set dynamically
    }

    private val rectF = RectF()
    private var sweepAngle = 0f
    private var startAngle = -90f // Start from top

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setSweepAngle(angle: Float) {
        sweepAngle = angle
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = paint.strokeWidth / 2
        rectF.set(padding, padding, w - padding, h - padding)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(rectF, startAngle, sweepAngle, false, paint)
    }
}

