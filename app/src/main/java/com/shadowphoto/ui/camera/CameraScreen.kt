package com.shadowphoto.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shadowphoto.camera.CameraController
import com.shadowphoto.domain.SessionStore
import com.shadowphoto.ui.components.MutedText
import com.shadowphoto.ui.components.SecureScreenEffect
import com.shadowphoto.ui.readBounded
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    camera: CameraController,
    session: SessionStore,
    onCaptured: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    SecureScreenEffect()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var asked by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        asked = true
        if (!allowed) {
            cameraError = "Permiso de cámara denegado. Actívalo en los ajustes o elige una foto de la galería."
        }
    }
    var busy by remember { mutableStateOf(false) }
    val mainExecutor = ContextCompat.getMainExecutor(context)
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            camera.unbind()
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!granted) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (asked) "Cámara no disponible" else "Se necesita la cámara",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                MutedText(
                    if (asked) {
                        "Sin el permiso no podemos capturar. La foto no iría a la galería. Vuelve atrás y elige una imagen que ya tengas."
                    } else {
                        "La captura se queda en la app. No se escribe en la galería del teléfono."
                    },
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, shape = CircleShape) {
                    Text(if (asked) "Volver a pedir permiso" else "Permitir cámara")
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { preview ->
                        camera.bind(lifecycleOwner, preview, imageCapture) { exception ->
                            cameraError = exception.message ?: "No se pudo abrir la cámara"
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    val file = camera.newCaptureFile()
                    imageCapture.takePicture(
                        camera.outputOptions(file),
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                try {
                                    val bytes = file.inputStream().use { readBounded(it) }
                                    secureRemove(file)
                                    mainExecutor.execute {
                                        session.setPrivate(bytes)
                                        onCaptured()
                                    }
                                } catch (e: Exception) {
                                    secureRemove(file)
                                    mainExecutor.execute {
                                        cameraError = e.message ?: "No se pudo leer la captura"
                                        busy = false
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                secureRemove(file)
                                mainExecutor.execute {
                                    cameraError = exception.message ?: "La captura falló"
                                    busy = false
                                }
                            }
                        },
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                shape = CircleShape,
                enabled = !busy,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Capturar")
                }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
        }
        cameraError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

private fun secureRemove(file: File) {
    try {
        if (file.exists() && file.isFile) {
            val length = file.length().toInt().coerceAtLeast(0)
            if (length > 0) {
                file.outputStream().use { it.write(ByteArray(length)) }
            }
            file.delete()
        }
    } catch (_: Exception) {
        file.delete()
    }
}
