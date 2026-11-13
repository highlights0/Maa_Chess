package com.aliothmoon.maameow.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTrace

/** Compact stop control above the calibrated board, never over a chess intersection. */
class XiangqiStatusOverlay(private val context: Context) {
    companion object {
        internal const val TOP_DP = 44
        internal const val HEIGHT_DP = 72
    }
    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var label: TextView? = null

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
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(12); y = dp(TOP_DP) }
        try {
            windows.addView(row, params)
            view = row; label = textView
        } catch (error: Exception) { XiangqiTrace("auto-status").failure("show", error) }
    }

    fun hide() {
        val attached = view ?: return
        view = null; label = null
        runCatching { windows.removeViewImmediate(attached) }
            .onFailure { XiangqiTrace("auto-status").failure("hide", it) }
    }
}
