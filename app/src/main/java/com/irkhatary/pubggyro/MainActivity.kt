package com.irkhatary.pubggyro

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity(), SensorEventListener {
    private lateinit var web: WebView
    private lateinit var sensorManager: SensorManager
    private var gyro: Sensor? = null
    private var testing = false
    private var liveActive = false
    private var samples = 0
    private var firstNs = 0L
    private var lastNs = 0L
    private var maxGapMs = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("gyro_settings", Context.MODE_PRIVATE) }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST) {
            val msg = if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) "Shizuku permission granted" else "Shizuku permission denied"
            js("window.onShizukuResult && window.onShizukuResult('" + msg + "')")
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 8, 18)
        window.navigationBarColor = Color.rgb(5, 8, 18)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        web = WebView(this).apply {
            setBackgroundColor(Color.rgb(5, 8, 18))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "GyroNative")
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun js(script: String) {
        runOnUiThread { if (::web.isInitialized) web.evaluateJavascript(script, null) }
    }

    inner class Bridge {
        @JavascriptInterface fun hasGyro(): Boolean = gyro != null
        @JavascriptInterface fun getLanguage(): String = if (prefs.getBoolean("fa", true)) "fa" else "en"
        @JavascriptInterface fun setLanguage(value: String) { prefs.edit().putBoolean("fa", value == "fa").apply() }
        @JavascriptInterface fun startLive(): Boolean {
            val s = gyro ?: return false
            runOnUiThread {
                liveActive = true
                sensorManager.registerListener(this@MainActivity, s, SensorManager.SENSOR_DELAY_GAME)
                js("window.onGyroStatus && window.onGyroStatus(true)")
            }
            return true
        }
        @JavascriptInterface fun stopLive() {
            runOnUiThread {
                liveActive = false
                if (!testing) sensorManager.unregisterListener(this@MainActivity)
            }
        }
        @JavascriptInterface fun isShizukuRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        @JavascriptInterface fun hasShizukuPermission(): Boolean = try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }
        @JavascriptInterface fun getShizukuStatus(): String = if (isShizukuRunning()) {
            if (hasShizukuPermission()) "running granted" else "running permission denied"
        } else "not running"
        @JavascriptInterface fun openShizuku() {
            runOnUiThread {
                val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (i != null) startActivity(i)
                else Toast.makeText(this@MainActivity, "Shizuku not installed", Toast.LENGTH_LONG).show()
            }
        }
        @JavascriptInterface fun startTest() { runOnUiThread { startSensorTest() } }
        @JavascriptInterface fun stopTest() { runOnUiThread { stopSensorTest() } }
        @JavascriptInterface fun launchGame() { runOnUiThread { launchPubg() } }
        @JavascriptInterface fun requestShizuku() {
            runOnUiThread {
                try {
                    if (!Shizuku.pingBinder()) {
                        js("window.onShizukuResult && window.onShizukuResult('Shizuku not running')")
                    } else if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        js("window.onShizukuResult && window.onShizukuResult('Shizuku permission already granted')")
                    } else {
                        Shizuku.requestPermission(REQUEST)
                    }
                } catch (e: Exception) {
                    val msg = (e.message ?: "Shizuku error").replace("'", "")
                    js("window.onShizukuResult && window.onShizukuResult('" + msg + "')")
                }
            }
        }
    }

    private fun startSensorTest() {
        stopSensorTest()
        val s = gyro
        if (s == null) { js("window.onGyroStatus && window.onGyroStatus(false)"); return }
        testing = true
        samples = 0; firstNs = 0L; lastNs = 0L; maxGapMs = 0f
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        js("window.onTestStarted && window.onTestStarted()")
        handler.postDelayed({
            if (!testing) return@postDelayed
            sensorManager.unregisterListener(this)
            testing = false
            val seconds = if (samples > 1 && lastNs > firstNs) (lastNs - firstNs) / 1_000_000_000.0 else 0.0
            val hz = if (seconds > 0) samples / seconds else 0.0
            val hzText = String.format(java.util.Locale.US, "%.2f", hz)
            val gapText = String.format(java.util.Locale.US, "%.2f", maxGapMs)
            js("window.onTestFinished && window.onTestFinished(" + samples + "," + hzText + "," + gapText + ")")
        }, 5000L)
    }

    private fun stopSensorTest() {
        if (::sensorManager.isInitialized && !liveActive) sensorManager.unregisterListener(this)
        testing = false
        handler.removeCallbacksAndMessages(null)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        samples++
        if (firstNs == 0L) firstNs = event.timestamp
        if (lastNs != 0L) maxGapMs = maxOf(maxGapMs, (event.timestamp - lastNs) / 1_000_000f)
        lastNs = event.timestamp
        val x = String.format(java.util.Locale.US, "%.3f", event.values[0])
        val y = String.format(java.util.Locale.US, "%.3f", event.values[1])
        val z = String.format(java.util.Locale.US, "%.3f", event.values[2])
        js("window.onGyroData && window.onGyroData('" + x + "','" + y + "','" + z + "'," + samples + ")")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun launchPubg() {
        for (pkg in listOf("com.tencent.ig", "com.pubg.imobile", "com.tencent.iglite")) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) { startActivity(intent); return }
        }
        Toast.makeText(this, if (prefs.getBoolean("fa", true)) "پابجی موبایل پیدا نشد" else "PUBG Mobile not found", Toast.LENGTH_LONG).show()
    }

    override fun onPause() { super.onPause(); stopSensorTest(); liveActive = false; sensorManager.unregisterListener(this) }

    override fun onDestroy() {
        super.onDestroy()
        stopSensorTest()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        if (::web.isInitialized) web.destroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { js("window.handleBack && window.handleBack()") }

    companion object { private const val REQUEST = 7031 }
}
