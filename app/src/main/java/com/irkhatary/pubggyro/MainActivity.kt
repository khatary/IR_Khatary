package com.irkhatary.pubggyro

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable
import kotlin.math.atan2
import kotlin.math.roundToInt

class MainActivity : Activity(), SensorEventListener {
    private val bg = Color.rgb(6, 9, 19)
    private val cyan = Color.rgb(0, 229, 255)
    private val purple = Color.rgb(143, 72, 255)
    private lateinit var sensorManager: SensorManager
    private var gyro: Sensor? = null
    private var accel: Sensor? = null
    private var activeSensor: Sensor? = null
    private var liveX = 0f
    private var liveY = 0f
    private var liveZ = 0f
    private var baseX = 0f
    private var baseY = 0f
    private var sensitivity = 1.0f
    private var smooth = 0.35f
    private var outputX = 0f
    private var outputY = 0f
    private var sensorReadout: TextView? = null
    private var reticle: TextView? = null
    private var sensorScreen = false
    private var emulatorScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        showSplash()
    }

    private fun panel(stroke: Int = Color.rgb(43, 57, 94)) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(23, 30, 52), Color.rgb(11, 15, 29))
    ).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), stroke) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun showSplash() {
        val splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(bg); setPadding(dp(28), dp(28), dp(28), dp(28))
        }
        splash.addView(TextView(this).apply {
            text = "◈"; textSize = 76f; setTextColor(cyan); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
        splash.addView(TextView(this).apply {
            text = "PUBG GYRO"; textSize = 31f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; letterSpacing = .08f
        })
        splash.addView(TextView(this).apply {
            text = "MOTION LAB  •  SENSOR CONTROL"; textSize = 11f
            setTextColor(cyan); gravity = Gravity.CENTER; letterSpacing = .16f
        })
        splash.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }, LinearLayout.LayoutParams(-1, dp(4)).apply { setMargins(dp(30), dp(36), dp(30), 0) })
        setContentView(splash)
        Handler(Looper.getMainLooper()).postDelayed({ showHome() }, 3000L)
    }

    private fun showHome() {
        stopSensor()
        sensorScreen = false; emulatorScreen = false
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        column.addView(TextView(this).apply {
            text = "PUBG GYRO"; textSize = 27f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = .03f
        })
        column.addView(TextView(this).apply {
            text = "MOTION SYSTEM  /  VERSION 1.0"; textSize = 10f
            setTextColor(cyan); letterSpacing = .12f
        })
        val hasGyro = gyro != null
        val status = cardText(
            if (hasGyro) "●  HARDWARE GYROSCOPE DETECTED" else "●  NO HARDWARE GYROSCOPE",
            if (hasGyro) cyan else Color.rgb(255, 174, 77), 13f
        )
        column.addView(status, marginParams(bottom = 14, top = 20))
        column.addView(cardText(
            "DEVICE SCAN\n\nGyroscope: ${if (hasGyro) "Available" else "Not detected"}\nAccelerometer: ${if (accel != null) "Available" else "Not detected"}\n\n${if (hasGyro) "Real gyro readings can be tested." else "Motion estimate uses the accelerometer and works only inside this app."}",
            Color.LTGRAY, 14f
        ), marginParams(bottom = 14))
        action(column, "⌁   SENSOR TEST", "Live X / Y / Z sensor readings", cyan) { showSensorTest() }
        action(column, "◉   GYRO EMULATOR", "Tilt preview • sensitivity • calibration", purple) { showEmulator() }
        action(column, "▶   LAUNCH PUBG MOBILE", "Open the game if installed", cyan) { launchPubg() }
        action(column, "⚙   SETTINGS & INFO", "App version and sensor limitations", purple) { showSettings() }
        column.addView(TextView(this).apply {
            text = "IR_KHATARY  •  PLAY SMART"; textSize = 10f
            letterSpacing = .14f; setTextColor(Color.rgb(92, 109, 151))
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(8))
        })
        scroll.addView(column)
        setContentView(scroll)
    }

    private fun marginParams(bottom: Int = 0, top: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(top), 0, dp(bottom)) }

    private fun cardText(text: String, color: Int, size: Float): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); setPadding(dp(16), dp(16), dp(16), dp(16))
        background = panel(); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun action(parent: LinearLayout, title: String, subtitle: String, accent: Int, run: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            background = panel(accent); isClickable = true; isFocusable = true
            setOnClickListener { run() }
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 17f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = subtitle; textSize = 12f; setTextColor(Color.rgb(153, 166, 201)); setPadding(0, dp(5), 0, 0)
        })
        parent.addView(box, marginParams(bottom = 12, top = 2))
    }

    private fun makePage(title: String): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(18)); setBackgroundColor(bg)
        }
        layout.addView(TextView(this).apply {
            text = title; textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(TextView(this).apply {
            text = "PUBG GYRO  /  SENSOR LAB"; textSize = 10f; setTextColor(cyan); letterSpacing = .12f
            setPadding(0, dp(4), 0, dp(18))
        })
        return layout
    }

    private fun addBack(layout: LinearLayout) {
        val b = Button(this).apply { text = "‹   BACK TO HOME"; setOnClickListener { showHome() } }
        layout.addView(b, marginParams(top = 14))
    }

    private fun showSensorTest() {
        stopSensor()
        sensorScreen = true; emulatorScreen = false
        val layout = makePage("SENSOR TEST")
        val scroller = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val list = TextView(this).apply {
            text = "GYROSCOPE: ${if (gyro != null) "AVAILABLE" else "NOT PRESENT"}\nACCELEROMETER: ${if (accel != null) "AVAILABLE" else "NOT PRESENT"}"
            textSize = 14f; setTextColor(if (gyro != null) cyan else Color.rgb(255, 174, 77))
            setPadding(dp(16), dp(16), dp(16), dp(16)); background = panel()
        }
        content.addView(list, marginParams(bottom = 12))
        sensorReadout = TextView(this).apply {
            text = "Move your phone to see live values.\n\nX: 0.000\nY: 0.000\nZ: 0.000"
            textSize = 17f; setTextColor(Color.WHITE); setPadding(dp(18), dp(22), dp(18), dp(22)); background = panel(cyan)
        }
        content.addView(sensorReadout, marginParams(bottom = 12))
        content.addView(cardText("Readings are from the physical sensor selected below. Accelerometer values are not gyro values.", Color.LTGRAY, 12f))
        val choose = Button(this).apply {
            text = if (gyro != null) "USE REAL GYROSCOPE" else "USE ACCELEROMETER"
            setOnClickListener { startLiveSensor() }
        }
        content.addView(choose, marginParams(top = 12))
        scroller.addView(content)
        layout.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        addBack(layout); setContentView(layout)
        startLiveSensor()
    }

    private fun startLiveSensor() {
        val sensor = gyro ?: accel
        activeSensor = sensor
        if (sensor == null) {
            sensorReadout?.text = "No motion sensor available."
            return
        }
        sensorManager.unregisterListener(this)
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun showEmulator() {
        stopSensor()
        sensorScreen = false; emulatorScreen = true
        outputX = 0f; outputY = 0f
        val layout = makePage("GYRO EMULATOR")
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(cardText(
            if (gyro != null) "Real gyroscope detected. This preview uses live gyro data." else "NO HARDWARE GYRO\nTilt estimation from accelerometer. Preview only; PUBG cannot receive a virtual system gyro.",
            if (gyro != null) cyan else Color.rgb(255, 174, 77), 13f
        ), marginParams(bottom = 12))
        val stage = FrameLayout(this).apply { background = panel(cyan) }
        val grid = TextView(this).apply {
            text = "＋"; textSize = 40f; setTextColor(Color.rgb(66, 88, 130)); gravity = Gravity.CENTER
        }
        stage.addView(grid, FrameLayout.LayoutParams(-1, -1))
        reticle = TextView(this).apply {
            text = "⊙"; textSize = 42f; setTextColor(cyan); gravity = Gravity.CENTER
        }
        stage.addView(reticle, FrameLayout.LayoutParams(dp(70), dp(70), Gravity.CENTER))
        content.addView(stage, LinearLayout.LayoutParams(-1, dp(230)).apply { setMargins(0, 0, 0, dp(12)) })
        val values = TextView(this).apply {
            text = "TILT X: 0.0°     TILT Y: 0.0°"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(dp(14), dp(12), dp(14), dp(12)); background = panel()
        }
        sensorReadout = values
        content.addView(values, marginParams(bottom = 10))
        val sensLabel = TextView(this).apply { text = "SENSITIVITY  ${sensitivity}x"; setTextColor(Color.WHITE); textSize = 13f }
        content.addView(sensLabel)
        val sens = SeekBar(this).apply {
            max = 300; progress = (sensitivity * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sensitivity = (progress.coerceAtLeast(20) / 100f)
                    sensLabel.text = "SENSITIVITY  ${"%.2f".format(sensitivity)}x"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        content.addView(sens)
        val smoothLabel = TextView(this).apply { text = "SMOOTHING"; setTextColor(Color.WHITE); textSize = 13f }
        content.addView(smoothLabel, marginParams(top = 8))
        val smoothSeek = SeekBar(this).apply {
            max = 90; progress = (smooth * 100).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    smooth = progress / 100f
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        content.addView(smoothSeek)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        buttons.addView(Button(this).apply {
            text = "CALIBRATE"; setOnClickListener { baseX = liveX; baseY = liveY; outputX = 0f; outputY = 0f }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(Button(this).apply {
            text = "RESET"; setOnClickListener { baseX = 0f; baseY = 0f; outputX = 0f; outputY = 0f }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(buttons)
        val scroll = ScrollView(this); scroll.addView(content)
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addBack(layout); setContentView(layout)
        val source = gyro ?: accel
        if (source != null) {
            activeSensor = source
            sensorManager.registerListener(this, source, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun showSettings() {
        stopSensor()
        val layout = makePage("SETTINGS & INFO")
        layout.addView(cardText(
            "APP: PUBG GYRO\nVERSION: 1.0\n\nSensor data stays on this device.\n\nA regular Android app cannot add a virtual system gyroscope to PUBG Mobile. The emulator preview operates inside PUBG GYRO only.\n\nNo root required for sensor testing.",
            Color.LTGRAY, 14f
        ), marginParams(bottom = 12))
        layout.addView(Button(this).apply { text = "RESET EMULATOR"; setOnClickListener {
            sensitivity = 1f; smooth = .35f; baseX = 0f; baseY = 0f
            Toast.makeText(this@MainActivity, "Emulator values reset", Toast.LENGTH_SHORT).show()
        } })
        addBack(layout); setContentView(layout)
    }

    private fun launchPubg() {
        val candidates = listOf("com.tencent.ig", "com.pubg.imobile", "com.tencent.iglite")
        for (pkg in candidates) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) { startActivity(intent); return }
        }
        Toast.makeText(this, "PUBG Mobile was not found.", Toast.LENGTH_LONG).show()
    }

    override fun onSensorChanged(event: SensorEvent) {
        liveX = event.values.getOrElse(0) { 0f }
        liveY = event.values.getOrElse(1) { 0f }
        liveZ = event.values.getOrElse(2) { 0f }
        if (sensorScreen) {
            val label = if (event.sensor.type == Sensor.TYPE_GYROSCOPE) "GYROSCOPE" else "ACCELEROMETER"
            sensorReadout?.text = "$label\n\nX: ${"%.4f".format(liveX)}\nY: ${"%.4f".format(liveY)}\nZ: ${"%.4f".format(liveZ)}"
        }
        if (emulatorScreen) {
            val tiltX: Float
            val tiltY: Float
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                tiltX = liveX * 12f
                tiltY = liveY * 12f
            } else {
                tiltX = Math.toDegrees(atan2(liveX.toDouble(), liveZ.toDouble())).toFloat()
                tiltY = Math.toDegrees(atan2(liveY.toDouble(), liveZ.toDouble())).toFloat()
            }
            val targetX = ((tiltX - baseX) * sensitivity).coerceIn(-40f, 40f)
            val targetY = ((tiltY - baseY) * sensitivity).coerceIn(-40f, 40f)
            outputX += (targetX - outputX) * (1f - smooth)
            outputY += (targetY - outputY) * (1f - smooth)
            sensorReadout?.text = "TILT X: ${"%.1f".format(outputX)}°     TILT Y: ${"%.1f".format(outputY)}°"
            reticle?.translationX = outputY * dp(3)
            reticle?.translationY = -outputX * dp(3)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun stopSensor() {
        sensorManager.unregisterListener(this)
        activeSensor = null
    }

    override fun onPause() {
        super.onPause()
        stopSensor()
    }

    override fun onBackPressed() {
        showHome()
    }
}
