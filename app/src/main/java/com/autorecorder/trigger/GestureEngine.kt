package com.autorecorder.trigger

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.autorecorder.config.AppConfig

/**
 * Owns all trigger sources and dispatches to the service on activation.
 * Gesture-only: shake, volume key, custom learned gesture.
 */
class GestureEngine(
    private val context: Context,
    private val onTrigger: (GestureType) -> Unit
) {
    enum class GestureType { SHAKE, VOLUME, CUSTOM }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val shakeDetector = ShakeDetector()
    private val customDetector = CustomGestureDetector { onTrigger(GestureType.CUSTOM) }
    private var volumeReceiver: VolumeKeyReceiver? = null
    private var lastVolumeTrigger = 0L
    private var sensorRegistered = false

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            shakeDetector.onAcceleration(
                event.values[0], event.values[1], event.values[2]
            )
            customDetector.onAcceleration(
                event.values[0], event.values[1], event.values[2]
            )
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        reloadConfig()
        volumeReceiver = VolumeKeyReceiver(context) { direction ->
            onVolumeKey(direction)
        }.also { it.start() }
    }

    fun stop() {
        if (sensorRegistered) {
            sensorManager.unregisterListener(sensorListener)
            sensorRegistered = false
        }
        volumeReceiver?.stop()
        volumeReceiver = null
    }

    fun reloadConfig() {
        val config = AppConfig.load(context)
        shakeDetector.threshold = config.shakeThreshold
        shakeDetector.countRequired = config.shakeCount
        shakeDetector.cooldownMs = config.shakeCooldownMs
        shakeDetector.onShake = if (config.shakeEnabled) {
            { onTrigger(GestureType.SHAKE) }
        } else {
            null
        }
        customDetector.load(context)

        val sensorNeeded =
            config.shakeEnabled || (config.customEnabled && customDetector.hasTemplate())
        if (sensorNeeded && accelerometer != null && !sensorRegistered) {
            sensorManager.registerListener(
                sensorListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                200_000
            )
            sensorRegistered = true
        } else if (!sensorNeeded && sensorRegistered) {
            sensorManager.unregisterListener(sensorListener)
            sensorRegistered = false
        }
    }

    private fun onVolumeKey(direction: Int) {
        if (direction == 0) return
        val config = AppConfig.load(context)
        if (!config.volumeEnabled) return
        val now = System.currentTimeMillis()
        if (now - lastVolumeTrigger > 2500) {
            lastVolumeTrigger = now
            onTrigger(GestureType.VOLUME)
        }
    }
}