package com.shadowphoto.ui.nav

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shadowphoto.ShadowPhotoApp
import com.shadowphoto.container.ContainerFormatException
import com.shadowphoto.container.ShpDecoder
import com.shadowphoto.ui.camera.CameraScreen
import com.shadowphoto.ui.components.confirmIdentity
import com.shadowphoto.ui.decrypt.DecryptScreen
import com.shadowphoto.ui.decrypt.DecryptViewModel
import com.shadowphoto.ui.home.HomeScreen
import com.shadowphoto.ui.home.HomeViewModel
import com.shadowphoto.ui.protect.ProtectScreen
import com.shadowphoto.ui.protect.ProtectViewModel
import com.shadowphoto.ui.protect.SuccessScreen
import com.shadowphoto.ui.readBounded
import com.shadowphoto.ui.vault.DetailScreen
import com.shadowphoto.ui.vault.VaultScreen
import com.shadowphoto.ui.vault.VaultViewModel
import com.shadowphoto.ui.viewer.ViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Routes {
    const val HOME = "home"
    const val PROTECT = "protect"
    const val SUCCESS = "success/{id}"
    const val CAMERA = "camera"
    const val VAULT = "vault"
    const val DETAIL = "detail/{id}"
    const val DECRYPT = "decrypt/{mode}/{id}"
    const val VIEWER = "viewer"

    fun success(id: String) = "success/$id"
    fun detail(id: String) = "detail/$id"
    fun decryptVault(id: String) = "decrypt/vault/$id"
    fun decryptExternal() = "decrypt/external/file"
}

@Composable
fun ShadowPhotoNav() {
    val context = LocalContext.current
    val app = context.applicationContext as ShadowPhotoApp
    val factory = remember { ShadowFactory(app) }
    val nav = rememberNavController()
    var openError by remember { mutableStateOf<String?>(null) }
    var gateMessage by remember { mutableStateOf<String?>(null) }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        nav.currentBackStackEntry?.savedStateHandle?.set("open_uri", uri.toString())
        nav.navigate(Routes.decryptExternal())
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = factory)
            HomeScreen(
                viewModel = vm,
                onNewPhoto = { nav.navigate(Routes.PROTECT) },
                onVault = {
                    nav.navigate(Routes.VAULT) {
                        launchSingleTop = true
                    }
                },
                onOpen = {
                    openError = null
                    openDocument.launch(arrayOf("image/png", "application/octet-stream", "*/*"))
                },
                onOpenItem = { id -> nav.navigate(Routes.detail(id)) },
            )
        }
        composable(Routes.PROTECT) {
            val vm: ProtectViewModel = viewModel(factory = factory)
            ProtectScreen(
                viewModel = vm,
                onBack = {
                    app.container.session.clearCapture()
                    nav.popBackStack()
                },
                onCamera = { nav.navigate(Routes.CAMERA) },
                onSealed = { id ->
                    nav.navigate(Routes.success(id)) {
                        popUpTo(Routes.PROTECT) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.SUCCESS,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: VaultViewModel = viewModel(factory = factory)
            SuccessScreen(
                id = id,
                viewModel = vm,
                onVault = {
                    nav.navigate(Routes.VAULT) {
                        popUpTo(Routes.HOME)
                    }
                },
                onAnother = { nav.navigate(Routes.PROTECT) },
                onHome = {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                camera = app.container.camera,
                session = app.container.session,
                onCaptured = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.VAULT) {
            val vm: VaultViewModel = viewModel(factory = factory)
            VaultScreen(
                viewModel = vm,
                onHome = {
                    nav.popBackStack(Routes.HOME, inclusive = false)
                },
                onOpen = { id -> nav.navigate(Routes.detail(id)) },
                onProtect = { nav.navigate(Routes.PROTECT) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: VaultViewModel = viewModel(factory = factory)
            DetailScreen(
                id = id,
                viewModel = vm,
                onBack = { nav.popBackStack() },
                onDecrypt = { nav.navigate(Routes.decryptVault(id)) },
            )
        }
        composable(
            route = Routes.DECRYPT,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { entry ->
            val mode = entry.arguments?.getString("mode").orEmpty()
            val id = entry.arguments?.getString("id").orEmpty()
            val vm: DecryptViewModel = viewModel(factory = factory)
            val external = mode == "external"
            LaunchedEffect(mode, id) {
                if (external) {
                    val uriString = nav.previousBackStackEntry?.savedStateHandle?.get<String>("open_uri")
                    if (uriString != null) {
                        try {
                            val bytes = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { readBounded(it) }
                                    ?: error("No se pudo leer el archivo")
                            }
                            ShpDecoder().readBlob(bytes)
                            app.container.session.setExternal(bytes)
                            vm.loadExternal()
                        } catch (e: ContainerFormatException) {
                            openError = "Este archivo no es un contenedor ShadowPhoto."
                            nav.popBackStack()
                        } catch (e: Exception) {
                            openError = e.message ?: "No se pudo abrir el archivo"
                            nav.popBackStack()
                        }
                    } else {
                        vm.loadExternal()
                    }
                } else {
                    vm.loadVault(id)
                }
            }
            DecryptScreen(
                viewModel = vm,
                canImport = external,
                onBack = {
                    if (external) app.container.session.clearExternal()
                    nav.popBackStack()
                },
                onUnlocked = {
                    val host = context.findFragmentActivity()
                    if (host == null) {
                        nav.navigate(Routes.VIEWER)
                    } else {
                        confirmIdentity(
                            activity = host,
                            title = "Mostrar fotografía original",
                            subtitle = "Confirma con biometría o el bloqueo del dispositivo",
                            onConfirmed = { nav.navigate(Routes.VIEWER) },
                            onCancelled = {
                                app.container.session.clearUnlocked()
                                gateMessage = "No se confirmó tu identidad. La imagen no se mostró."
                            },
                        )
                    }
                },
                onImported = {
                    nav.navigate(Routes.VAULT) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(Routes.VIEWER) {
            ViewerScreen(
                session = app.container.session,
                onBack = {
                    app.container.session.clearUnlocked()
                    nav.popBackStack()
                },
            )
        }
    }
    if (openError != null) {
        AlertDialog(
            onDismissRequest = { openError = null },
            title = { Text("No se pudo abrir") },
            text = { Text(openError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { openError = null }) { Text("Entendido") }
            },
        )
    }
    if (gateMessage != null) {
        AlertDialog(
            onDismissRequest = { gateMessage = null },
            title = { Text("Identidad no confirmada") },
            text = { Text(gateMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { gateMessage = null }) { Text("Entendido") }
            },
        )
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

private class ShadowFactory(
    private val app: ShadowPhotoApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val container = app.container
        return when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(container.repository)
            ProtectViewModel::class.java -> ProtectViewModel(app, container.repository, container.session)
            VaultViewModel::class.java -> VaultViewModel(container.repository, container.keyManager)
            DecryptViewModel::class.java -> DecryptViewModel(container.repository, container.session)
            else -> error("ViewModel no registrado: ${modelClass.name}")
        } as T
    }
}
