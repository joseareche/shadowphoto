package com.shadowphoto.ui.decrypt

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowphoto.data.repo.VaultRepository
import com.shadowphoto.domain.SessionStore
import com.shadowphoto.domain.UnlockOutcome
import com.shadowphoto.ui.decodeFilePreview
import com.shadowphoto.ui.decodePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DecryptUiState(
    val cover: Bitmap? = null,
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val ready: Boolean = false,
    val missing: Boolean = false,
)

class DecryptViewModel(
    private val repository: VaultRepository,
    private val session: SessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(DecryptUiState())
    val state: StateFlow<DecryptUiState> = _state.asStateFlow()
    private var container: ByteArray? = null

    fun loadVault(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, missing = false) }
            val entity = repository.get(id)
            if (entity == null) {
                _state.update { it.copy(busy = false, missing = true) }
                return@launch
            }
            val bytes = repository.readContainer(entity)
            container = bytes
            val cover = withContext(Dispatchers.IO) { decodeFilePreview(entity.path, 1080) }
            _state.update { it.copy(busy = false, cover = cover, ready = true) }
        }
    }

    fun loadExternal() {
        val bytes = session.peekExternal()
        if (bytes == null) {
            _state.update { it.copy(missing = true) }
            return
        }
        container = bytes
        _state.update {
            it.copy(
                cover = decodePreview(bytes, 1080),
                ready = true,
                missing = false,
                error = null,
            )
        }
    }

    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }

    fun importExternal(onDone: (String?) -> Unit) {
        val bytes = session.peekExternal()
        if (bytes == null) {
            onDone("No hay archivo para guardar")
            return
        }
        viewModelScope.launch {
            try {
                repository.importContainer(bytes)
                onDone(null)
            } catch (e: Exception) {
                onDone(e.message ?: "No se pudo guardar en la bóveda")
            }
        }
    }

    fun unlock(onSuccess: () -> Unit) {
        val bytes = container
        if (bytes == null) {
            _state.update { it.copy(error = "No hay archivo para descifrar.") }
            return
        }
        val snapshot = _state.value
        if (snapshot.password.isEmpty()) {
            _state.update { it.copy(error = "Introduce la clave de descifrado.") }
            return
        }
        val password = snapshot.password.toCharArray()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val outcome = repository.unlock(bytes, password)
            password.fill('\u0000')
            when (outcome) {
                is UnlockOutcome.Success -> {
                    session.setUnlocked(outcome.bytes)
                    _state.update { it.copy(busy = false, password = "") }
                    onSuccess()
                }
                UnlockOutcome.AuthenticationFailed -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = "La clave no coincide. No se recuperó ninguna imagen.",
                        )
                    }
                }
                is UnlockOutcome.InvalidContainer -> {
                    _state.update {
                        it.copy(
                            busy = false,
                            error = "El archivo está dañado. No se mostró ninguna imagen.",
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        _state.value.cover?.recycle()
        super.onCleared()
    }
}
