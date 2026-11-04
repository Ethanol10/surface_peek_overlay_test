package com.ethanol10.surfaceduooverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
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

/**
 * One notification type on the peek strip (typically one app).
 *
 * @param count how many notifications of this type there are. The hinge strip shows the number
 *        on the box only when it is greater than 1.
 */
data class PeekNotification(val key: String, val icon: Drawable, val count: Int = 1)

/**
 * Where notification data comes from.
 * Prototype: a fake. AOSP: back this with the active-notifications state
 * SystemUI already exposes (group by package to get one entry + count per app).
 */
interface PeekNotificationSource {
    fun current(): List<PeekNotification>
    fun addListener(listener: (List<PeekNotification>) -> Unit)
    fun removeListener(listener: (List<PeekNotification>) -> Unit)
}

/**
 * Owns the Peek Mode window and its state.
 *
 * The layout has two identical outer-edge panels plus a strip along the hinge on each screen
 * (rotated 90 degrees: anticlockwise on the left screen, clockwise on the right). Each strip
 * holds the clock, a battery box, then one box per notification type. All bind functions
 * update every copy.
 *
 * Notification slots: at most [maxVisible] types are shown. If there are more, the next slot
 * becomes an ellipsis icon. On the hinge strip that ellipsis box also shows how many
 * notification TYPES are hidden (not how many notifications).
 *
 * @param hingeWidthPx hinge gap in RAW PIXELS within the single combined display
 *        (66 for the target device). It is deliberately not converted from dp.
 *        Kept as a parameter so it can be changed in one place.
 * @param maxVisible how many notification types are drawn before the ellipsis slot.
 */
class PeekController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val notifications: PeekNotificationSource,
    private val hingeWidthPx: Int = DEFAULT_HINGE_GAP_PX,
    private val maxVisible: Int = 5,
) {
    companion object {
        /** Hinge gap of the only device this targets, in pixels. */
        const val DEFAULT_HINGE_GAP_PX = 66

        /**
         * The orientation Peek is always shown in, whatever the device's physical orientation.
         * The layout assumes the spanned display with a vertical hinge (left screen | hinge |
         * right screen), i.e. the wide orientation. If your device maps that to a different
         * rotation, change this one constant (e.g. SCREEN_ORIENTATION_REVERSE_LANDSCAPE).
         */
        const val FORCED_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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

            // Outer panels: battery background rounded on all corners (strips keep top-only).
            panels().forEach {
                it.findViewById<BatteryBoxView>(R.id.peek_panel_battery).setRoundAllCorners(true)
            }

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

    /** The two cloned hinge strips (left screen, right screen). */
    private fun strips(): List<View> {
        val view = peekView ?: return emptyList()
        return listOf(R.id.peek_strip_left, R.id.peek_strip_right)
            .mapNotNull { view.findViewById<View>(it) }
    }

    private fun bindBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING // drives the pulse
        val pluggedIn = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 // drives the bolt icon
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0

        // Every battery box (outer panels + hinge strips) is the same view: percent, icon,
        // level fill animating up from 0, and the pulse while charging.
        panels().forEach {
            it.findViewById<BatteryBoxView>(R.id.peek_panel_battery).setLevel(pct, charging, pluggedIn)
        }
        strips().forEach {
            it.findViewById<BatteryBoxView>(R.id.peek_strip_battery).setLevel(pct, charging, pluggedIn)
        }
    }

    private fun bindNotifications(list: List<PeekNotification>) {
        val density = context.resources.displayMetrics.density
        val size = (24 * density).toInt()
        val margin = (6 * density).toInt()
        val total = list.sumOf { it.count }
        val countLabel = when (total) {
            0 -> ""
            1 -> "1 notification"
            else -> "$total notifications"
        }

        // Types that get their own slot, and how many TYPES spill into the ellipsis slot.
        val visible = list.take(maxVisible)
        val hiddenTypes = list.size - visible.size

        // A Drawable instance shouldn't be shared between views, so every view gets its own copy.
        fun ownIcon(n: PeekNotification): Drawable =
            n.icon.constantState?.newDrawable()?.mutate() ?: n.icon
        fun moreIcon(): Drawable = context.getDrawable(R.drawable.ic_peek_more)!!

        // Outer-edge panels: icon grid (ellipsis icon in the overflow slot) + total count
        panels().forEach { panel ->
            val grid = panel.findViewById<GridLayout>(R.id.peek_notif_icons)
            val countText = panel.findViewById<TextView>(R.id.peek_notif_count)

            fun addIcon(drawable: Drawable) {
                val icon = ImageView(context).apply {
                    setImageDrawable(drawable)
                    setColorFilter(0xFFFFFFFF.toInt())
                }
                grid.addView(icon, GridLayout.LayoutParams().apply {
                    width = size; height = size
                    setMargins(margin, margin, margin, margin)
                })
            }

            grid.removeAllViews()
            visible.forEach { addIcon(ownIcon(it)) }
            if (hiddenTypes > 0) addIcon(moreIcon())

            countText.text = countLabel
            countText.visibility = if (total == 0) View.GONE else View.VISIBLE
        }

        // Hinge strips: one rounded-top box per type. The number shows only when that type has
        // more than one notification. The overflow box is an ellipsis plus the hidden type count.
        val inflater = LayoutInflater.from(context)
        strips().forEach { strip ->
            val row = strip.findViewById<LinearLayout>(R.id.peek_strip_icons)

            fun addBox(drawable: Drawable, number: String?) {
                val box = inflater.inflate(R.layout.peek_notif_box, row, false)
                box.findViewById<ImageView>(R.id.peek_box_icon).setImageDrawable(drawable)
                box.findViewById<TextView>(R.id.peek_box_count).apply {
                    if (number == null) {
                        visibility = View.GONE
                    } else {
                        text = number
                        visibility = View.VISIBLE
                    }
                }
                row.addView(box)
            }

            row.removeAllViews()
            visible.forEach { n ->
                addBox(ownIcon(n), if (n.count > 1) n.count.toString() else null)
            }
            if (hiddenTypes > 0) addBox(moreIcon(), hiddenTypes.toString())
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
        // Pin the display to one orientation while Peek is up, so the view never rotates with the device.
        screenOrientation = FORCED_ORIENTATION
    }
}
 