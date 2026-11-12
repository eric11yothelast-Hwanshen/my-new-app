package com.hmoney.app.ui.screens.ai

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.ui.components.MarkdownText

/** 快捷提問選項（顯示與否由設定的開關控制） */
private val QUICK_PROMPTS = listOf(
    R.string.ai_quick_month_top,
    R.string.ai_quick_month_summary,
    R.string.ai_quick_week_anomaly,
    R.string.ai_quick_saving_tips,
)

/**
 * 全域 AI 助手 bottom sheet：對話式問答，附精簡去敏感化的財務資料 context。
 * 頂部功能列支援新對話、切換／刪除對話與清空全部；訊息長按可複製或刪除。
 * sheetState 使用 skipHalfExpanded = true——首次開啟即為全開，輸入列直接可見。
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun AiAssistantSheet(
    onDismiss: () -> Unit,
    onGoSettings: () -> Unit,
    viewModel: AiChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    var showSessionMenu by remember { mutableStateOf(false) }
    var pendingDeleteSession by remember { mutableStateOf<Long?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    val untitled = stringResource(R.string.ai_chat_untitled)

    // 開啟即全開：訊息列表 weight(1f) 填滿 sheet 剩餘高度，輸入列永遠可見
    val sheetState = rememberModalBottomSheetState(skipHalfExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 頂部功能列：標題＋新對話＋對話選擇＋清空
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.ai_chat_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = viewModel::newSession) {
                    Icon(
                        Icons.Outlined.AddComment,
                        contentDescription = stringResource(R.string.ai_chat_new),
                    )
                }
                // 「保留對話」關閉時為純記憶體模式：無跨頁同步的對話列表可切換
                if (state.settings.keepHistory) {
                Box {
                    IconButton(onClick = { showSessionMenu = true }) {
                        Icon(
                            Icons.Outlined.Forum,
                            contentDescription = stringResource(R.string.ai_chat_pick_session),
                        )
                    }
                    DropdownMenu(
                        expanded = showSessionMenu,
                        onDismissRequest = { showSessionMenu = false },
                    ) {
                        if (state.sessions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(untitled) },
                                onClick = { showSessionMenu = false },
                            )
                        }
                        state.sessions.forEach { session ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = session.title.ifBlank { untitled },
                                        maxLines = 1,
                                    )
                                },
                                onClick = {
                                    viewModel.switchTo(session.id)
                                    showSessionMenu = false
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.common_delete),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .combinedClickable(
                                                onClick = { pendingDeleteSession = session.id },
                                            ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            )
                        }
                    }
                }
                }
                IconButton(onClick = { showClearAllConfirm = true }) {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.ai_chat_clear_all),
                    )
                }
            }

            // 快捷提問：點擊帶入輸入框可編輯
            if (state.settings.showQuickPrompts) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QUICK_PROMPTS) { res ->
                        AssistChip(
                            onClick = { input = stringResource(res) },
                            label = {
                                Text(
                                    text = stringResource(res),
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
            }

            val listState = rememberLazyListState()
            val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
            LaunchedEffect(state.messages.size, state.loading, imeBottom) {
                val target = listState.layoutInfo.totalItemsCount - 1
                if (target >= 0) {
                    listState.animateScrollToItem(target)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.ai_chat_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onDelete = { viewModel.deleteMessage(message.id) },
                    )
                }
                if (state.loading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.ai_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = viewModel::cancel) {
                                Text(stringResource(R.string.ai_cancel))
                            }
                        }
                    }
                }
                state.error?.let { error ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(error.toMessageRes()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(
                                onClick = {
                                    viewModel.clearError()
                                    onGoSettings()
                                },
                            ) {
                                Text(stringResource(R.string.ai_go_settings))
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.ai_chat_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !state.loading,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.ai_send),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    // 清空所有對話：確認對話框
    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text(stringResource(R.string.ai_chat_clear_all)) },
            text = { Text(stringResource(R.string.ai_chat_clear_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearAllConfirm = false
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    // 刪除單一對話：確認對話框
    pendingDeleteSession?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            title = { Text(stringResource(R.string.ai_chat_delete_session)) },
            text = { Text(stringResource(R.string.ai_chat_delete_session_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSession(sessionId)
                        pendingDeleteSession = null
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSession = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** 對話氣泡：使用者靠右（主色）、AI 靠左（表面色）；長按可複製文字或刪除單則 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: AiChatMessage,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Surface(
                color = if (message.isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { menuExpanded = true },
                ),
            ) {
                if (message.isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                } else {
                    MarkdownText(
                        text = message.text,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_chat_copy)) },
                    onClick = {
                        clipboard.setText(AnnotatedString(message.text))
                        menuExpanded = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_chat_delete_message)) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}
