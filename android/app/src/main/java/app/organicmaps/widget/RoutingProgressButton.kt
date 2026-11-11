package app.organicmaps.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import app.organicmaps.BuildConfig
import app.organicmaps.R

class RoutingProgressButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle,
) : AppCompatButton(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.button_accent_normal)
    }

    init {
        clipToOutline = true
    }

    var buildProgress: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, 100)
            if (clamped == field) return
            field = clamped
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        if (buildProgress > 0) {
            val fillWidth = width * buildProgress / 100f
            // Keep the InCar START label on its disabled surface throughout route calculation.
            val top = if (BuildConfig.IS_IN_CAR) height - resources.getDimension(R.dimen.nav_progress) else 0f
            if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                canvas.drawRect(width - fillWidth, top, width.toFloat(), height.toFloat(), fillPaint)
            } else {
                canvas.drawRect(0f, top, fillWidth, height.toFloat(), fillPaint)
            }
        }
        super.onDraw(canvas)
    }
}
