package com.shadowphoto.ui.vault

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowphoto.crypto.KeyManager
import com.shadowphoto.data.db.ProtectedImageEntity
import com.shadowphoto.data.repo.VaultRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VaultViewModel(
    private val repository: VaultRepository,
    private val keyManager: KeyManager,
) : ViewModel() {
    val images: StateFlow<List<ProtectedImageEntity>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val keystoreReady: StateFlow<Boolean> = flow {
        emit(keyManager.ensureDeviceSeal())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun export(entity: ProtectedImageEntity, uri: Uri, backup: Boolean, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                repository.exportTo(entity, uri, backup)
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "No se pudo exportar")
            }
        }
    }

    fun load(id: String, onLoaded: (ProtectedImageEntity?) -> Unit) {
        viewModelScope.launch {
            onLoaded(repository.get(id))
        }
    }
}
