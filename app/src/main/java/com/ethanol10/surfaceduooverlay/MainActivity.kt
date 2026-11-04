package com.ethanol10.surfaceduooverlay

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Prototype harness: buttons stand in for your hinge/fold trigger.
 *
 * Manifest needs:
 *   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
 * Gradle needs:
 *   implementation("androidx.constraintlayout:constraintlayout:2.2.0")
 */
class MainActivity : AppCompatActivity() {

    /** Fake notification source so you can test the icon grid and the count. */
    private class FakeSource(private val icon: () -> Drawable) : PeekNotificationSource {
        private val listeners = mutableSetOf<(List<PeekNotification>) -> Unit>()
        private var items = emptyList<PeekNotification>()

        fun setCount(n: Int) {
            items = List(n) { PeekNotification("fake-$it", icon(), count = it + 1) }
            listeners.toList().forEach { it(items) }
        }

        override fun current() = items
        override fun addListener(listener: (List<PeekNotification>) -> Unit) { listeners += listener }
        override fun removeListener(listener: (List<PeekNotification>) -> Unit) { listeners -= listener }
    }

    private lateinit var controller: PeekController
    private lateinit var source: FakeSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        source = FakeSource {
            ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_email)!!.mutate()
        }
        controller = PeekController(
            context = applicationContext,
            windowManager = getSystemService(WindowManager::class.java),
            notifications = source,
            // hingeWidthPx omitted on purpose: defaults to PeekController.DEFAULT_HINGE_GAP_PX (66 px)
        )

        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(button("Show peek (5 s)") { ensureOverlayPermission { controller.show(5_000) } })
            addView(button("Show peek (stay)") { ensureOverlayPermission { controller.show() } })
            addView(button("Hide peek") { controller.hide() })
            // N boxes; box i shows the number i (1..N), so you can see per-box counts
            addView(button("0 boxes") { source.setCount(0) })
            addView(button("3 boxes") { source.setCount(3) })
            addView(button("6 boxes") { source.setCount(6) })
            addView(button("9 boxes") { source.setCount(9) })
        })
    }

    private fun ensureOverlayPermission(then: () -> Unit) {
        if (Settings.canDrawOverlays(this)) then()
        else startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    override fun onDestroy() {
        controller.hide()
        super.onDestroy()
    }
}