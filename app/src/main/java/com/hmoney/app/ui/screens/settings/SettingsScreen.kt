package com.hmoney.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.model.DarkMode
import com.hmoney.app.ui.components.FluidSwitch
import com.hmoney.app.ui.components.glassSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 設定頁：目前提供「外觀」（深色模式／動態色彩）。
 * 預算、AI 連線、通知、備份將於後續階段加入對應區塊。
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val appearance by viewModel.appearance.collectAsState()
    val glassState = rememberHazeState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .glassSource(glassState)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 12.dp),
        )

        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.titleMedium,
        )

        // 深色模式
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_dark_mode),
                style = MaterialTheme.typography.bodyLarge,
            )
            val modes = DarkMode.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = appearance.darkMode == mode,
                        onClick = { viewModel.setDarkMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size,
                        ),
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        DarkMode.SYSTEM -> R.string.settings_dark_mode_system
                                        DarkMode.LIGHT -> R.string.settings_dark_mode_light
                                        DarkMode.DARK -> R.string.settings_dark_mode_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }

        // 動態色彩（流體切換開關）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_dynamic_color),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.settings_dynamic_color_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FluidSwitch(
                checked = appearance.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor,
            )
        }
    }
}
