package com.shadowphoto.ui.vault

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.shadowphoto.data.db.ProtectedImageEntity
import com.shadowphoto.data.db.VaultFlags
import com.shadowphoto.ui.components.ErrorText
import com.shadowphoto.ui.components.MutedText
import com.shadowphoto.ui.theme.appBarColors
import com.shadowphoto.ui.components.PrimaryButton
import com.shadowphoto.ui.components.PublishActions
import com.shadowphoto.ui.components.SecondaryButton
import com.shadowphoto.ui.components.formatBytes
import com.shadowphoto.ui.components.formatWhen
import com.shadowphoto.ui.decodeFilePreview
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    id: String,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    onDecrypt: () -> Unit,
) {
    var entity by remember { mutableStateOf<ProtectedImageEntity?>(null) }
    var missing by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        viewModel.load(id) { loaded ->
            entity = loaded
            missing = loaded == null
        }
    }
    LaunchedEffect(entity?.path) {
        val path = entity?.path ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { decodeFilePreview(path, 1200) }
    }

    val exportPng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val current = entity
        if (uri != null && current != null) {
            viewModel.export(current, uri, backup = false) { error ->
                messageIsError = error != null
                message = error ?: "PNG protegido exportado. Ábrelo como documento, no como foto recomprimida."
            }
        }
    }
    val exportShp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val current = entity
        if (uri != null && current != null) {
            viewModel.export(current, uri, backup = true) { error ->
                messageIsError = error != null
                message = error ?: "Respaldo .shp exportado. En otro teléfono, ábrelo con ShadowPhoto."
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = appBarColors(),
            )
        },
    ) { padding ->
        val current = entity
        if (current == null && !missing) {
            BoxLoading(Modifier.padding(padding))
        } else if (missing) {
            MutedText("Esa fotografía ya no está en la bóveda.", Modifier.padding(padding).padding(20.dp))
        }
        if (current != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Portada",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Text("Fotografía protegida", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                MutedText("La portada no lleva una marca visible. El candado solo existe dentro de ShadowPhoto.")
                Spacer(Modifier.height(16.dp))
                Fact("Fecha", formatWhen(current.createdAt))
                Fact("Formato", "SHP-1 · PNG con bloque shPd")
                Fact("Hash SHA-256", current.contentHash)
                Fact("Original cifrado", formatBytes(current.originalSize))
                Fact(
                    "Origen",
                    if (current.flags and VaultFlags.IMPORTED != 0) "Importada" else "Creada en este dispositivo",
                )
                message?.let { text ->
                    if (messageIsError) ErrorText(text) else MutedText(text, Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(18.dp))
                PrimaryButton(text = "Descifrar", onClick = onDecrypt)
                Spacer(Modifier.height(10.dp))
                PublishActions(
                    sealedPng = File(current.path),
                    displayName = "ShadowPhoto-${current.id.take(8)}.png",
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "Exportar PNG protegido", onClick = {
                    exportPng.launch("shadowphoto-${current.id.take(8)}.png")
                })
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "Exportar respaldo .shp", onClick = {
                    exportShp.launch("shadowphoto-${current.id.take(8)}.shp")
                })
                Spacer(Modifier.height(10.dp))
                SecondaryButton(text = "Eliminar de la bóveda", onClick = { confirmDelete = true })
                Spacer(Modifier.height(12.dp))
                MutedText(
                    "Para abrirlo en otro teléfono, exporta el .shp o el PNG como documento y ábrelo con ShadowPhoto.",
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar fotografía") },
            text = { Text("Se borra la portada y el bloque cifrado de este dispositivo. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(id)
                    onBack()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun BoxLoading(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = if (label.startsWith("Hash")) FontFamily.Monospace else FontFamily.Default,
        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
    )
}
