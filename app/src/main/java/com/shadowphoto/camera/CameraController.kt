package com.shadowphoto.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.shadowphoto.data.files.PrivateFileStore
import java.io.File

/**
 * Cámara hacia almacenamiento de caché de la app. La captura no pasa por la galería.
 */
class CameraController(
    context: Context,
    private val files: PrivateFileStore,
) {
    private val appContext = context.applicationContext
    private var provider: ProcessCameraProvider? = null

    fun newCaptureFile(): File = files.newCaptureFile()

    fun outputOptions(file: File): ImageCapture.OutputFileOptions =
        ImageCapture.OutputFileOptions.Builder(file).build()

    fun bind(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        imageCapture: ImageCapture,
        onError: (Exception) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(appContext)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                cameraProvider.unbindAll()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            } catch (e: Exception) {
                onError(e)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun unbind() {
        provider?.unbindAll()
    }
}
