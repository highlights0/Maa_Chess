package com.aliothmoon.maameow.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTrace
import com.aliothmoon.maameow.domain.xiangqi.XiangqiBoardCalibration
import kotlin.math.hypot
import kotlin.math.roundToInt

internal fun clampXiangqiStatusPosition(x: Int, y: Int, width: Int, height: Int, barWidth: Int, barHeight: Int): Pair<Int, Int> =
    x.coerceIn(0, (width - barWidth).coerceAtLeast(0)) to y.coerceIn(0, (height - barHeight).coerceAtLeast(0))

/** Draggable stop control; board-overlapping content is cleared during captures and input. */
class XiangqiStatusOverlay(private val context: Context) {
    companion object {
        internal const val TOP_DP = 44
        internal const val HEIGHT_DP = 72
    }
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var label: TextView? = null
    private var layout: WindowManager.LayoutParams? = null
    private var temporarilyHidden = false
    var isDragging: Boolean = false
        private set
    private val preferences = context.getSharedPreferences("xiangqi_status_position", Context.MODE_PRIVATE)

    fun show(text: String, stop: () -> Unit) {
        label?.let { it.text = text; return }
        if (!Settings.canDrawOverlays(context)) return
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply { setColor(0xdd202124.toInt()); cornerRadius = dp(10).toFloat() }
        }
        val textView = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 12f; this.text = text
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(textView, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(context).apply {
            this.text = context.getString(R.string.xiangqi_auto_stop_short)
            textSize = 12f
            minWidth = 0; minimumWidth = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { stop() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val params = WindowManager.LayoutParams(
            minOf(dp(300), context.resources.displayMetrics.widthPixels - dp(24)), dp(HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (preferences.getFloat("x_dp", 12f) * density).roundToInt()
            y = (preferences.getFloat("y_dp", TOP_DP.toFloat()) * density).roundToInt()
        }
        fun moveTo(x: Int, y: Int) {
            if (view !== row) return
            val bounds = Rect()
            row.getWindowVisibleDisplayFrame(bounds)
            val (clampedX, clampedY) = clampXiangqiStatusPosition(x, y,
                bounds.width().takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels,
                bounds.height().takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels,
                params.width, params.height)
            params.x = clampedX; params.y = clampedY
            runCatching { windows.updateViewLayout(row, params) }
                .onFailure { XiangqiTrace("auto-status").failure("drag", it); hide() }
        }
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var moved = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val drag = android.view.View.OnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    moved = false; isDragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (moved || hypot(dx, dy) > slop) {
                        moved = true
                        moveTo(startX + dx.roundToInt(), startY + dy.roundToInt())
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    if (moved) {
                        preferences.edit().putFloat("x_dp", params.x / density).putFloat("y_dp", params.y / density).apply()
                        XiangqiTrace("auto-status").event("position.saved", "x=${params.x} y=${params.y}")
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) target.performClick()
                }
            }
            true
        }
        row.setOnTouchListener(drag)
        textView.setOnTouchListener(drag)
        try {
            windows.addView(row, params)
            view = row; label = textView; layout = params
            row.post { if (view === row) moveTo(params.x, params.y) }
        } catch (error: Exception) { XiangqiTrace("auto-status").failure("show", error) }
    }

    @Suppress("DEPRECATION")
    fun clearIfOverlapping(calibration: XiangqiBoardCalibration): Boolean {
        val attached = view ?: return false
        val params = layout ?: return false
        if (temporarilyHidden) return false
        val metrics = android.util.DisplayMetrics()
        windows.defaultDisplay.getRealMetrics(metrics)
        // Unknown layouts still need a clean capture so the user can calibrate them.
        val board = runCatching { calibration.scaledTo(metrics.widthPixels, metrics.heightPixels) }.getOrNull()?.let { c ->
            val padding = ((c.right - c.left) / 8 * 0.65).toInt()
            Rect(c.left.toInt() - padding, c.top.toInt() - padding, c.right.toInt() + padding, c.bottom.toInt() + padding)
        }
        val origin = IntArray(2)
        attached.getLocationOnScreen(origin)
        if (board != null && !Rect.intersects(board, Rect(origin[0], origin[1], origin[0] + attached.width, origin[1] + attached.height))) return false
        params.alpha = 0f
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        windows.updateViewLayout(attached, params)
        temporarilyHidden = true
        XiangqiTrace("auto-status").event("temporarily-cleared", "reason=board-overlap")
        return true
    }

    fun restoreAfterCapture() {
        val attached = view ?: return
        val params = layout ?: return
        if (!temporarilyHidden) return
        params.alpha = 1f
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        windows.updateViewLayout(attached, params)
        temporarilyHidden = false
        XiangqiTrace("auto-status").event("restored")
    }

    fun hide() {
        val attached = view ?: return
        view = null; label = null; layout = null
        temporarilyHidden = false; isDragging = false
        runCatching { windows.removeViewImmediate(attached) }
            .onFailure { XiangqiTrace("auto-status").failure("hide", it) }
    }
}
