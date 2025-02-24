package com.ethanol10.surfaceduooverlay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ethanol10.surfaceduooverlay.SurfaceDuoOverlayService.BatteryReceiver
import com.ethanol10.surfaceduooverlay.SurfaceDuoOverlayService.LockScreenStateReceiver
import java.lang.reflect.InvocationTargetException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class OverlayActivity : ComponentActivity() {
    private var batteryReceiver: BatteryReceiver? = null
    private var windowManager: WindowManager? = null
    private var textView: TextView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isShowing: Boolean = false
    private var lockScreenReceiver: LockScreenStateReceiver? = null
    private var postureOverlay: View? = null
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var fadeHandler: Handler? = null
    private var fadeRunnable: Runnable? = null
    private var sleepAfterShowingOverlay = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        this.setShowWhenLocked(true)
        this.setTurnScreenOn(true)

        val controller = WindowInsetsControllerCompat(
            window, window.decorView
        )

        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        //Entry point for this activity
        sleepAfterShowingOverlay = intent.getBooleanExtra("sleepOnAction", false)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?;
        windowParams = WindowManager.LayoutParams()
        windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
        windowParams?.height = WindowManager.LayoutParams.MATCH_PARENT
        windowParams?.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        windowParams?.format = PixelFormat.TRANSLUCENT
        windowParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_FULLSCREEN
        showOverlay(sleepAfterShowingOverlay)
    }

    fun getBatteryPercentage(context: Context): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun getDisplayText(): String {
        val formatter = SimpleDateFormat("KK:mm a", Locale.getDefault())
        val formattedTime = formatter.format(Date())
        return "${formattedTime}"
    }

    fun getDateText(): String {
        val formatter = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
        val formattedTime = formatter.format(Date())
        return "${formattedTime}"
    }

    fun getBatteryEmoji(context: Context): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        Log.d("ETHANOL10", status.toString())

        // Plug Emoji
        if(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) == 100 && status == BatteryManager.BATTERY_STATUS_CHARGING){
            return "\uD83D\uDD0C"
        }

        // Lightning symbol
        if(status == BatteryManager.BATTERY_STATUS_CHARGING){
            return "⚡"
        }

        //Default to Battery symbol
        return "\uD83D\uDD0B"

    }

    fun scaleView(v: View, startScale: Float, endScale: Float) {
        val anim: Animation = ScaleAnimation(
            1f, 1f,  // Start and end values for the X axis scaling
            startScale, endScale,  // Start and end values for the Y axis scaling
            Animation.RELATIVE_TO_SELF, 0f,  // Pivot point of X scaling
            Animation.RELATIVE_TO_SELF, 1f
        ) // Pivot point of Y scaling
        anim.setFillAfter(true) // Needed to keep the result of the animation
        anim.setDuration(1000)
        anim.setInterpolator(AccelerateDecelerateInterpolator())
        v.startAnimation(anim)
    }

    fun showOverlay(sleepAfterShowingOverlay: Boolean) {
        hideOverlay(false)
        val displayText = getDisplayText()
        val dateText = getDateText()

        // Inflate the overlay view
        postureOverlay = LayoutInflater.from(this).inflate(R.layout.overlay, null)

        // Set the time on the left and right clocks
        val left_clock = postureOverlay?.findViewById<TextView>(R.id.left_clock)
        val left_hinge_clock = postureOverlay?.findViewById<TextView>(R.id.left_hinge_clock)
        val left_battery = postureOverlay?.findViewById<TextView>(R.id.left_battery)
        val right_clock = postureOverlay?.findViewById<TextView>(R.id.right_clock)
        val right_hinge_clock = postureOverlay?.findViewById<TextView>(R.id.right_hinge_clock)
        val right_battery = postureOverlay?.findViewById<TextView>(R.id.right_battery)
        val battery_background = postureOverlay?.findViewById<View>(R.id.battery_background)
        val parent_view = postureOverlay?.findViewById<View>(R.id.parent_layout)

        var heightvar: Int = windowManager!!.currentWindowMetrics.bounds.height()
        var densityVar: Float = windowManager!!.currentWindowMetrics.density

        var heightToAnimateTo: Float = heightvar * (50f / 100f) * 2f

        Log.d("ETHANOL10", heightvar.toString())
        Log.d("ETHANOL10", (getBatteryPercentage(this) / 100f).toString())
        Log.d("ETHANOL10", heightToAnimateTo.toString())

//        battery_background?.animate()?.scaleY(heightToAnimateTo)?.setInterpolator(AccelerateDecelerateInterpolator())?.setDuration(2000);

        scaleView(battery_background!!, 0f, getBatteryPercentage(this) / 100f)

        if (left_clock != null && right_clock != null && left_battery != null && right_battery != null && right_hinge_clock != null && left_hinge_clock != null) {
            left_clock.text = displayText
            right_clock.text = displayText
            left_hinge_clock.text = """${displayText} | ${getBatteryEmoji(this)}${getBatteryPercentage(this)}%"""
            right_hinge_clock.text = """${displayText} | ${getBatteryEmoji(this)}${getBatteryPercentage(this)}%"""
            left_battery.text = """${dateText} | ${getBatteryEmoji(this)}${getBatteryPercentage(this)}%"""
            right_battery.text = """${dateText} | ${getBatteryEmoji(this)}${getBatteryPercentage(this)}%"""
        }
        if(parent_view != null){
            //Animate from 0 alpha
            parent_view.alpha = 0f
            animateParentOpacity(true)
        }


        // Define layout parameters for the overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        if(sleepAfterShowingOverlay){
            try {
                windowManager?.addView(postureOverlay, windowParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if(handler != null){
                try{
                    runnable?.let { handler?.removeCallbacks(it) }
                }catch (e: Exception){
                    e.printStackTrace()
                }
            }

            handler = Handler(Looper.getMainLooper())
            runnable = Runnable{
                hideOverlay(true)
                val pManager = this.getSystemService(POWER_SERVICE) as PowerManager
                try {
                    pManager.javaClass.getMethod(
                        "goToSleep",
                        *arrayOf<Class<*>?>(Long::class.javaPrimitiveType)
                    ).invoke(pManager, SystemClock.uptimeMillis())
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                } catch (e: NoSuchMethodException) {
                    e.printStackTrace()
                }
            }

            handler!!.postDelayed(runnable!!, 5000)
        }else{
            try {
                windowManager?.addView(postureOverlay, windowParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    fun hideOverlay(shouldAnimate: Boolean = true) {
        if(!shouldAnimate){
            try {
                if(postureOverlay != null){
                    windowManager?.removeView(postureOverlay)
                    postureOverlay = null
                }
            } catch (e: Exception) {
            }
            return
        }

        animateParentOpacity(false)
        if(fadeHandler != null){
            try{
                fadeRunnable?.let { fadeHandler?.removeCallbacks(it) }
            }catch (e: Exception){
                e.printStackTrace()
            }
        }

        fadeHandler = Handler(Looper.getMainLooper())
        fadeRunnable = Runnable{
            try {
                if(postureOverlay != null){
                    windowManager?.removeView(postureOverlay)
                    postureOverlay = null
                }
            } catch (e: Exception) {
            }
        }

        fadeHandler!!.postDelayed(fadeRunnable!!, 1000)

    }

    fun animateParentOpacity(show: Boolean){
        var alphaToTarget = 1f
        if(!show){
            alphaToTarget = 0f
        }

        if(postureOverlay != null){
            val parent_view = postureOverlay?.findViewById<View>(R.id.parent_layout)
            parent_view?.animate()?.alpha(alphaToTarget)?.setInterpolator(
                AccelerateDecelerateInterpolator()
            )?.setDuration(750);
        }
    }
}