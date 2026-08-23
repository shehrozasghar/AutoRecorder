package com.autorecorder

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.autorecorder.config.AppConfig
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var serviceSwitch: SwitchMaterial
    private lateinit var shakeSwitch: SwitchMaterial
    private lateinit var shakeCount: EditText
    private lateinit var shakeThreshold: EditText
    private lateinit var volumeSwitch: SwitchMaterial
    private lateinit var customSwitch: SwitchMaterial
    private lateinit var useBackSwitch: SwitchMaterial
    private lateinit var maxMinutes: EditText
    private lateinit var status: TextView

    private lateinit var sensorManager: SensorManager
    private var loadingUi = false

    private var templateListener: SensorEventListener? = null
    private var templateSamples = 0
    private val templateValues = mutableListOf<Float>()
    private val templateTarget = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        setContentView(buildUi())
        loadIntoUi()
    }

    override fun onResume() {
        super.onResume()
        loadIntoUi()
        status.text = if (isServiceRunning()) {
            "Service running - gestures active"
        } else {
            "Service stopped"
        }
    }

    private fun buildUi(): View {
        val root = ScrollView(this).apply { isFillViewport = true }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(col, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, dp(12))
        }
        col.addView(status)

        serviceSwitch = SwitchMaterial(this).apply {
            text = "Enable background service"
            setOnCheckedChangeListener { _, checked ->
                if (!loadingUi) onServiceToggle(checked)
            }
        }
        col.addView(serviceSwitch)

        col.addView(sectionTitle("Shake trigger"))
        shakeSwitch = SwitchMaterial(this).apply {
            text = "Enabled"
            setOnCheckedChangeListener { _, _ -> if (!loadingUi) saveFromUi() }
        }
        col.addView(shakeSwitch)
        col.addView(fieldLabel("Shakes required (default 3)"))
        shakeCount = numberField("3")
        col.addView(shakeCount)
        col.addView(fieldLabel("Sensitivity (higher = harder shake)"))
        shakeThreshold = numberField("18")
        col.addView(shakeThreshold)

        col.addView(sectionTitle("Volume key trigger"))
        volumeSwitch = SwitchMaterial(this).apply {
            text = "Enabled (press volume key once to toggle recording)"
            setOnCheckedChangeListener { _, _ -> if (!loadingUi) saveFromUi() }
        }
        col.addView(volumeSwitch)

        col.addView(sectionTitle("Custom gesture (learn your own)"))
        customSwitch = SwitchMaterial(this).apply {
            text = "Enabled"
            setOnCheckedChangeListener { _, _ -> if (!loadingUi) saveFromUi() }
        }
        col.addView(customSwitch)
        col.addView(button("Learn current gesture (shake in your pattern)") {
            startTemplateCapture()
        })
        col.addView(button("Clear learned gesture") {
            clearTemplate()
        })

        col.addView(sectionTitle("Recording"))
        useBackSwitch = SwitchMaterial(this).apply {
            text = "Use back camera"
            setOnCheckedChangeListener { _, _ -> if (!loadingUi) saveFromUi() }
        }
        col.addView(useBackSwitch)
        col.addView(fieldLabel("Max length (minutes, 0 = until stop)"))
        maxMinutes = numberField("5")
        col.addView(maxMinutes)

        col.addView(button("Grant permissions") { requestAllPermissions() })
        col.addView(button("Request battery optimization exemption") {
            requestIgnoreBatteryOptimizations()
        })
        col.addView(button("Save settings") {
            saveFromUi()
            status.text = "Saved. Restart the service if it is running."
        })

        return root
    }

    private fun sectionTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(20), 0, dp(4))
    }

    private fun fieldLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, dp(8), 0, dp(2))
    }

    private fun numberField(default: String): EditText = EditText(this).apply {
        setText(default)
        inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun onServiceToggle(checked: Boolean) {
        saveFromUi()
        if (checked) {
            if (!hasAllPermissions()) {
                serviceSwitch.isChecked = false
                status.text = "Grant permissions first."
                requestAllPermissions()
                return
            }
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecorderService::class.java)
                    .setAction(RecorderService.ACTION_START)
            )
            status.text = "Service started - gestures active"
        } else {
            stopService(
                Intent(this, RecorderService::class.java)
                    .setAction(RecorderService.ACTION_STOP)
            )
            status.text = "Service stopped"
        }
    }

    private fun startTemplateCapture() {
        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        templateSamples = 0
        templateValues.clear()
        status.text = "Recording gesture pattern ~4s - shake in your pattern..."
        templateListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                templateValues.add(sqrt(x * x + y * y + z * z))
                templateSamples++
                if (templateSamples >= templateTarget) finishTemplateCapture()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(
            templateListener,
            acc,
            SensorManager.SENSOR_DELAY_GAME
        )
    }

    private fun finishTemplateCapture() {
        sensorManager.unregisterListener(templateListener)
        templateListener = null
        val normalized = normalizeTemplate(templateValues.toFloatArray(), 48)
        getSharedPreferences("autorecorder_config", MODE_PRIVATE)
            .edit()
            .putString("templateData", normalized.joinToString(","))
            .apply()
        status.text = "Gesture template saved - custom trigger active"
    }

    private fun clearTemplate() {
        getSharedPreferences("autorecorder_config", MODE_PRIVATE)
            .edit()
            .remove("templateData")
            .apply()
        status.text = "Gesture template cleared"
    }

    private fun normalizeTemplate(a: FloatArray, n: Int): FloatArray {
        val resampled = if (a.size == n) a else {
            val out = FloatArray(n)
            for (i in 0 until n) {
                val idx = (i * (a.size - 1).toFloat()) / (n - 1)
                val lo = idx.toInt()
                val hi = minOf(lo + 1, a.size - 1)
                val frac = idx - lo
                out[i] = a[lo] * (1 - frac) + a[hi] * frac
            }
            out
        }
        val mean = resampled.average().toFloat()
        val variance = resampled.map { (it - mean) * (it - mean) }.average()
        val std = sqrt(variance.toFloat()).coerceAtLeast(1e-3f)
        return FloatArray(resampled.size) { (resampled[it] - mean) / std }
    }

    private fun saveFromUi() {
        val cfg = AppConfig(
            shakeEnabled = shakeSwitch.isChecked,
            shakeCount = shakeCount.text.toString().toIntOrNull() ?: 3,
            shakeThreshold = shakeThreshold.text.toString().toFloatOrNull() ?: 18f,
            volumeEnabled = volumeSwitch.isChecked,
            customEnabled = customSwitch.isChecked,
            useBackCamera = useBackSwitch.isChecked,
            maxRecordingMinutes = maxMinutes.text.toString().toIntOrNull() ?: 5
        )
        cfg.save(this)
        if (isServiceRunning()) {
            startService(
                Intent(this, RecorderService::class.java)
                    .setAction(RecorderService.ACTION_CONFIG_CHANGED)
            )
        }
    }

    private fun loadIntoUi() {
        loadingUi = true
        val cfg = AppConfig.load(this)
        serviceSwitch.isChecked = isServiceRunning()
        shakeSwitch.isChecked = cfg.shakeEnabled
        shakeCount.setText(cfg.shakeCount.toString())
        shakeThreshold.setText(cfg.shakeThreshold.toString())
        volumeSwitch.isChecked = cfg.volumeEnabled
        customSwitch.isChecked = cfg.customEnabled
        useBackSwitch.isChecked = cfg.useBackCamera
        maxMinutes.setText(cfg.maxRecordingMinutes.toString())
        loadingUi = false
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun hasAllPermissions(): Boolean {
        val mic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val cam = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val notif = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return mic && cam && notif
    }

    private fun isServiceRunning(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == RecorderService::class.java.name }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}