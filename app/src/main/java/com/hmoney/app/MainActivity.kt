package com.hmoney.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hmoney.app.ui.HmoneyAppRoot
import com.hmoney.app.ui.theme.HmoneyTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一 Activity：edge-to-edge 顯示，所有畫面由 Compose Navigation 管理。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HmoneyTheme {
                HmoneyAppRoot()
            }
        }
    }
}
