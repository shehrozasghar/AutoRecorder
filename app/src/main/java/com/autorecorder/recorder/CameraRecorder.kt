package com.autorecorder.recorder

import android.content.ContentValues
import android.content.Context
import android.os.Environment
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
 * CameraX-based video + audio recorder writing directly to MediaStore.
 * Runs without any preview surface (invisible to the user).
 */
class CameraRecorder(
    private val context: Context,
    private val executor: ExecutorService
) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null
    private var provider: ProcessCameraProvider? = null

    fun start(useBack: Boolean, onResult: (Boolean, String?) -> Unit) {
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

                        val rec = vc.output
                            .prepareRecording(context, outputOptions)
                            .withAudioEnabled()
                            .start(executor, Consumer<VideoRecordEvent> { event ->
                                if (event is VideoRecordEvent.Finalize) {
                                    val ok = !event.hasError()
                                    onResult(ok, if (ok) null else "error=${event.error}")
                                    cleanupAfterFinalize()
                                }
                            })
                        recording = rec
                        onResult(true, null)
                    } catch (e: Exception) {
                        onResult(false, e.message)
                    }
                }, mainExecutor)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }

    fun stop() {
        mainExecutor.execute {
            try {
                recording?.stop()
            } catch (_: Exception) {
            }
        }
    }

    private fun cleanupAfterFinalize() {
        mainExecutor.execute {
            recording = null
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
            provider = null
            lifecycleOwner?.stop()
            lifecycleOwner = null
        }
    }
}