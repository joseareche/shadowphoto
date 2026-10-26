package com.shadowphoto.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shadowphoto.data.db.ProtectedImageEntity
import com.shadowphoto.data.repo.VaultRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(repository: VaultRepository) : ViewModel() {
    val images: StateFlow<List<ProtectedImageEntity>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
