package com.ethanol10.surfaceduooverlay

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

public class MainActivity : Application() {
    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            val intent2 = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent2.setData(Uri.parse("package:" + packageName))
            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent2)
        }

        Log.d("ETHANOL10", "bruh")

        var intent = Intent(this, SurfaceDuoOverlayService::class.java)

        startService(intent)
    }

    override fun onTerminate() {
        super.onTerminate()

        val intent = Intent(this, SurfaceDuoOverlayService::class.java)

        stopService(intent)
    }
}
