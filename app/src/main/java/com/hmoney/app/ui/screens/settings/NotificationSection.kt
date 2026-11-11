package com.hmoney.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.ui.components.FluidSwitch
import com.hmoney.app.ui.common.Formats
import java.time.LocalTime

/**
 * 通知區塊：每日記帳提醒（開關＋時間）、通知權限動態請求與被拒時的引導。
 */
@Composable
internal fun NotificationSection(
    viewModel: NotificationViewModel = hiltViewModel(),
    onOpenWizard: () -> Unit,
) {
    val context = LocalContext.current
    val reminder by viewModel.reminder.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var showPermissionGuide by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }

    // POST_NOTIFICATIONS 動態請求（API 33+）
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) showPermissionGuide = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.notification_title),
            style = MaterialTheme.typography.titleMedium,
        )

        // 每日記帳提醒
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notification_daily_reminder),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.notification_daily_reminder_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FluidSwitch(
                checked = reminder.enabled,
                onCheckedChange = { enabled ->
                    if (enabled && !hasPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    viewModel.setReminder(enabled, reminder.hour, reminder.minute)
                },
            )
        }

        if (reminder.enabled) {
            AssistChip(
                onClick = { showTimePicker = true },
                label = {
                    Text(
                        stringResource(R.string.notification_daily_reminder_time) +
                            "：" + Formats.time(LocalTime.of(reminder.hour, reminder.minute)),
                    )
                },
            )
        }

        // 通知權限狀態／請求／被拒引導
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (hasPermission) {
                Text(
                    text = stringResource(R.string.notification_permission_granted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                    Text(stringResource(R.string.notification_permission_request))
                }
                TextButton(onClick = { showPermissionGuide = true }) {
                    Text(stringResource(R.string.notification_permission_open_settings))
                }
            }
        }

        // 防後台設定精靈入口
        TextButton(onClick = onOpenWizard) {
            Text(stringResource(R.string.wizard_title))
        }
    }

    // 提醒時間選擇
    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = reminder.hour,
            initialMinute = reminder.minute,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.notification_daily_reminder_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setReminder(true, pickerState.hour, pickerState.minute)
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // 通知被拒時的引導畫面
    if (showPermissionGuide) {
        AlertDialog(
            onDismissRequest = { showPermissionGuide = false },
            title = { Text(stringResource(R.string.notification_permission_title)) },
            text = { Text(stringResource(R.string.notification_permission_denied)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 直接開啟本 App 的系統通知設定頁
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        showPermissionGuide = false
                    },
                ) { Text(stringResource(R.string.notification_permission_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionGuide = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
