package com.aliothmoon.maameow.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.xiangqi.XiangqiMove
import com.aliothmoon.maameow.domain.xiangqi.XiangqiRecognitionResult
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTrace
import com.aliothmoon.maameow.domain.xiangqi.screenPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Transparent, non-touchable hints in physical-screen coordinates. */
class XiangqiHintOverlay(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun hide() {
        val attached = view ?: return
        view = null
        runCatching { windows.removeViewImmediate(attached) }
            .onFailure { XiangqiTrace("game-hint").failure("hide", it) }
    }

    fun show(result: XiangqiRecognitionResult, move: XiangqiMove) {
        hide()
        if (!Settings.canDrawOverlays(context)) return
        val from = move.from.screenPoint(result.calibration, result.redAtTopOnScreen)
        val to = move.to.screenPoint(result.calibration, result.redAtTopOnScreen)
        val density = context.resources.displayMetrics.density
        val radius = ((result.calibration.right - result.calibration.left) / 8 * 0.42).toFloat()
        val hint = object : View(context) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                val origin = IntArray(2)
                getLocationOnScreen(origin)
                val x1 = from.x.toFloat() - origin[0]
                val y1 = from.y.toFloat() - origin[1]
                val x2 = to.x.toFloat() - origin[0]
                val y2 = to.y.toFloat() - origin[1]
                paint.color = Color.rgb(0, 180, 70)
                paint.strokeWidth = 3 * density
                paint.style = Paint.Style.STROKE
                canvas.drawLine(x1, y1, x2, y2, paint)
                val angle = atan2(y2 - y1, x2 - x1)
                for (wing in listOf(-0.6f, 0.6f)) {
                    canvas.drawLine(x2, y2, x2 - cos(angle + wing) * radius,
                        y2 - sin(angle + wing) * radius, paint)
                }
                canvas.drawCircle(x2, y2, radius, paint)
                paint.color = Color.rgb(255, 160, 0)
                canvas.drawCircle(x1, y1, radius, paint)
                paint.style = Paint.Style.FILL
                paint.textSize = 13 * density
                canvas.drawText(context.getString(R.string.xiangqi_game_hint), x1 - radius, y1 - radius - 8 * density, paint)
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 0.6f // Below Android's obscuring-opacity limit so game touches pass through.
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            windows.addView(hint, params)
            view = hint
            XiangqiTrace("game-hint").event("shown", "from=$from to=$to")
        } catch (error: Exception) {
            XiangqiTrace("game-hint").failure("show", error)
        }
    }
}
