package com.shadowphoto.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shadowphoto.data.db.ProtectedImageEntity
import com.shadowphoto.ui.components.AppBottomBar
import com.shadowphoto.ui.components.CoverTile
import com.shadowphoto.ui.components.MainTab
import com.shadowphoto.ui.components.MutedText
import com.shadowphoto.ui.components.PrimaryButton
import com.shadowphoto.ui.components.formatWhen
import com.shadowphoto.ui.theme.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onHome: () -> Unit,
    onOpen: (String) -> Unit,
    onProtect: () -> Unit,
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    val keystoreReady by viewModel.keystoreReady.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Mis fotografías") },
                colors = appBarColors(),
            )
        },
        bottomBar = {
            AppBottomBar(selected = MainTab.Vault, onHome = onHome, onVault = {})
        },
    ) { padding ->
        when (val list = images) {
            null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            emptyList<ProtectedImageEntity>() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("La bóveda está vacía", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                MutedText("Todas las fotos protegidas aparecen aquí con su portada. El candado solo se ve dentro de la app.")
                Spacer(Modifier.height(18.dp))
                PrimaryButton(text = "Proteger una fotografía", onClick = onProtect)
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                MutedText(
                    if (keystoreReady) {
                        "Todas aparecen con su portada. El sello del dispositivo está activo."
                    } else {
                        "Todas aparecen con su portada."
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        CoverTile(
                            path = item.path,
                            label = formatWhen(item.createdAt),
                            onClick = { onOpen(item.id) },
                        )
                    }
                }
            }
        }
    }
}
