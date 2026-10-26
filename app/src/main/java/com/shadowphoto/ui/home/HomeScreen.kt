package com.shadowphoto.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.shadowphoto.ui.components.SecondaryButton
import com.shadowphoto.ui.components.formatWhen
import com.shadowphoto.ui.components.photoCountLabel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNewPhoto: () -> Unit,
    onOpen: () -> Unit,
    onOpenItem: (String) -> Unit,
    onVault: () -> Unit,
) {
    val images by viewModel.images.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AppBottomBar(selected = MainTab.Home, onHome = {}, onVault = onVault)
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "ShadowPhoto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tus recuerdos,\nsolo tuyos",
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(Modifier.height(8.dp))
            val count = images?.size
            MutedText(
                if (count == null) "Cargando la bóveda…"
                else "${photoCountLabel(count)} · Protegidas localmente",
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = "Nueva fotografía", onClick = onNewPhoto)
            Spacer(Modifier.height(10.dp))
            SecondaryButton(text = "Abrir archivo", onClick = onOpen)
            Spacer(Modifier.height(16.dp))
            when (val list = images) {
                null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                emptyList<ProtectedImageEntity>() -> EmptyGallery(Modifier.weight(1f))
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { item ->
                        CoverTile(
                            path = item.path,
                            label = formatWhen(item.createdAt),
                            onClick = { onOpenItem(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(12.dp))
        Text("Aún no hay fotografías", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        MutedText("Cuando protejas una imagen, aquí verás su portada y el indicador de que está cifrada.")
    }
}
