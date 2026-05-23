package com.kidsguard.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceWatchService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "kidsguard_channel"
        const val NOTIF_ID = 1001
        const val ABSENCE_DELAY_MS = 2000L   // pause after 2 s of no face
        const val RESUME_DELAY_MS  = 500L    // resume after 0.5 s of face back
        var isFacePresent = false             // read by accessibility service
        var shouldBePaused = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    private var faceAbsentSince = 0L
    private var faceBackSince   = 0L
    private var isVideoPaused   = false

    // ML Kit face detector (fast / lightweight)
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f)
            .build()
    )

    // ──────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Watching for your child's face…"))
        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        faceDetector.close()
    }

    // ──────────────────────────────────────────
    // Camera + Analysis
    // ──────────────────────────────────────────

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { proxy -> analyzeFrame(proxy) }
                }

            val selector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, selector, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, mainExecutor)
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeFrame(proxy: ImageProxy) {
        val mediaImage = proxy.image
        if (mediaImage == null) { proxy.close(); return }

        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                handleFaceResult(faces.isNotEmpty())
            }
            .addOnCompleteListener {
                proxy.close()
            }
    }

    // ──────────────────────────────────────────
    // Face-state logic
    // ──────────────────────────────────────────

    private fun handleFaceResult(faceFound: Boolean) {
        val now = System.currentTimeMillis()
        isFacePresent = faceFound

        if (faceFound) {
            faceAbsentSince = 0L

            if (isVideoPaused) {
                if (faceBackSince == 0L) faceBackSince = now
                if (now - faceBackSince >= RESUME_DELAY_MS) {
                    resumeYTKids()
                }
            } else {
                faceBackSince = 0L
            }

        } else {
            faceBackSince = 0L

            if (!isVideoPaused) {
                if (faceAbsentSince == 0L) faceAbsentSince = now
                if (now - faceAbsentSince >= ABSENCE_DELAY_MS) {
                    pauseYTKids()
                }
            }
        }
    }

    // ──────────────────────────────────────────
    // Media control — two methods for reliability
    // ──────────────────────────────────────────

    private fun pauseYTKids() {
        if (isVideoPaused) return
        isVideoPaused = true
        shouldBePaused = true
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        updateNotification("⏸ Paused — child looked away")
    }

    private fun resumeYTKids() {
        if (!isVideoPaused) return
        isVideoPaused = false
        shouldBePaused = false
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        updateNotification("▶ Watching again — resumed")
        // Reset message after a moment
        handler.postDelayed({
            updateNotification("Watching for your child's face…")
        }, 3000)
    }

    /**
     * Sends a media key event — works with YouTube Kids (and most media apps)
     * because they register as a media session.
     */
    private fun dispatchMediaKey(keyCode: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val up   = KeyEvent(KeyEvent.ACTION_UP,   keyCode)
        audio.dispatchMediaKeyEvent(down)
        audio.dispatchMediaKeyEvent(up)
    }

    // ──────────────────────────────────────────
    // Notification
    // ──────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "KidsGuard background monitor" }
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("👀 KidsGuard Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIF_ID, buildNotification(text))
    }
}
