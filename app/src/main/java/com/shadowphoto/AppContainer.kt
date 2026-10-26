package com.shadowphoto

import android.content.Context
import com.shadowphoto.camera.CameraController
import com.shadowphoto.crypto.KeyManager
import com.shadowphoto.data.db.ShadowDatabase
import com.shadowphoto.data.files.PrivateFileStore
import com.shadowphoto.data.repo.VaultRepository
import com.shadowphoto.domain.ProtectionService
import com.shadowphoto.domain.SessionStore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = ShadowDatabase.create(appContext)
    val files = PrivateFileStore(appContext)
    val protection = ProtectionService()
    val repository = VaultRepository(
        context = appContext,
        dao = database.protectedImages(),
        files = files,
        protection = protection,
    )
    val session = SessionStore()
    val keyManager = KeyManager(appContext)
    val camera = CameraController(appContext, files)
}
