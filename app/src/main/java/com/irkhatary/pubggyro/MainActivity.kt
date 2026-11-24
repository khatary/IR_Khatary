package com.irkhatary.pubggyro

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import kotlin.math.roundToInt
import rikka.shizuku.Shizuku

class MainActivity : Activity(), SensorEventListener {
    private val bg = Color.rgb(6, 9, 19)
    private val cyan = Color.rgb(0, 229, 255)
    private val green = Color.rgb(76, 255, 145)
    private lateinit var sm: SensorManager
    private var gyro: Sensor? = null
    private var accel: Sensor? = null
    private var logView: TextView? = null
    private var logScroll: ScrollView? = null
    private var logText = StringBuilder()
    private var liveText: TextView? = null
    private var events = 0
    private var firstNs = 0L
    private var lastNs = 0L
    private var maxGap = 0f
    private var repairing = false
    private val prefs by lazy { getSharedPreferences("gyro_settings", Context.MODE_PRIVATE) }
    private val fa get() = prefs.getBoolean("fa", true)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST) {
            if (result == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log(t("granted"))
                log(t("limitations"))
            } else log(t("denied"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        home()
    }

    private fun t(key: String): String {
        val faMap = mapOf(
            "subtitle" to "تشخیص و عیب‌یابی سنسور ژیروسکوپ",
            "found" to "ژیروسکوپ سخت‌افزاری موجود است",
            "absent" to "ژیروسکوپ سخت‌افزاری پیدا نشد",
            "repair" to "تعمیر و عیب‌یابی ژیرو",
            "sensor" to "تست زنده سنسور",
            "settings" to "تنظیمات / Settings",
            "launch" to "اجرای PUBG Mobile",
            "start" to "شروع عیب‌یابی و تلاش برای اصلاح",
            "shizuku" to "بررسی / دریافت مجوز Shizuku",
            "back" to "بازگشت",
            "language" to "زبان / Language",
            "fa" to "فارسی",
            "en" to "English",
            "note" to "برنامه سنسور را دوباره ثبت و تست می‌کند. تعمیر سخت‌افزار یا درایور همه گوشی‌ها از طریق اپ ممکن نیست.",
            "terminal" to "گزارش عملیات",
            "ready" to "آماده اجرا...",
            "begin" to "شروع عیب‌یابی...",
            "checking" to "بررسی وجود ژیروسکوپ...",
            "missing" to "ژیرو وجود ندارد؛ تعمیر نرم‌افزاری ممکن نیست.",
            "present" to "ژیرو پیدا شد.",
            "reregister" to "ثبت مجدد سنسور برای تلاش جهت بازیابی...",
            "sampling" to "تست داده سنسور به مدت 4 ثانیه...",
            "samples" to "نمونه دریافت شد؛ نرخ تقریبی",
            "gap" to "بیشترین فاصله نمونه‌ها",
            "nodata" to "داده‌ای دریافت نشد.",
            "system" to "خواندن گزارش سرویس سنسور (فقط خواندنی)...",
            "sysdone" to "گزارش سیستم دریافت شد.",
            "sysfail" to "خطا در گزارش سیستم:",
            "need" to "در حال درخواست مجوز از Shizuku...",
            "notrunning" to "Shizuku نصب یا فعال نیست. فقط تست سنسور انجام شد.",
            "granted" to "مجوز Shizuku داده شد.",
            "denied" to "مجوز Shizuku رد شد؛ دستور سیستمی اجرا نشد.",
            "finish" to "پایان بررسی؛ ژیرو را دوباره در پابجی آزمایش کنید.",
            "cal" to "کالیبراسیون داخل این برنامه فقط نقطه مرجع پیش‌نمایش است، نه کالیبراسیون سخت‌افزار.",
            "limitations" to "برنامه نمی‌تواند ژیرو مجازی به پابجی تزریق کند یا خرابی فیزیکی را تضمینی تعمیر کند.",
            "data" to "داده زنده ژیرو",
            "nodata2" to "هنوز داده‌ای دریافت نشده است."
        )
        val enMap = mapOf(
            "subtitle" to "Gyroscope sensor diagnostics",
            "found" to "Hardware gyroscope detected",
            "absent" to "No hardware gyroscope found",
            "repair" to "Gyro repair & diagnostics",
            "sensor" to "Live sensor test",
            "settings" to "Settings",
            "launch" to "Launch PUBG Mobile",
            "start" to "Start diagnosis & recovery attempt",
            "shizuku" to "Check / authorize Shizuku",
            "back" to "Back",
            "language" to "Language / زبان",
            "fa" to "فارسی",
            "en" to "English",
            "note" to "The app re-registers and tests the sensor. An app cannot repair hardware or drivers on every phone.",
            "terminal" to "Operation log",
            "ready" to "Ready...",
            "begin" to "Starting diagnostics...",
            "checking" to "Checking for gyroscope...",
            "missing" to "No gyro found; software repair is not possible.",
            "present" to "Gyroscope found.",
            "reregister" to "Re-registering sensor to attempt recovery...",
            "sampling" to "Sampling sensor for 4 seconds...",
            "samples" to "Samples received; approximate rate",
            "gap" to "Largest sample interval",
            "nodata" to "No sensor data received.",
            "system" to "Reading sensor service report (read-only)...",
            "sysdone" to "System report received.",
            "sysfail" to "System report error:",
            "need" to "Requesting Shizuku permission...",
            "notrunning" to "Shizuku is not installed or running. Sensor test only.",
            "granted" to "Shizuku permission granted.",
            "denied" to "Shizuku permission denied; no system command was run.",
            "finish" to "Done; test gyro again in PUBG.",
            "cal" to "Calibration in this app sets only the preview reference, not hardware calibration.",
            "limitations" to "The app cannot inject a virtual gyro into PUBG or guarantee repair of physical faults.",
            "data" to "Live gyro data",
            "nodata2" to "No sensor data received yet."
        )
        return (if (fa) faMap else enMap)[key] ?: key
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun card(stroke: Int = Color.rgb(43, 57, 94)) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(23, 30, 52), Color.rgb(11, 15, 29))
    ).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), stroke) }

    private fun base(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(20), dp(18), dp(18))
        setBackgroundColor(bg)
        addView(TextView(this@MainActivity).apply {
            text = title; textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        })
        addView(TextView(this@MainActivity).apply {
            text = "IR_KHATARY  •  SENSOR LAB"; textSize = 10f; setTextColor(cyan)
            setPadding(0, dp(5), 0, dp(16))
        })
    }

    private fun textCard(text: String, color: Int = Color.LTGRAY, size: Float = 13f) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        setPadding(dp(14), dp(14), dp(14), dp(14)); background = card()
    }

    private fun btn(parent: LinearLayout, caption: String, color: Int = cyan, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = caption; setTextColor(Color.WHITE); background = card(color)
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, dp(6), 0, dp(6)) })
    }

    private fun display(root: LinearLayout) {
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun home() {
        stopSensor()
        val root = base("PUBG GYRO")
        root.addView(textCard(t("subtitle"), cyan))
        root.addView(textCard(if (gyro != null) "●  " + t("found") else "●  " + t("absent"),
            if (gyro != null) green else Color.rgb(255, 174, 77)))
        btn(root, "⚡  " + t("repair"), green) { repairPage() }
        btn(root, "⌁  " + t("sensor"), cyan) { sensorPage() }
        btn(root, "▶  " + t("launch"), cyan) { launchGame() }
        btn(root, "⚙  " + t("settings"), Color.rgb(143, 72, 255)) { settingsPage() }
        root.addView(textCard(t("note"), Color.LTGRAY, 11f))
        display(root)
    }

    private fun repairPage() {
        stopSensor()
        logText = StringBuilder()
        val root = base(t("repair"))
        root.addView(textCard(t("note"), Color.LTGRAY, 11f))
        btn(root, "⚡  " + t("start"), green) { startRepair() }
        btn(root, "🔐  " + t("shizuku"), Color.rgb(143, 72, 255)) { askShizuku() }
        root.addView(textCard(t("terminal"), cyan, 12f))
        logScroll = ScrollView(this).apply { background = card(green) }
        logView = TextView(this).apply {
            text = "> " + t("ready") + "\n"; textSize = 12f; setTextColor(green)
            typeface = Typeface.MONOSPACE; setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        logScroll?.addView(logView)
        root.addView(logScroll, LinearLayout.LayoutParams(-1, dp(260)).apply { setMargins(0, dp(7), 0, dp(7)) })
        root.addView(textCard(t("limitations"), Color.LTGRAY, 11f))
        btn(root, t("back")) { home() }
        display(root)
    }

    private fun log(line: String) {
        runOnUiThread {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            logText.append("[" + time + "] " + line + "\n")
            if (logText.length > 10000) logText.delete(0, logText.length - 8000)
            logView?.text = logText.toString()
            logScroll?.post { logScroll?.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun startRepair() {
        if (repairing) return
        repairing = true
        events = 0; firstNs = 0L; lastNs = 0L; maxGap = 0f
        log(t("begin")); log(t("checking"))
        val sensor = gyro
        if (sensor == null) {
            log(t("missing")); repairing = false; return
        }
        log(t("present")); log(t("reregister"))
        sm.unregisterListener(this)
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        log(t("sampling"))
        Handler(Looper.getMainLooper()).postDelayed({
            sm.unregisterListener(this)
            if (events > 1 && lastNs > firstNs) {
                val seconds = (lastNs - firstNs) / 1_000_000_000f
                log(t("samples") + " " + "%.1f Hz / ".format(events / seconds) + events)
                log(t("gap") + " " + "%.1f ms".format(maxGap))
            } else log(t("nodata"))
            repairing = false
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) { log(t("granted")); log(t("limitations")); log(t("finish")) }
                else {
                    log(t("need"))
                    try { Shizuku.requestPermission(REQUEST) } catch (e: Exception) { log(e.message ?: "Shizuku error") }
                }
            } else {
                log(t("notrunning")); log(t("finish"))
            }
        }, 4000L)
    }

    private fun askShizuku() {
        try {
            if (!Shizuku.pingBinder()) { log(t("notrunning")); return }
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log(t("granted")); log(t("limitations"))
            } else {
                log(t("need")); Shizuku.requestPermission(REQUEST)
            }
        } catch (e: Exception) { log(e.message ?: "Shizuku error") }
    }

    private fun sensorPage() {
        stopSensor()
        val root = base(t("sensor"))
        root.addView(textCard(t("data"), cyan))
        val readout = textCard(t("nodata2"), Color.WHITE, 15f)
        root.addView(readout)
        root.addView(textCard(t("cal"), Color.LTGRAY, 11f))
        btn(root, t("back")) { home() }
        display(root)
        if (gyro == null) readout.text = t("absent")
        else {
            liveText = readout
            sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        events++
        if (firstNs == 0L) firstNs = event.timestamp
        if (lastNs != 0L) maxGap = maxOf(maxGap, (event.timestamp - lastNs) / 1_000_000f)
        lastNs = event.timestamp
        liveText?.text = t("data") + "\nX: " + "%.3f".format(event.values[0]) +
            "\nY: " + "%.3f".format(event.values[1]) + "\nZ: " + "%.3f".format(event.values[2]) +
            "\nSamples: " + events
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun settingsPage() {
        stopSensor()
        val root = base(t("settings"))
        btn(root, t("language") + ": " + t(if (fa) "fa" else "en")) {
            prefs.edit().putBoolean("fa", !fa).apply()
            settingsPage()
        }
        root.addView(textCard(t("limitations"), Color.LTGRAY, 12f))
        btn(root, t("back")) { home() }
        display(root)
    }

    private fun launchGame() {
        for (pkg in listOf("com.tencent.ig", "com.pubg.imobile", "com.tencent.iglite")) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) { startActivity(intent); return }
        }
        Toast.makeText(this, if (fa) "پابجی موبایل پیدا نشد" else "PUBG Mobile not found", Toast.LENGTH_LONG).show()
    }

    private fun stopSensor() {
        sm.unregisterListener(this)
        liveText = null
    }

    override fun onPause() { super.onPause(); stopSensor() }
    override fun onDestroy() {
        super.onDestroy()
        stopSensor()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }
    override fun onBackPressed() { home() }

    companion object { private const val REQUEST = 7031 }
}
