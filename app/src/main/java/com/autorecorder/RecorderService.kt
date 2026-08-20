package com.autorecorder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.autorecorder.config.AppConfig
import com.autorecorder.recorder.AudioOnlyRecorder
import com.autorecorder.recorder.CameraRecorder
import com.autorecorder.trigger.GestureEngine
import java.util.concurrent.Executors

class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.autorecorder.action.START"
        const val ACTION_STOP = "com.autorecorder.action.STOP"
        const val ACTION_ARM_VOICE = "com.autorecorder.action.ARM_VOICE"
        const val ACTION_CONFIG_CHANGED = "com.autorecorder.action.CONFIG_CHANGED"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "autorecorder"
    }

    private var engine: GestureEngine? = null
    private var cameraRecorder: CameraRecorder? = null
    private var audioRecorder: AudioOnlyRecorder? = null
    private var recording = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val stopRecordingRunnable = Runnable { stopRecording() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                ensureStarted()
                when (intent?.action) {
                    ACTION_ARM_VOICE -> engine?.armVoice()
                    ACTION_CONFIG_CHANGED -> engine?.reloadConfig()
                }
            }
        }
        return START_STICKY
    }

    private fun ensureStarted() {
        if (engine == null) {
            val types = foregroundTypes()
            startForeground(NOTIFICATION_ID, buildNotification("Idle", "Gesture detection active"), types)
            engine = GestureEngine(this) { onGesture() }
            engine?.start()
        }
    }

    private fun onGesture() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (recording) return
        engine?.stopVoice()
        val config = AppConfig.load(this)
        recording = true

        if (config.keepScreenAwake) acquireWakeLock()
        updateNotification("Recording", "Gesture again to stop")
        mainHandler.removeCallbacks(stopRecordingRunnable)
        if (config.maxRecordingMinutes > 0) {
            mainHandler.postDelayed(
                stopRecordingRunnable,
                config.maxRecordingMinutes * 60_000L
            )
        }

        val cam = CameraRecorder(this, executor)
        cameraRecorder = cam
        cam.start(config.useBackCamera) { success, msg ->
            if (!success) {
                val audio = AudioOnlyRecorder(this)
                audio.start(config.maxRecordingMinutes)
                audioRecorder = audio
                updateNotification(
                    "Recording (audio only)",
                    if (msg.isNullOrBlank()) "Camera unavailable" else msg
                )
            }
        }
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        mainHandler.removeCallbacks(stopRecordingRunnable)
        cameraRecorder?.stop()
        cameraRecorder = null
        audioRecorder?.stop()
        audioRecorder = null
        releaseWakeLock()
        updateNotification("Idle", "Gesture detection active")
        if (AppConfig.load(this).voiceEnabled) engine?.armVoice()
    }

    private fun stopEverything() {
        stopRecording()
        mainHandler.removeCallbacksAndMessages(null)
        engine?.stop()
        engine = null
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun buildNotification(title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recorder service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun foregroundTypes(): Int {
        val mic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val cam = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        var types = 0
        if (mic) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (cam) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        return types
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AutoRecorder:Recording"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    override fun onDestroy() {
        stopEverything()
        executor.shutdown()
        super.onDestroy()
    }
}