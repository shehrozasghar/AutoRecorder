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
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.autorecorder.config.AppConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var statusChip: TextView
    private lateinit var statusText: TextView
    private lateinit var serviceSwitch: SwitchMaterial
    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var shakeSwitch: SwitchMaterial
    private lateinit var shakeCount: TextInputEditText
    private lateinit var shakeThreshold: TextInputEditText
    private lateinit var volumeSwitch: SwitchMaterial
    private lateinit var customSwitch: SwitchMaterial
    private lateinit var maxMinutes: TextInputEditText
    private lateinit var learnGestureButton: MaterialButton
    private lateinit var clearGestureButton: MaterialButton
    private lateinit var permissionsButton: MaterialButton
    private lateinit var batteryButton: MaterialButton
    private lateinit var saveButton: MaterialButton

    private lateinit var sensorManager: SensorManager
    private var loadingUi = false

    private var templateListener: SensorEventListener? = null
    private var templateSamples = 0
    private val templateValues = mutableListOf<Float>()
    private val templateTarget = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        setContentView(R.layout.activity_main)
        bindViews()
        wireListeners()
        loadIntoUi()
    }

    private fun bindViews() {
        statusChip = findViewById(R.id.statusChip)
        statusText = findViewById(R.id.statusText)
        serviceSwitch = findViewById(R.id.serviceSwitch)
        modeGroup = findViewById(R.id.modeGroup)
        shakeSwitch = findViewById(R.id.shakeSwitch)
        shakeCount = findViewById(R.id.shakeCount)
        shakeThreshold = findViewById(R.id.shakeThreshold)
        volumeSwitch = findViewById(R.id.volumeSwitch)
        customSwitch = findViewById(R.id.customSwitch)
        maxMinutes = findViewById(R.id.maxMinutes)
        learnGestureButton = findViewById(R.id.learnGestureButton)
        clearGestureButton = findViewById(R.id.clearGestureButton)
        permissionsButton = findViewById(R.id.permissionsButton)
        batteryButton = findViewById(R.id.batteryButton)
        saveButton = findViewById(R.id.saveButton)
    }

    private fun wireListeners() {
        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            if (!loadingUi) onServiceToggle(checked)
        }

        modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked && !loadingUi) saveFromUi()
        }

        shakeSwitch.setOnCheckedChangeListener { _, _ -> if (!loadingUi) saveFromUi() }
        volumeSwitch.setOnCheckedChangeListener { _, _ -> if (!loadingUi) saveFromUi() }
        customSwitch.setOnCheckedChangeListener { _, _ -> if (!loadingUi) saveFromUi() }

        learnGestureButton.setOnClickListener { startTemplateCapture() }
        clearGestureButton.setOnClickListener { clearTemplate() }
        permissionsButton.setOnClickListener { requestAllPermissions() }
        batteryButton.setOnClickListener { requestIgnoreBatteryOptimizations() }
        saveButton.setOnClickListener {
            saveFromUi()
            showMessage("Settings saved. Restart the service if it is running.")
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val running = isServiceRunning()
        statusChip.text = if (running) {
            getString(R.string.status_active)
        } else {
            getString(R.string.status_inactive)
        }
        statusChip.background = ContextCompat.getDrawable(
            this,
            if (running) R.drawable.bg_status_active else R.drawable.bg_status_idle
        )
        statusChip.setTextColor(
            ContextCompat.getColor(
                this,
                if (running) R.color.status_active else R.color.status_idle
            )
        )
    }

    private fun onServiceToggle(checked: Boolean) {
        saveFromUi()
        if (checked) {
            if (!hasAllPermissions()) {
                serviceSwitch.isChecked = false
                showMessage("Grant permissions first.")
                requestAllPermissions()
                return
            }
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecorderService::class.java)
                    .setAction(RecorderService.ACTION_START)
            )
            showMessage("Service started - gestures active")
        } else {
            stopService(
                Intent(this, RecorderService::class.java)
                    .setAction(RecorderService.ACTION_STOP)
            )
            showMessage("Service stopped")
        }
        refreshStatus()
    }

    private fun showMessage(text: String) {
        statusText.text = text
        val root = findViewById<View>(android.R.id.content)
        try {
            Snackbar.make(root, text, Snackbar.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
    }

    private fun startTemplateCapture() {
        val acc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        templateSamples = 0
        templateValues.clear()
        showMessage("Recording gesture pattern - shake in your pattern...")
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
        showMessage("Gesture template saved - custom trigger active")
    }

    private fun clearTemplate() {
        getSharedPreferences("autorecorder_config", MODE_PRIVATE)
            .edit()
            .remove("templateData")
            .apply()
        showMessage("Gesture template cleared")
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
            recordMode = selectedMode(),
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

    private fun selectedMode(): Int = when (modeGroup.checkedButtonId) {
        R.id.modeVideoFront -> AppConfig.MODE_VIDEO_FRONT
        R.id.modeVideoBack -> AppConfig.MODE_VIDEO_BACK
        else -> AppConfig.MODE_AUDIO
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
        maxMinutes.setText(cfg.maxRecordingMinutes.toString())

        when (cfg.recordMode) {
            AppConfig.MODE_VIDEO_FRONT -> modeGroup.check(R.id.modeVideoFront)
            AppConfig.MODE_VIDEO_BACK -> modeGroup.check(R.id.modeVideoBack)
            else -> modeGroup.check(R.id.modeAudio)
        }
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
}