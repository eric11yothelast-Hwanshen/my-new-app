package com.hmoney.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.ui.screens.ai.FeatureAiViewModel
import com.hmoney.app.ui.screens.ai.toMessageRes

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
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.ai_insight_week)) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = state.analysis.summary)
                        state.analysis.insights.forEach {
                            Text(text = "• $it", style = MaterialTheme.typography.bodySmall)
                        }
                        state.analysis.anomalies.forEach {
                            Text(text = "⚠ $it", style = MaterialTheme.typography.bodySmall)
                        }
                        state.analysis.tips.forEach {
                            Text(text = "💡 $it", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ai_close))
                    }
                },
            )
        }

        FeatureAiViewModel.UiState.Idle -> Unit
        is FeatureAiViewModel.UiState.Filter -> Unit
    }
}
