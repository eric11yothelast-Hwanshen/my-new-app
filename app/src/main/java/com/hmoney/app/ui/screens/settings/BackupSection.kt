package com.hmoney.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.ui.common.Formats
import java.time.Instant
import java.time.ZoneId

/**
 * 備份區塊：手動匯出 CSV／完整 JSON 備份／還原，
 * 顯示最近自動備份時間（每日自動備份保留近 7 份）。
 */
@Composable
internal fun BackupSection(
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // 建立檔案（匯出）
    val createCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(viewModel::exportCsv) }
    val createJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportJson) }

    // 選擇備份檔（還原）
    val openJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pendingRestoreUri = it } }

    // 操作完成後短暫顯示結果再回復
    LaunchedEffect(state.status) {
        if (state.status == BackupViewModel.Status.SUCCESS ||
            state.status == BackupViewModel.Status.FAILED
        ) {
            kotlinx.coroutines.delay(2500)
            viewModel.resetStatus()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.backup_title),
            style = MaterialTheme.typography.titleMedium,
        )

        // 最近自動備份
        Text(
            text = state.latestBackupMillis?.let {
                stringResource(R.string.backup_latest, Formats.date(it) + " " + Formats.time(it))
            } ?: stringResource(R.string.backup_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 操作狀態：loading／成功／失敗
        when (state.status) {
            BackupViewModel.Status.WORKING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 2.dp))
                    Text(
                        text = stringResource(R.string.backup_working),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            BackupViewModel.Status.SUCCESS -> Text(
                text = stringResource(R.string.backup_success),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
            BackupViewModel.Status.FAILED -> Text(
                text = stringResource(R.string.backup_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            BackupViewModel.Status.IDLE -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { createCsv.launch("Hmoney-transactions.csv") },
                enabled = state.status != BackupViewModel.Status.WORKING,
            ) {
                Text(stringResource(R.string.backup_csv))
            }
            OutlinedButton(
                onClick = { createJson.launch("Hmoney-backup.json") },
                enabled = state.status != BackupViewModel.Status.WORKING,
            ) {
                Text(stringResource(R.string.backup_json))
            }
        }
        OutlinedButton(
            onClick = { openJson.launch(arrayOf("application/json")) },
            enabled = state.status != BackupViewModel.Status.WORKING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.backup_restore))
        }
    }

    // 還原確認：覆蓋現有資料，需明確確認
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.backup_restore)) },
            text = { Text(stringResource(R.string.backup_restore_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.restore(uri)
                        pendingRestoreUri = null
                    },
                ) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

