package com.dome.recordscreen

import android.annotation.SuppressLint
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader

import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.core.content.getSystemService
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize


@Parcelize
data class ScreenRecordConfig(
    val resultCode: Int,
    val data: Intent
): Parcelable

class ScreenRecordService: Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private lateinit var  imageReader: ImageReader


    private var width = 100
    private var height = 200


    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())



    private val mediaProjectionManager by lazy {
        getSystemService<MediaProjectionManager>()
    }

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            releaseResources()
            stopService()

        }
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {


        when(intent?.action) {
            START_RECORDING -> {
                val notification = NotificationHelper.createNotification(applicationContext)
                NotificationHelper.createNotificationChannel(applicationContext)
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        1,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(
                        1,
                        notification
                    )
                }
                _isServiceRunning.value = true
                startRecording(intent)


            }
            STOP_RECORDING -> {
                stopRecording()
            }
            SNAP -> {
                captureScreenshot()
            }
        }
        return START_STICKY
    }

    private fun startRecording(intent: Intent) {
        val config = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                KEY_RECORDING_CONFIG,
                ScreenRecordConfig::class.java
            )
        } else {
            intent.getParcelableExtra(KEY_RECORDING_CONFIG,)
        }
        if(config == null) {
            return
        }

        mediaProjection = mediaProjectionManager?.getMediaProjection(
            config.resultCode,
            config.data
        )
        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        //startScreenCapture()
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = createVirtualDisplayOri()




    }

    private fun createVirtualDisplayOri(): VirtualDisplay? {

        return mediaProjection?.createVirtualDisplay(
            "Screen",
            width,
            height,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )
    }

    private fun stopRecording() {

        mediaProjection?.stop()

    }

    private fun stopService() {
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }





    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        serviceScope.coroutineContext.cancelChildren()
    }

    private fun releaseResources() {

        virtualDisplay?.release()
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection = null
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()


        const val START_RECORDING = "START_RECORDING"
        const val STOP_RECORDING = "STOP_RECORDING"
        const val KEY_RECORDING_CONFIG = "KEY_RECORDING_CONFIG"
        const val SNAP = "SNAP"
    }






    // Capture the screenshot from the ImageReader
    private fun captureScreenshot() {

        Log.i("domedome", "snap!")

        val image = imageReader.acquireLatestImage()

        if (image != null) {
            processImage(image)
            image.close()
        }
    }


    // Convert the captured image to Base64 and send it over the network
    private fun processImage(image: Image) {
        val planes = image.planes
        val buffer = planes[0].buffer // Assuming first plane is the correct one
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream) // Compress to JPEG format

        val byteArray = stream.toByteArray()
        bitmap.recycle()
        val base64String = Base64.encodeToString(
            byteArray,
            Base64.NO_WRAP
        )

        /*
        this is base64 jpeg string, you can open this in python colab with
        import io
        import base64
        from PIL import Image
        from IPython.display import display
        image_bytes = base64.b64decode(base64_string)
        image = Image.open(io.BytesIO(image_bytes))
        display(image)

        */
        Log.i("domedome", base64String)
    }











}