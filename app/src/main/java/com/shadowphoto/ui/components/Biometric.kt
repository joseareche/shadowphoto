package com.shadowphoto.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Pide biometría o el bloqueo del dispositivo antes de mostrar la foto original.
 * Si el teléfono no tiene nada configurado, continúa: la clave sigue siendo obligatoria.
 * Cancelar no abre la imagen.
 */
fun confirmIdentity(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onConfirmed: () -> Unit,
    onCancelled: () -> Unit,
) {
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val can = BiometricManager.from(activity).canAuthenticate(authenticators)
    if (can != BiometricManager.BIOMETRIC_SUCCESS) {
        onConfirmed()
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onConfirmed()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onCancelled()
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(authenticators)
        .build()
    prompt.authenticate(info)
}
