package com.autorecorder.trigger

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.CountDownTimer
import com.autorecorder.config.AppConfig

/**
 * Owns all trigger sources and dispatches to the service on activation.
 */
class GestureEngine(
    private val context: Context,
    private val onTrigger: (GestureType) -> Unit
) {
    enum class GestureType { SHAKE, VOLUME, CUSTOM, VOICE }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val shakeDetector = ShakeDetector()
    private val customDetector = CustomGestureDetector { onTrigger(GestureType.CUSTOM) }
    private var volumeReceiver: VolumeKeyReceiver? = null
    private var voice: VoiceKeyword? = null
    private var voiceTimer: CountDownTimer? = null
    private var volumeTimes = mutableListOf<Long>()
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
        volumeReceiver = VolumeKeyReceiver(context) { onVolumeKey() }.also { it.start() }
        if (AppConfig.load(context).voiceEnabled) armVoice()
    }

    fun stop() {
        if (sensorRegistered) {
            sensorManager.unregisterListener(sensorListener)
            sensorRegistered = false
        }
        volumeReceiver?.stop()
        volumeReceiver = null
        stopVoice()
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

        if (voice == null && config.voiceEnabled) armVoice()
    }

    fun stopVoice() {
        voiceTimer?.cancel()
        voiceTimer = null
        voice?.stop()
        voice = null
    }

    fun armVoice() {
        val config = AppConfig.load(context)
        if (!config.voiceEnabled) return
        voiceTimer?.cancel()
        voice?.stop()
        val seconds = config.voiceWindowSeconds.coerceAtLeast(1)
        voice = VoiceKeyword(context).also {
            it.onKeyword = { onTrigger(GestureType.VOICE) }
            it.start()
        }
        voiceTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                voice?.stop()
                voice = null
            }
        }.start()
    }

    private fun onVolumeKey() {
        val config = AppConfig.load(context)
        if (!config.volumeEnabled) return
        val now = System.currentTimeMillis()
        volumeTimes.add(now)
        volumeTimes.removeAll { now - it > 2000 }
        if (volumeTimes.size >= 3) {
            volumeTimes.clear()
            armVoice()
        } else if (volumeTimes.size == 1 && now - lastVolumeTrigger > 2500) {
            lastVolumeTrigger = now
            onTrigger(GestureType.VOLUME)
        }
    }
}