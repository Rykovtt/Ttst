package com.kartoteka.app.security

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream

/**
 * Тихий снимок фронтальной камерой — без превью и без звука затвора.
 * Используется на экране блокировки после неверного PIN-кода.
 */
object IntruderCamera {
    fun hasPermission(context: android.content.Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** [onDone] получает JPEG или null (нет разрешения, нет фронтальной камеры, ошибка). */
    fun <T> snap(owner: T, onDone: (ByteArray?) -> Unit) where T : LifecycleOwner, T : android.content.Context {
        if (!hasPermission(owner)) return onDone(null)
        val main = ContextCompat.getMainExecutor(owner)
        val future = ProcessCameraProvider.getInstance(owner)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener onDone(null)
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val bound = runCatching {
                if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) return@runCatching false
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, capture)
                true
            }.getOrDefault(false)
            if (!bound) return@addListener onDone(null)
            capture.takePicture(main, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val jpeg = runCatching { toJpeg(image) }.getOrNull()
                    image.close()
                    provider.unbind(capture)
                    onDone(jpeg)
                }

                override fun onError(exception: ImageCaptureException) {
                    provider.unbind(capture)
                    onDone(null)
                }
            })
        }, main)
    }

    private fun toJpeg(image: ImageProxy): ByteArray {
        var bmp = image.toBitmap()
        val longest = maxOf(bmp.width, bmp.height)
        val scale = if (longest > MAX) MAX.toFloat() / longest else 1f
        val m = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
            postScale(scale, scale)
        }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    private const val MAX = 1920
}
