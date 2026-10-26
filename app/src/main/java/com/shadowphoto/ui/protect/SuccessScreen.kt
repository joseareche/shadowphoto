package com.shadowphoto.ui.protect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shadowphoto.data.db.ProtectedImageEntity
import com.shadowphoto.ui.components.MutedText
import com.shadowphoto.ui.components.PrimaryButton
import com.shadowphoto.ui.components.PublishActions
import com.shadowphoto.ui.components.SecondaryButton
import com.shadowphoto.ui.theme.appBarColors
import com.shadowphoto.ui.vault.VaultViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuccessScreen(
    id: String,
    viewModel: VaultViewModel,
    onVault: () -> Unit,
    onAnother: () -> Unit,
    onHome: () -> Unit,
) {
    var entity by remember { mutableStateOf<ProtectedImageEntity?>(null) }
    var missing by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        viewModel.load(id) { loaded ->
            entity = loaded
            missing = loaded == null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(title = { Text("Listo") }, colors = appBarColors())
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Imagen protegida correctamente", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            MutedText(
                "La fotografía original quedó cifrada dentro de la portada y solo vive en la bóveda. " +
                    "Un visor PNG normal muestra únicamente esa portada.",
            )
            Spacer(Modifier.height(8.dp))
            MutedText(
                "Si olvidas la clave, no hay forma de recuperarla. " +
                    "La foto privada no se guardó en la galería.",
            )
            Spacer(Modifier.height(22.dp))
            val current = entity
            when {
                current == null && !missing -> {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(Modifier.height(16.dp))
                }
                missing -> {
                    MutedText("La portada protegida no está disponible para compartir.")
                    Spacer(Modifier.height(16.dp))
                }
                current != null -> {
                    PublishActions(
                        sealedPng = File(current.path),
                        displayName = "ShadowPhoto-${current.id.take(8)}.png",
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
            PrimaryButton(text = "Ir a la bóveda", onClick = onVault)
            Spacer(Modifier.height(10.dp))
            SecondaryButton(text = "Proteger otra", onClick = onAnother)
            Spacer(Modifier.height(10.dp))
            SecondaryButton(text = "Volver al inicio", onClick = onHome)
            Spacer(Modifier.height(24.dp))
        }
    }
}
