package com.hmoney.app.ui.screens.entry

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hmoney.app.R

/**
 * 記帳入口流程主控（S41 新元件）：
 * FAB 點擊後顯示入口選擇（只列開啟者）；三個 AI 開關全關時直接開 AddTransactionSheet（現行為）。
 * 流程：入口選擇 → 打字對話／相片挑選／掃碼覆蓋 → 草稿預覽 → 確認寫入。
 */
@Composable
fun EntryFlowHost(
    onDismiss: () -> Unit,
    onOpenManualSheet: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val flags by viewModel.flags.collectAsState()
    var chooserVisible by rememberSaveable { mutableStateOf(true) }
    var textDialogVisible by rememberSaveable { mutableStateOf(false) }
    var scanVisible by rememberSaveable { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.parseFromImage(uri)
        } else {
            chooserVisible = true
        }
    }

    // 全部關閉 → 不出選單，直接開 AddTransactionSheet（與現行為完全相同）
    LaunchedEffect(flags) {
        val f = flags ?: return@LaunchedEffect
        if (visibleEntryOptions(f).isEmpty()) {
            onOpenManualSheet()
            onDismiss()
        }
    }

    // S41-fix：confirmAll 完成（DONE）自動關閉 host；防禦——host 可見但所有子畫面皆不可見時自動重開選單，
    // 避免子流程取消後 FAB 再點無反應（showEntryFlow 卡 true）
    LaunchedEffect(state.phase, state.errorRes) {
        if (state.phase == EntryViewModel.Phase.DONE) {
            viewModel.reset()
            onDismiss()
            return@LaunchedEffect
        }
        if (state.phase == EntryViewModel.Phase.IDLE &&
            !textDialogVisible &&
            !scanVisible
        ) {
            chooserVisible = true
        }
    }

    if (chooserVisible && flags != null) {
        EntryChooserSheet(
            options = visibleEntryOptions(flags!!),
            onSelect = { option ->
                chooserVisible = false
                when (option) {
                    EntryOption.MANUAL -> {
                        onOpenManualSheet()
                        onDismiss()
                    }
                    EntryOption.AI_TEXT -> textDialogVisible = true
                    EntryOption.AI_IMAGE -> photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                    EntryOption.BARCODE_SCAN -> scanVisible = true
                }
            },
            onDismiss = onDismiss,
        )
    }

    if (textDialogVisible) {
        var text by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textDialogVisible = false; chooserVisible = true },
            confirmButton = {
                TextButton(
                    onClick = {
                        textDialogVisible = false
                        viewModel.parseFromText(text)
                    },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(R.string.entry_text_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { textDialogVisible = false; chooserVisible = true }) {
                    Text(stringResource(R.string.entry_cancel))
                }
            },
            title = { Text(stringResource(R.string.entry_text_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.entry_text_hint)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    if (scanVisible) {
        ScanOverlayScreen(
            state = state,
            onFrame = viewModel::onCameraFrame,
            onRecordThis = {
                scanVisible = false
                viewModel.prepareScanDraft()
            },
            onClose = {
                scanVisible = false
                viewModel.reset()
                chooserVisible = true
            },
        )
    }

    if (state.phase == EntryViewModel.Phase.PREVIEW ||
        state.phase == EntryViewModel.Phase.WRITING ||
        state.phase == EntryViewModel.Phase.DONE
    ) {
        DraftPreviewSheet(
            state = state,
            onAmountChange = viewModel::updateDraftAmount,
            onNoteChange = viewModel::updateDraftNote,
            onCategoryChange = viewModel::updateDraftCategory,
            onAccountChange = viewModel::updateDraftAccount,
            onDateChange = viewModel::updateDraftDate,
            onRemove = viewModel::removeDraft,
            onConfirmAll = viewModel::confirmAll,
            onClose = {
                viewModel.reset()
                onDismiss()
            },
        )
    }

    if (state.errorRes != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.entry_ok))
                }
            },
            title = { Text(stringResource(R.string.entry_error_title)) },
            text = { Text(stringResource(state.errorRes!!)) },
        )
    }
}
