package com.hmoney.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.ui.screens.ai.FeatureAiViewModel
import com.hmoney.app.ui.screens.ai.toMessageRes
import com.hmoney.app.ui.theme.CategoryPalette

/**
 * 通用 AI 結果對話框：loading（可取消）、錯誤（含前往設定）、文字結果／本週洞察。
 * 所有 AI 呼叫的三態回饋統一走這裡。
 */
@Composable
fun AiResultDialog(
    state: FeatureAiViewModel.UiState,
    onCancel: () -> Unit,
    onGoSettings: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    when (state) {
        is FeatureAiViewModel.UiState.Loading -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.ai_result_title)) },
                text = {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 64.dp)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.ai_loading))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.ai_cancel))
                    }
                },
            )
        }

        is FeatureAiViewModel.UiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.ai_result_title)) },
                text = {
                    Text(
                        text = stringResource(state.error.toMessageRes()),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onGoSettings()
                        },
                    ) { Text(stringResource(R.string.ai_go_settings)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ai_close))
                    }
                },
            )
        }

        is FeatureAiViewModel.UiState.TextResult -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.ai_result_title)) },
                text = {
                    Text(
                        text = state.text,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ai_close))
                    }
                },
            )
        }

        is FeatureAiViewModel.UiState.Insight -> {
            val analysis = state.analysis
            val timeText = remember(analysis.createdAtEpochMillis) {
                android.text.format.DateUtils
                    .getRelativeTimeSpanString(analysis.createdAtEpochMillis)
                    .toString()
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.ai_insight_week)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 摘要
                        Text(
                            text = analysis.summary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // Top 3 支出分類（CategoryPalette 色點；未收錄的自訂分類以 primary 顯示）
                        analysis.insights.take(3).forEach { line ->
                            val sep = if (line.contains('：')) "：" else ":"
                            val name = line.substringBefore(sep).trim()
                            val detail = line.substringAfter(sep, "").trim()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            CategoryPalette.colorOfNameOrNull(name)
                                                ?: MaterialTheme.colorScheme.primary,
                                            CircleShape,
                                        ),
                                )
                                Text(
                                    text = if (detail.isBlank()) name else "$name　$detail",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        // 一條建議
                        analysis.tips.firstOrNull()?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        // 生成時間（相對時間）＋重新分析
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    Row {
                        if (onRetry != null) {
                            TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.ai_retry))
                            }
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.ai_close))
                        }
                    }
                },
            )
        }

        FeatureAiViewModel.UiState.Idle -> Unit
        is FeatureAiViewModel.UiState.Filter -> Unit
    }
}
