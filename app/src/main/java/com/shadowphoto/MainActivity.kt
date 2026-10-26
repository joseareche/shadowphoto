package com.shadowphoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shadowphoto.ui.nav.ShadowPhotoNav
import com.shadowphoto.ui.theme.ShadowPhotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadowPhotoTheme {
                ShadowPhotoNav()
            }
        }
    }
}
