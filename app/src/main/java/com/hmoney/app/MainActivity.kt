package com.hmoney.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.hmoney.app.domain.model.AppearanceSettings
import com.hmoney.app.domain.model.DarkMode
import com.hmoney.app.domain.repository.SettingsRepository
import com.hmoney.app.ui.HmoneyAppRoot
import com.hmoney.app.ui.theme.HmoneyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 唯一 Activity：edge-to-edge 顯示，深色模式／動態色彩依使用者設定套用。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by settingsRepository.observeAppearance()
                .collectAsState(initial = AppearanceSettings())
            val systemDark = isSystemInDarkTheme()
            HmoneyTheme(
                darkTheme = when (appearance.darkMode) {
                    DarkMode.SYSTEM -> systemDark
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                },
                dynamicColor = appearance.dynamicColor,
            ) {
                HmoneyAppRoot()
            }
        }
    }
}
