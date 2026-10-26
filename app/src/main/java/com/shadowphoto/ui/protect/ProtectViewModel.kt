package com.shadowphoto.ui.protect

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowphoto.data.repo.VaultRepository
import com.shadowphoto.domain.ProtectionException
import com.shadowphoto.domain.SessionStore
import com.shadowphoto.ui.decodePreview
import com.shadowphoto.ui.neutralCoverPng
import com.shadowphoto.ui.readBounded
import com.shadowphoto.ui.toCoverPng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProtectUiState(
    val privatePreview: Bitmap? = null,
    val coverPreview: Bitmap? = null,
    val hasPrivate: Boolean = false,
    val hasCover: Boolean = false,
    val password: String = "",
    val confirm: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val savedId: String? = null,
)

class ProtectViewModel(
    private val appContext: Context,
    private val repository: VaultRepository,
    private val session: SessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ProtectUiState())
    val state: StateFlow<ProtectUiState> = _state.asStateFlow()

    init {
        refreshPreviews()
    }

    fun refreshPreviews() {
        viewModelScope.launch {
            val privateBytes = session.peekPrivate()
            val coverBytes = session.peekCover()
            val privatePreview = withContext(Dispatchers.Default) {
                privateBytes?.let { decodePreview(it, 1080) }
            }
            val coverPreview = withContext(Dispatchers.Default) {
                coverBytes?.let { decodePreview(it, 1080) }
            }
            _state.update {
                it.copy(
                    hasPrivate = privateBytes != null,
                    hasCover = coverBytes != null,
                    privatePreview = privatePreview,
                    coverPreview = coverPreview,
                )
            }
        }
    }

    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null, saved = false, savedId = null) }

    fun onConfirm(value: String) = _state.update { it.copy(confirm = value, error = null, saved = false, savedId = null) }

    fun importPrivate(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val bytes = readUri(uri)
                session.setPrivate(bytes)
                refreshPreviews()
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "No se pudo leer la fotografía") }
            }
        }
    }

    fun importCover(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val bytes = withContext(Dispatchers.Default) { toCoverPng(readUri(uri)) }
                session.setCover(bytes)
                refreshPreviews()
                _state.update { it.copy(busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "No se pudo leer la portada") }
            }
        }
    }

    fun generateCover() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val png = withContext(Dispatchers.Default) { neutralCoverPng() }
            session.setCover(png)
            refreshPreviews()
            _state.update { it.copy(busy = false) }
        }
    }

    fun protect() {
        val snapshot = _state.value
        if (!snapshot.hasPrivate) {
            _state.update { it.copy(error = "Elige o toma la fotografía privada.") }
            return
        }
        if (!snapshot.hasCover) {
            _state.update { it.copy(error = "Elige una portada alternativa o genera una.") }
            return
        }
        if (snapshot.password.length < 8) {
            _state.update { it.copy(error = "La clave necesita al menos 8 caracteres.") }
            return
        }
        if (snapshot.password != snapshot.confirm) {
            _state.update { it.copy(error = "Las claves no coinciden.") }
            return
        }
        val original = session.peekPrivate() ?: return
        val cover = session.peekCover() ?: return
        val password = snapshot.password.toCharArray()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val entity = withContext(Dispatchers.Default) {
                    repository.protectAndStore(original, cover, password)
                }
                session.clearCapture()
                _state.update {
                    it.copy(
                        busy = false,
                        saved = true,
                        savedId = entity.id,
                        password = "",
                        confirm = "",
                        hasPrivate = false,
                        privatePreview = null,
                        error = null,
                    )
                }
            } catch (e: ProtectionException) {
                _state.update { it.copy(busy = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, error = e.message ?: "No se pudo proteger la fotografía")
                }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun discardPrivate() {
        session.clearCapture()
        refreshPreviews()
        _state.update { it.copy(saved = false, savedId = null, password = "", confirm = "", error = null) }
    }

    fun consumeSaved() {
        _state.update { it.copy(saved = false, savedId = null) }
    }

    private suspend fun readUri(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { readBounded(it) }
            ?: error("No se pudo abrir la imagen")
    }
}
