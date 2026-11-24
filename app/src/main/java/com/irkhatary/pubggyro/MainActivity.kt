package com.irkhatary.pubggyro

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import android.graphics.drawable.GradientDrawable

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 10, 20)
    private val cyan = Color.rgb(0, 229, 255)
    private lateinit var sensorManager: SensorManager
    private var gyro: Sensor? = null
    private var accel: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        showSplash()
    }

    private fun panel() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(22, 28, 48), Color.rgb(13, 17, 32))
    ).apply { cornerRadius = 30f; setStroke(1, Color.rgb(48, 62, 100)) }

    private fun showSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(bg)
            setPadding(28, 28, 28, 28)
        }
        splash.addView(TextView(this).apply {
            text = "◈"; textSize = 72f; setTextColor(cyan); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
        splash.addView(TextView(this).apply {
            text = "PUBG GYRO"; textSize = 30f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        splash.addView(TextView(this).apply {
            text = "SENSOR LAB  •  MOTION CONTROL"; textSize = 11f; setTextColor(cyan)
            gravity = Gravity.CENTER; letterSpacing = .18f
        })
        splash.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }, LinearLayout.LayoutParams(-1, 5).apply { setMargins(32, 38, 32, 0) })
        setContentView(splash)
        Handler(Looper.getMainLooper()).postDelayed({ showHome() }, 3000)
    }

    private fun showHome() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(20, 18, 20, 12)
        }
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(TextView(this).apply {
            text = "PUBG GYRO\nMOTION SYSTEM  /  v1.0"; textSize = 25f
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        column.addView(TextView(this).apply {
            text = if (gyro != null) "●  HARDWARE GYROSCOPE DETECTED" else "●  NO HARDWARE GYROSCOPE"
            textSize = 13f; setTextColor(if (gyro != null) cyan else Color.rgb(255, 170, 70))
            setPadding(18, 18, 18, 18); background = panel()
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 18, 0, 14) })
        column.addView(TextView(this).apply {
            text = "DEVICE SENSOR STATUS\n\nGyroscope: ${if (gyro != null) "Available" else "Not detected"}\nAccelerometer: ${if (accel != null) "Available" else "Not detected"}\n\nEmulation works inside this app. Android does not let a regular app add a system gyroscope to PUBG Mobile."
            textSize = 14f; setTextColor(Color.LTGRAY); setPadding(18, 18, 18, 18); background = panel()
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 14) })

        action(column, "⌁   SENSOR TEST", "Inspect device motion sensors") { sensorTest() }
        action(column, "◉   GYRO EMULATOR", "Motion simulation preview") { emulator() }
        action(column, "▶   OPEN PUBG MOBILE", "Launch game if installed") { launchPubg() }
        action(column, "⚙   SETTINGS", "Sensitivity and app options") { settings() }
        column.addView(TextView(this).apply {
            text = "IR_KHATARY  •  PLAY SMART"; textSize = 10f; letterSpacing = .15f
            setTextColor(Color.rgb(100, 115, 155)); gravity = Gravity.CENTER; setPadding(0, 22, 0, 8)
        })
        scroll.addView(column)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(content)
    }

    private fun action(parent: LinearLayout, title: String, desc: String, run: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16); background = panel()
            isClickable = true; isFocusable = true; setOnClickListener { run() }
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = desc; textSize = 12f; setTextColor(Color.rgb(145, 160, 195)); setPadding(0, 6, 0, 0)
        })
        parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 12) })
    }

    private fun page(title: String, message: String) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(22, 22, 22, 22); setBackgroundColor(bg)
        }
        layout.addView(TextView(this).apply {
            text = title; textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(TextView(this).apply {
            text = message; textSize = 16f; setTextColor(Color.LTGRAY); setPadding(0, 24, 0, 24)
        })
        layout.addView(Button(this).apply { text = "BACK TO HOME"; setOnClickListener { showHome() } })
        setContentView(layout)
    }

    private fun sensorTest() = page("SENSOR TEST",
        "Gyroscope: ${if (gyro != null) "PRESENT" else "NOT PRESENT"}\nAccelerometer: ${if (accel != null) "PRESENT" else "NOT PRESENT"}\n\nLive X/Y/Z graph is planned for the next development step.")

    private fun emulator() = page("GYRO EMULATOR",
        "Motion simulation preview\n\nThis mode demonstrates movement inside PUBG GYRO only. It cannot register a virtual hardware gyroscope for PUBG Mobile on a non-rooted Android device.")

    private fun settings() = page("SETTINGS",
        "PUBG GYRO v1.0\n\nSensitivity and calibration options will be added in the next development step.")

    private fun launchPubg() {
        val candidates = listOf("com.tencent.ig", "com.pubg.imobile", "com.tencent.iglite")
        for (pkg in candidates) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) { startActivity(intent); return }
        }
        Toast.makeText(this, "PUBG Mobile was not found on this device.", Toast.LENGTH_LONG).show()
    }
}
