package com.autorecorder.recorder

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import java.util.concurrent.ExecutorService

/**
 * CameraX-based video (+optional audio) recorder writing directly to
 * MediaStore. Runs without any preview surface (invisible to the user).
 *
 * onResult reports:
 *  - success=true: recording started / finalized cleanly
 *  - success=false, earlyFailure=true: failed immediately after start
 *    (e.g. mic busy) - caller may retry without audio
 *  - success=false, earlyFailure=false: failed mid-recording
 */
class CameraRecorder(
    private val context: Context,
    private val executor: ExecutorService
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var provider: ProcessCameraProvider? = null
    private var startedAtMs = 0L

    fun start(
        useBack: Boolean,
        withAudio: Boolean,
        onResult: (success: Boolean, earlyFailure: Boolean, message: String?) -> Unit
    ) {
        mainExecutor.execute {
            try {
                val future = ProcessCameraProvider.getInstance(context)
                future.addListener({
                    try {
                        val p = future.get()
                        provider = p

                        val lo = ServiceLifecycleOwner()
                        lo.start()
                        lifecycleOwner = lo

                        val recorder = Recorder.Builder()
                            .setQualitySelector(
                                QualitySelector.from(
                                    Quality.FHD,
                                    FallbackStrategy.higherQualityOrLowerThan(Quality.FHD)
                                )
                            )
                            .build()
                        val vc = VideoCapture.withOutput(recorder)

                        p.unbindAll()
                        // Let any previous camera/mic session fully release first.
                        mainHandler.postDelayed({
                            try {
                                bindAndRecord(p, lo, vc, useBack, withAudio, onResult)
                            } catch (e: Exception) {
                                cleanupNow()
                                onResult(false, true, e.message)
                            }
                        }, MIC_RELEASE_DELAY_MS)
                    } catch (e: Exception) {
                        cleanupNow()
                        onResult(false, true, e.message)
                    }
                }, mainExecutor)
            } catch (e: Exception) {
                onResult(false, true, e.message)
            }
        }
    }

    private fun bindAndRecord(
        p: ProcessCameraProvider,
        lo: ServiceLifecycleOwner,
        vc: VideoCapture<Recorder>,
        useBack: Boolean,
        withAudio: Boolean,
        onResult: (Boolean, Boolean, String?) -> Unit
    ) {
        val selector =
            if (useBack) CameraSelector.DEFAULT_BACK_CAMERA
            else CameraSelector.DEFAULT_FRONT_CAMERA

        p.unbindAll()
        p.bindToLifecycle(lo, selector, vc)
        videoCapture = vc

        val contentValues = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                "AR_${System.currentTimeMillis()}.mp4"
            )
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/AutoRecorder"
            )
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        startedAtMs = System.currentTimeMillis()
        var pending = vc.output.prepareRecording(context, outputOptions)
        if (withAudio) pending = pending.withAudioEnabled()
        val rec = pending.start(executor, Consumer<VideoRecordEvent> { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    val early =
                        System.currentTimeMillis() - startedAtMs < EARLY_FAILURE_MS
                    onResult(false, early, "error=${event.error}")
                } else {
                    onResult(true, false, null)
                }
                cleanupAfterFinalize()
            }
        })
        recording = rec
        onResult(true, false, null)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        mainExecutor.execute {
            try {
                recording?.stop()
            } catch (_: Exception) {
            }
        }
    }

    private fun cleanupAfterFinalize() {
        mainExecutor.execute { cleanupNow() }
    }

    private fun cleanupNow() {
        recording = null
        try {
            provider?.unbindAll()
        } catch (_: Exception) {
        }
        provider = null
        lifecycleOwner?.stop()
        lifecycleOwner = null
    }

    companion object {
        private const val MIC_RELEASE_DELAY_MS = 300L
        private const val EARLY_FAILURE_MS = 3000L
    }
}