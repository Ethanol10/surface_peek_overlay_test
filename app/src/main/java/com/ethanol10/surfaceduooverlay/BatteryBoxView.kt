package com.ethanol10.surfaceduooverlay

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * The battery box used in both the outer panels and the hinge strips (peek_battery_box.xml).
 *
 * - Rounded TOP corners, flat bottom (or all four corners via [setRoundAllCorners]).
 * - Background is a green bar filling the box horizontally from the left in proportion to the
 *   battery level. Every time the view attaches (each time Peek is shown) the bar animates up
 *   from 0 to the current level; later level changes animate smoothly from where it is.
 * - While charging, the green fill slowly pulses to a lighter green.
 * - Shows the percent, and a battery icon that gains a lightning bolt while plugged in.
 *
 * Children: an ImageView with id peek_battery_icon and a TextView with id peek_battery_text.
 *
 * "Horizontally" and "top" are in this view's own frame. Inside the rotated hinge strip that
 * means the fill grows along the strip and the rounded corners face away from the hinge.
 */
class BatteryBoxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private companion object {
        const val CORNER_DP = 10f           // keep equal to peek_box_bg.xml
        const val TRACK_COLOR = 0x26FFFFFF  // keep equal to peek_box_bg.xml
        const val GREEN = 0xFF2E7D32.toInt()
        const val LIGHT_GREEN = 0xFF66BB6A.toInt()
        const val PULSE_MS = 2000L          // one direction; a full cycle is twice this
        const val FILL_MS = 1000L           // 0 -> current level
    }

    private val corner = CORNER_DP * resources.displayMetrics.density
    private val shape = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colorEvaluator = ArgbEvaluator()

    private var icon: ImageView? = null
    private var percentText: TextView? = null

    private var targetLevel = 0   // the real battery level, 0..100
    private var shownLevel = 0f   // what is currently drawn, 0..100 (animated)
    private var charging = false
    private var pulse = 0f        // 0 = base green, 1 = lighter green

    private var fillAnimator: ValueAnimator? = null

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = PULSE_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pulse = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setWillNotDraw(false) // ViewGroups skip onDraw by default
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        icon = findViewById(R.id.peek_battery_icon)
        percentText = findViewById(R.id.peek_battery_text)
    }

    /**
     * @param percent 0..100
     * @param isCharging true while actively charging: drives the pulse
     * @param isPluggedIn true while on a charger (including full): shows the bolt icon
     */
    fun setLevel(percent: Int, isCharging: Boolean, isPluggedIn: Boolean) {
        targetLevel = percent.coerceIn(0, 100)
        charging = isCharging
        percentText?.text = "$targetLevel%"
        icon?.setImageResource(
            if (isPluggedIn) R.drawable.ic_peek_battery_charging else R.drawable.ic_peek_battery
        )
        updatePulse()
        // Before attach, onAttachedToWindow starts the 0 -> level animation instead.
        if (isAttachedToWindow) animateFill()
        invalidate()
    }

    private fun animateFill() {
        fillAnimator?.cancel()
        fillAnimator = ValueAnimator.ofFloat(shownLevel, targetLevel.toFloat()).apply {
            duration = FILL_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shownLevel = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updatePulse() {
        if (charging && isAttachedToWindow) {
            if (!pulseAnimator.isStarted) pulseAnimator.start()
        } else {
            pulseAnimator.cancel()
            pulse = 0f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        shownLevel = 0f // always start from empty
        animateFill()
        updatePulse()
    }

    override fun onDetachedFromWindow() {
        fillAnimator?.cancel()
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    private var roundAll = false

    /** Round all four corners (outer panels) instead of only the top two (hinge strips). */
    fun setRoundAllCorners(all: Boolean) {
        roundAll = all
        buildShape()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildShape()
    }

    private fun buildShape() {
        shape.reset()
        val bottom = if (roundAll) corner else 0f
        // Radii order: top-left, top-right, bottom-right, bottom-left (x, y each).
        shape.addRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            floatArrayOf(corner, corner, corner, corner, bottom, bottom, bottom, bottom),
            Path.Direction.CW,
        )
    }

    override fun onDraw(canvas: Canvas) {
        // empty track
        paint.color = TRACK_COLOR
        canvas.drawPath(shape, paint)

        // green fill, clipped to the rounded shape
        paint.color = if (charging) {
            colorEvaluator.evaluate(pulse, GREEN, LIGHT_GREEN) as Int
        } else {
            GREEN
        }
        canvas.save()
        canvas.clipPath(shape)
        canvas.drawRect(0f, 0f, width * shownLevel / 100f, height.toFloat(), paint)
        canvas.restore()
    }
}