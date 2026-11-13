package com.aliothmoon.maameow.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.xiangqi.XiangqiBoardCalibration
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTrace
import com.aliothmoon.maameow.domain.xiangqi.validateManualXiangqiCalibration
import kotlin.math.hypot

/** Frozen capture with editable intersections; no gestures reach the game while calibrating. */
class XiangqiCalibrationOverlay(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var frame: Bitmap? = null

    fun hide() {
        root?.let { runCatching { windows.removeViewImmediate(it) } }
        root = null
        frame?.recycle()
        frame = null
    }

    fun show(bitmap: Bitmap, initial: XiangqiBoardCalibration?, onSave: (XiangqiBoardCalibration) -> Unit) {
        hide()
        frame = bitmap
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val caption = TextView(context).apply {
            isClickable = true
            setTextColor(Color.WHITE); textSize = 15f; setPadding(dp(14), dp(18), dp(14), dp(10))
            setBackgroundColor(0xee202124.toInt())
        }
        val save = Button(context).apply { setText(R.string.xiangqi_calibration_save); isEnabled = false }
        val preview = object : View(context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var first = initial?.let { PointF(it.left.toFloat(), it.top.toFloat()) }
            private var last = initial?.let { PointF(it.right.toFloat(), it.bottom.toFloat()) }
            private var draggingFirst = true
            private fun fit() = XiangqiCalibrationViewport(width, height, bitmap.width, bitmap.height)

            fun calibration(): XiangqiBoardCalibration? {
                val a = first ?: return null
                val b = last ?: return null
                return runCatching {
                    XiangqiBoardCalibration(a.x.toDouble(), a.y.toDouble(), b.x.toDouble(), b.y.toDouble(), bitmap.width, bitmap.height)
                        .also(::validateManualXiangqiCalibration)
                }.getOrNull()
            }

            fun refresh() {
                save.isEnabled = calibration() != null
                caption.setText(when {
                    first == null -> R.string.xiangqi_calibration_first
                    last == null -> R.string.xiangqi_calibration_last
                    !save.isEnabled -> R.string.xiangqi_calibration_invalid
                    else -> R.string.xiangqi_calibration_review
                })
                invalidate()
            }

            override fun onDraw(canvas: Canvas) {
                if (width <= 0 || height <= 0) return
                val fit = fit()
                canvas.drawColor(Color.BLACK)
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawBitmap(bitmap, null, RectF(fit.left, fit.top, fit.right, fit.bottom), paint)
                canvas.save()
                canvas.translate(fit.left, fit.top)
                val scale = fit.scale
                canvas.scale(scale, scale)
                val a = first
                val b = last
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f / scale
                if (a != null && b != null && b.x > a.x && b.y > a.y) {
                    paint.color = Color.CYAN
                    for (col in 0..8) {
                        val x = a.x + (b.x - a.x) * col / 8
                        canvas.drawLine(x, a.y, x, b.y, paint)
                    }
                    for (row in 0..9) {
                        val y = a.y + (b.y - a.y) * row / 9
                        canvas.drawLine(a.x, y, b.x, y, paint)
                    }
                }
                for ((point, color) in listOf(a to Color.rgb(255, 160, 0), b to Color.GREEN)) if (point != null) {
                    paint.color = color
                    canvas.drawCircle(point.x, point.y, 14 / scale, paint)
                    canvas.drawLine(point.x - 22 / scale, point.y, point.x + 22 / scale, point.y, paint)
                    canvas.drawLine(point.x, point.y - 22 / scale, point.x, point.y + 22 / scale, paint)
                }
                canvas.restore()
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (width <= 0 || height <= 0) return false
                val fit = fit()
                val point = fit.imagePoint(event.x, event.y)
                val x = point.x.toFloat()
                val y = point.y.toFloat()
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!fit.contains(event.x, event.y)) return false
                        draggingFirst = first == null || (last != null && first!!.let { a ->
                            hypot(x - a.x, y - a.y) <= last!!.let { b -> hypot(x - b.x, y - b.y) }
                        })
                        if (draggingFirst) first = PointF(x, y) else last = PointF(x, y)
                        refresh()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (draggingFirst) first = PointF(x, y) else last = PointF(x, y)
                        refresh()
                    }
                    MotionEvent.ACTION_UP -> performClick()
                }
                return true
            }

            override fun performClick(): Boolean { super.performClick(); return true }
        }
        val container = FrameLayout(context)
        container.addView(preview, FrameLayout.LayoutParams(-1, -1))
        container.addView(caption, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val buttons = LinearLayout(context).apply {
            isClickable = true
            setPadding(dp(12), dp(6), dp(12), dp(18)); setBackgroundColor(0xee202124.toInt())
            addView(Button(context).apply { setText(R.string.common_cancel); setOnClickListener { hide() } }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(save, LinearLayout.LayoutParams(0, -2, 1f))
        }
        save.setOnClickListener {
            preview.calibration()?.let { confirmed ->
                try { onSave(confirmed) } finally { hide() }
            }
        }
        container.addView(buttons, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        preview.refresh()
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        root = container
        try { windows.addView(container, params) }
        catch (error: Exception) { hide(); XiangqiTrace("calibration").failure("show", error); throw error }
    }
}
