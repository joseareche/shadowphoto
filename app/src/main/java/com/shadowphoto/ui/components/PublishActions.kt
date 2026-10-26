package com.shadowphoto.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shadowphoto.share.ProtectedImagePublisher
import com.shadowphoto.share.ShareOutcome
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Comparte o copia solo el PNG sellado (portada + shPd).
 * La foto descifrada no sale de la sesión privada.
 */
@Composable
fun PublishActions(
    sealedPng: File,
    displayName: String,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val host = remember(context) { context.findActivity() ?: context }
    val publisher = remember(host) { ProtectedImagePublisher(host) }
    val scope = rememberCoroutineScope()
    var whatsApp by remember { mutableStateOf(publisher.whatsAppInstalled()) }
    var photos by remember { mutableStateOf(publisher.googlePhotosInstalled()) }
    var busy by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<ShareOutcome?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, publisher) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                whatsApp = publisher.whatsAppInstalled()
                photos = publisher.googlePhotosInstalled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val ready = enabled && !busy
    SecondaryButton(text = "Compartir", enabled = ready, onClick = {
        scope.launch {
            outcome = withContext(Dispatchers.Main) { publisher.shareWithSheet(sealedPng) }
        }
    })
    if (whatsApp) {
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = "Enviar por WhatsApp", enabled = ready, onClick = {
            scope.launch {
                outcome = withContext(Dispatchers.Main) { publisher.shareWithWhatsApp(sealedPng) }
            }
        })
    }
    Spacer(Modifier.height(10.dp))
    SecondaryButton(
        text = if (busy) "Guardando en el álbum…" else "Guardar en el álbum ShadowPhoto",
        enabled = ready,
        onClick = {
            scope.launch {
                busy = true
                outcome = null
                val result = withContext(Dispatchers.IO) {
                    publisher.saveToGalleryAlbum(sealedPng, displayName)
                }
                outcome = result
                busy = false
            }
        },
    )
    if (photos) {
        Spacer(Modifier.height(10.dp))
        SecondaryButton(text = "Enviar a Google Fotos", enabled = ready, onClick = {
            scope.launch {
                outcome = withContext(Dispatchers.Main) { publisher.sendToGooglePhotos(sealedPng) }
            }
        })
    }
    outcome?.let { result ->
        if (result.error) {
            ErrorText(result.text)
        } else {
            Spacer(Modifier.height(8.dp))
            MutedText(result.text)
        }
    }
    Spacer(Modifier.height(8.dp))
    MutedText(
        "Se comparte la portada ya cifrada. La foto privada no sale de la bóveda. " +
            "El atajo de WhatsApp la envía como documento para no recomprimirla. " +
            "Si otra app la trata como foto, puede borrar el bloque cifrado. " +
            "El álbum ShadowPhoto es una carpeta normal de Imágenes: Google Fotos puede subirla si la copia de seguridad está activada.",
    )
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
