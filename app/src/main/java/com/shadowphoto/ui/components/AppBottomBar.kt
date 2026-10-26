package com.shadowphoto.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class MainTab { Home, Vault }

@Composable
fun AppBottomBar(
    selected: MainTab,
    onHome: () -> Unit,
    onVault: () -> Unit,
) {
    NavigationBar(containerColor = Color(0xFF121A24)) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onBackground,
            selectedTextColor = MaterialTheme.colorScheme.onBackground,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        )
        NavigationBarItem(
            selected = selected == MainTab.Home,
            onClick = onHome,
            icon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
            label = { Text("Inicio") },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == MainTab.Vault,
            onClick = onVault,
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            label = { Text("Bóveda") },
            colors = colors,
        )
    }
}
