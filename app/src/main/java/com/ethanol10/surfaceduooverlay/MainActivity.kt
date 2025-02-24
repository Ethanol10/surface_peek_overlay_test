package com.ethanol10.surfaceduooverlay

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.ethanol10.surfaceduooverlay.ui.theme.SurfaceDuoOverlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
//        setContent {
//            SurfaceDuoOverlayTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }
//            }
//        }
        if (!Settings.canDrawOverlays(this)) {
            val intent2 = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent2.setData(Uri.parse("package:" + packageName))
            startActivity(intent2)
        }

        this.setShowWhenLocked(true)
        this.setTurnScreenOn(true)
        var intent : Intent = Intent(this, SurfaceDuoOverlayService::class.java)

        startService(intent)
    }

    override fun onDestroy(){
        super.onDestroy()

        val intent = Intent(this, SurfaceDuoOverlayService::class.java)

        stopService(intent)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SurfaceDuoOverlayTheme {
        Greeting("Android")
    }
}
