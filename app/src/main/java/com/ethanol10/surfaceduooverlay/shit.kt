package com.ethanol10.surfaceduooverlay

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log

public class shit : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.canDrawOverlays(this)) {
            val intent2 = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent2.setData(Uri.parse("package:" + packageName))
            startActivity(intent2)
        }

        Log.d("ETHANOL10", "bruh")

        var intent = Intent(this, SurfaceDuoOverlayService::class.java)

        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()

        val intent = Intent(this, SurfaceDuoOverlayService::class.java)

        stopService(intent)
    }
}