package com.shadowphoto.ui.decrypt

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowphoto.ui.components.ErrorText
import com.shadowphoto.ui.components.MutedText
import com.shadowphoto.ui.components.PasswordField
import com.shadowphoto.ui.components.PrimaryButton
import com.shadowphoto.ui.components.SecondaryButton
import com.shadowphoto.ui.components.SecureScreenEffect
import com.shadowphoto.ui.theme.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecryptScreen(
    viewModel: DecryptViewModel,
    canImport: Boolean,
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    onImported: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var notice by remember { mutableStateOf<String?>(null) }
    SecureScreenEffect()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Desbloquear") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = appBarColors(),
            )
        },
    ) { padding ->
        if (!state.ready && !state.missing && !state.busy && state.cover == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    MutedText("Leyendo el contenedor…")
                }
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("Fotografía protegida", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            MutedText("Introduce tu clave para recuperar el contenido original.")
            Spacer(Modifier.height(16.dp))
            state.cover?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Portada",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.5f)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(16.dp))
            }
            if (canImport) {
                MutedText("Este archivo viene de fuera. Puedes guardarlo en la bóveda y descifrarlo con la misma clave.")
                Spacer(Modifier.height(12.dp))
            }
            if (state.missing) {
                MutedText("No se encontró el archivo protegido.")
            } else {
                PasswordField(
                    label = "Clave de descifrado",
                    value = state.password,
                    onValueChange = viewModel::onPassword,
                )
                state.error?.let { ErrorText(it) }
                Spacer(Modifier.height(16.dp))
                if (state.busy) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        MutedText("Comprobando la clave…")
                    }
                } else {
                    PrimaryButton(
                        text = "Ver fotografía original",
                        onClick = { viewModel.unlock(onUnlocked) },
                        enabled = state.ready,
                    )
                }
                if (canImport) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(text = "Guardar en la bóveda", onClick = {
                        viewModel.importExternal { error ->
                            if (error == null) onImported() else notice = error
                        }
                    })
                }
                notice?.let { ErrorText(it) }
                Spacer(Modifier.height(18.dp))
                MutedText("Descifrado local y seguro. Si el teléfono tiene biometría, te la pediremos antes de mostrar la foto. Una clave incorrecta no devuelve nada.")
            }
        }
    }
}
