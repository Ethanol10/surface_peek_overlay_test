package com.ethanol10.surfaceduooverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** One notification as the peek strip sees it: just a key and a small icon. */
data class PeekNotification(val key: String, val icon: Drawable)

/**
 * Where notification data comes from.
 * Prototype: a fake. AOSP: back this with the active-notifications state
 * SystemUI already exposes.
 */
interface PeekNotificationSource {
    fun current(): List<PeekNotification>
    fun addListener(listener: (List<PeekNotification>) -> Unit)
    fun removeListener(listener: (List<PeekNotification>) -> Unit)
}

/**
 * Owns the Peek Mode window and its state.
 *
 * The layout has two identical outer-edge panels plus a clock + notification-icon strip
 * along the hinge on each screen (rotated 90 degrees: anticlockwise on the left screen,
 * clockwise on the right). All bind functions update every copy.
 *
 * @param hingeWidthPx hinge gap in RAW PIXELS within the single combined display
 *        (66 for the target device). It is deliberately not converted from dp.
 *        Kept as a parameter so it can be changed in one place.
 * @param maxIcons how many notification icons each outer panel draws.
 * @param maxCenterIcons how many notification icons each hinge strip draws.
 */
class PeekController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val notifications: PeekNotificationSource,
    private val hingeWidthPx: Int = DEFAULT_HINGE_GAP_PX,
    private val maxIcons: Int = 6,
    private val maxCenterIcons: Int = 5,
) {
    companion object {
        /** Hinge gap of the only device this targets, in pixels. */
        const val DEFAULT_HINGE_GAP_PX = 0
    }

    private var peekView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide() }

    val isShowing: Boolean get() = peekView != null

    private val notifListener: (List<PeekNotification>) -> Unit = { bindNotifications(it) }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = bindBattery(intent)
    }

    /** @param timeoutMs auto-hide delay, or null to stay until [hide] is called. */
    fun show(timeoutMs: Long? = null) {
        if (peekView == null) {
            val view = LayoutInflater.from(context).inflate(R.layout.peek_mode, null)
            view.findViewById<View>(R.id.peek_hinge_gap).layoutParams.width = hingeWidthPx
            peekView = view
            windowManager.addView(view, layoutParams())

            // registerReceiver returns the last sticky battery broadcast: use it for first paint.
            context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.let { bindBattery(it) }
            notifications.addListener(notifListener)
            bindNotifications(notifications.current())
        }
        handler.removeCallbacks(autoHide)
        timeoutMs?.let { handler.postDelayed(autoHide, it) }
    }

    fun hide() {
        handler.removeCallbacks(autoHide)
        val view = peekView ?: return
        notifications.removeListener(notifListener)
        runCatching { context.unregisterReceiver(batteryReceiver) }
        windowManager.removeView(view)
        peekView = null
    }

    /** The two cloned outer panels. Child ids repeat inside each, so look them up per panel. */
    private fun panels(): List<View> {
        val view = peekView ?: return emptyList()
        return listOf(R.id.peek_panel_left, R.id.peek_panel_right)
            .mapNotNull { view.findViewById<View>(it) }
    }

    private fun bindBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val text = if (charging) "\u26A1 $pct%" else "$pct%"
        panels().forEach { it.findViewById<TextView>(R.id.peek_battery).text = text }
    }

    private fun bindNotifications(list: List<PeekNotification>) {
        val density = context.resources.displayMetrics.density
        val size = (24 * density).toInt()
        val margin = (6 * density).toInt()
        val countLabel = when (list.size) {
            0 -> ""
            1 -> "1 notification"
            else -> "${list.size} notifications"
        }

        // A Drawable instance shouldn't be shared between views, so every view gets its own copy.
        fun iconView(n: PeekNotification) = ImageView(context).apply {
            setImageDrawable(n.icon.constantState?.newDrawable()?.mutate() ?: n.icon)
            setColorFilter(0xFFFFFFFF.toInt())
        }

        // Outer-edge panels: icon grid + count
        panels().forEach { panel ->
            val grid = panel.findViewById<GridLayout>(R.id.peek_notif_icons)
            val countText = panel.findViewById<TextView>(R.id.peek_notif_count)

            grid.removeAllViews()
            list.take(maxIcons).forEach { n ->
                grid.addView(iconView(n), GridLayout.LayoutParams().apply {
                    width = size; height = size
                    setMargins(margin, margin, margin, margin)
                })
            }
            countText.text = countLabel
            countText.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }

        // Hinge strips (one per screen): icons in a single line, rotated along the hinge
        peekView?.let { root ->
            listOf(R.id.peek_strip_left, R.id.peek_strip_right)
                .mapNotNull { root.findViewById<View>(it) }
                .forEach { strip ->
                    val row = strip.findViewById<LinearLayout>(R.id.peek_strip_icons)
                    row.removeAllViews()
                    list.take(maxCenterIcons).forEach { n ->
                        row.addView(iconView(n), LinearLayout.LayoutParams(size, size).apply {
                            setMargins(margin, 0, margin, 0)
                        })
                    }
                }
        }
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        // Prototype only (needs "draw over other apps"). In SystemUI use a window type
        // SystemUI is allowed to add, the same way other full-display SystemUI windows do.
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.OPAQUE
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
}