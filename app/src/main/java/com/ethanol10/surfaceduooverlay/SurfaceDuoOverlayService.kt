package com.ethanol10.surfaceduooverlay

import android.R.attr.value
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView


class SurfaceDuoOverlayService: Service() {
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

    private var activityContext: Context? = null

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    private fun createPostureOverlay() {
        val inflater = LayoutInflater.from(applicationContext)
        postureOverlay = inflater.inflate(R.layout.overlay, null)
    }

    override fun onCreate() {
        super.onCreate()
        batteryReceiver = BatteryReceiver()
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).also{intentFilter ->
            intentFilter.addAction("android.intent.action.ACTION_POWER_CONNECTED")
            intentFilter.addAction("android.intent.action.ACTION_POWER_DISCONNECTED")
        }
        registerReceiver(batteryReceiver, filter)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?;
        windowParams = WindowManager.LayoutParams()
        windowParams?.width = WindowManager.LayoutParams.MATCH_PARENT
        windowParams?.height = WindowManager.LayoutParams.MATCH_PARENT
        windowParams?.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        windowParams?.format = PixelFormat.TRANSLUCENT
        windowParams?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_FULLSCREEN

//        windowParams?.gravity = Gravity.BOTTOM


        //Register receiver for determining screen off and if user is present
        lockScreenReceiver = LockScreenStateReceiver()
        val lockFilter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        lockFilter.addAction(Intent.ACTION_USER_PRESENT)

        registerReceiver(lockScreenReceiver, lockFilter)


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        if(isShowing){
            windowManager?.removeViewImmediate(textView)
            isShowing = false
        }

        unregisterReceiver(batteryReceiver)
        unregisterReceiver(lockScreenReceiver)
    }

    inner class LockScreenStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if(!isShowing){
//                showOverlay(false)
                isShowing = true;
            }
//              if (intent.action == Intent.ACTION_SCREEN_OFF) {
//                //if screen is turn off show the textview
//                if (!isShowing) {
//                    windowManager?.addView(textView, windowParams)
//                    showOverlay(false)
//                    isShowing = true
//                }
//            } else if (intent.action == Intent.ACTION_USER_PRESENT) {
//                //Handle resuming events if user is present/screen is unlocked remove the textview immediately
//                if (isShowing) {
//                    windowManager?.removeViewImmediate(textView)
//                    hideOverlay()
//                    isShowing = false
//                }
//            }
//            }
        }
    }

    fun StartOverlayActivity(sleepOnAction: Boolean){
        val myIntent: Intent = Intent(
            this,
            OverlayActivity::class.java
        )
        myIntent.putExtra("sleepOnAction", sleepOnAction)
        myIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        this.startActivity(myIntent)
    }

    inner class BatteryReceiver : BroadcastReceiver() {
        var batteryLevel : Int = 0
        var batteryStatus : Int = -1

        override fun onReceive(context: Context?, intent: Intent?) {
            // Check if the action is the battery status change

            if(Intent.ACTION_POWER_CONNECTED == intent?.action || Intent.ACTION_POWER_DISCONNECTED == intent?.action){
                //Start activity

                StartOverlayActivity(true)
            }


            if (Intent.ACTION_BATTERY_CHANGED == intent?.action) {

                batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)  // Get battery level
                batteryStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)  // Get battery status

                if(!isShowing){
                    //Start activity with kill timer.
                    StartOverlayActivity(false)
                    isShowing = true;
                }
                // Show battery level as a Toast message
//            Toast.makeText(context, "Battery Level: $level%", Toast.LENGTH_SHORT).show()

//            // Handle different battery statuses (charging, full, discharging, etc.)
//            when (status) {
//                BatteryManager.BATTERY_STATUS_CHARGING -> {
//                    Toast.makeText(context, "Charging", Toast.LENGTH_SHORT).show()
//                }
//                BatteryManager.BATTERY_STATUS_DISCHARGING -> {
//                    Toast.makeText(context, "Discharging", Toast.LENGTH_SHORT).show()
//                }
//                BatteryManager.BATTERY_STATUS_FULL -> {
//                    Toast.makeText(context, "Battery Full", Toast.LENGTH_SHORT).show()
//                }
//                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> {
//                    Toast.makeText(context, "Not Charging", Toast.LENGTH_SHORT).show()
//                }
//            }
            }
        }
    }
}