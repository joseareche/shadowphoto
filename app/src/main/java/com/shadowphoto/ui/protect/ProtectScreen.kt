package com.shadowphoto.ui.protect

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowphoto.ui.components.ErrorText
import com.shadowphoto.ui.components.MutedText
import com.shadowphoto.ui.components.PasswordField
import com.shadowphoto.ui.components.PrimaryButton
import com.shadowphoto.ui.components.SecondaryButton
import com.shadowphoto.ui.components.SectionLabel
import com.shadowphoto.ui.components.SecureScreenEffect
import com.shadowphoto.ui.theme.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectScreen(
    viewModel: ProtectViewModel,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onSealed: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPreviews()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.savedId) {
        val id = state.savedId ?: return@LaunchedEffect
        viewModel.consumeSaved()
        onSealed(id)
    }
    SecureScreenEffect()
    val pickPrivate = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPrivate(uri)
    }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importCover(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Proteger imagen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = appBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (!state.saved) {
                StepRow(hasPrivate = state.hasPrivate, hasCover = state.hasCover, hasKey = state.password.length >= 8)
                Spacer(Modifier.height(16.dp))
                SectionLabel("Imagen seleccionada")
                Spacer(Modifier.height(8.dp))
                PreviewFrame(state.privatePreview, "La fotografía privada solo se muestra aquí")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Tomar foto",
                        onClick = onCamera,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                    SecondaryButton(
                        text = "Galería",
                        onClick = {
                            pickPrivate.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                }
                Spacer(Modifier.height(22.dp))
                SectionLabel("Portada alternativa")
                Spacer(Modifier.height(4.dp))
                MutedText("Es lo único que verá otra persona al abrir el PNG. No se mezcla con la foto privada.")
                Spacer(Modifier.height(8.dp))
                PreviewFrame(state.coverPreview, "Aún no hay portada")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(
                        text = "Elegir portada",
                        onClick = {
                            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                    SecondaryButton(
                        text = "Generar",
                        onClick = viewModel::generateCover,
                        modifier = Modifier.weight(1f),
                        enabled = !state.busy,
                    )
                }
                Spacer(Modifier.height(22.dp))
                SectionLabel("Contraseña de protección")
                Spacer(Modifier.height(8.dp))
                PasswordField(
                    label = "Clave",
                    value = state.password,
                    onValueChange = viewModel::onPassword,
                )
                Spacer(Modifier.height(10.dp))
                PasswordField(
                    label = "Repite la clave",
                    value = state.confirm,
                    onValueChange = viewModel::onConfirm,
                )
                Spacer(Modifier.height(8.dp))
                MutedText("Argon2id deriva la clave en el teléfono. Si la olvidas, la foto no se puede recuperar.")
                state.error?.let { ErrorText(it) }
                Spacer(Modifier.height(16.dp))
                if (state.busy) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(28.dp), strokeWidth = 2.dp)
                        Text(
                            "Cifrando la fotografía…",
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    PrimaryButton(
                        text = "Cifrar fotografía",
                        onClick = viewModel::protect,
                        enabled = state.hasPrivate && state.hasCover,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PreviewFrame(bitmap: Bitmap?, empty: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = empty,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            MutedText(empty, modifier = Modifier.padding(20.dp))
        }
    }
}

@Composable
private fun StepRow(hasPrivate: Boolean, hasCover: Boolean, hasKey: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepChip("1 · Foto", hasPrivate, Modifier.weight(1f))
        StepChip("2 · Portada", hasCover, Modifier.weight(1f))
        StepChip("3 · Clave", hasKey, Modifier.weight(1f))
    }
}

@Composable
private fun StepChip(label: String, done: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (done) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
