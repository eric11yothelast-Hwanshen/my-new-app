package com.hmoney.app.ui.screens.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.hmoney.app.ui.components.EmptyState
import com.hmoney.app.ui.components.GlassCard
import com.hmoney.app.ui.components.MarkdownText
import com.hmoney.app.ui.components.glassSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * AI 助手頁：本週花費分析（一鍵）＋自由提問＋分析歷史回看。
 * 未設定 API 時顯示友善引導而非錯誤。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsState()
    val history by viewModel.history.collectAsState()
    val analysisState by viewModel.analysisState.collectAsState()
    val glassState = rememberHazeState()

    var question by remember { mutableStateOf("") }
    var showConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wizard_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showConfigDialog = true }) {
                        Text(stringResource(R.string.ai_config_title))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .glassSource(glassState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 未設定 API：友善引導
                    if (!config.isConfigured) {
                        GlassCard(glassState = glassState) {
                            Text(
                                text = stringResource(R.string.ai_not_configured),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = { showConfigDialog = true }) {
                                Text(stringResource(R.string.ai_go_config))
                            }
                        }
                    }

                    // 本週花費分析
                    if (config.isConfigured) {
                        GlassCard(glassState = glassState) {
                            Text(
                                text = stringResource(R.string.ai_week_analysis),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.ai_config_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = viewModel::analyzeWeek,
                                enabled = analysisState !is AiViewModel.AnalysisUiState.Loading,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.ai_run))
                            }
                        }
                    }

                    // 執行狀態：loading／成功／明確錯誤
                    when (val state = analysisState) {
                        is AiViewModel.AnalysisUiState.Loading -> {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.ai_loading))
                            }
                        }
                        is AiViewModel.AnalysisUiState.Success -> {
                            AnalysisResultCard(glassState, state.analysis)
                        }
                        is AiViewModel.AnalysisUiState.Error -> {
                            Text(
                                text = stringResource(state.messageRes),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        AiViewModel.AnalysisUiState.Idle -> Unit
                    }

                    // 自由提問（已設定才顯示）
                    if (config.isConfigured) {
                        GlassCard(glassState = glassState) {
                            OutlinedTextField(
                                value = question,
                                onValueChange = { question = it },
                                placeholder = { Text(stringResource(R.string.ai_question_hint)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                            Button(
                                onClick = {
                                    viewModel.askQuestion(question)
                                    question = ""
                                },
                                enabled = question.isNotBlank() &&
                                    analysisState !is AiViewModel.AnalysisUiState.Loading,
                                modifier = Modifier.padding(top = 8.dp),
                            ) {
                                Text(stringResource(R.string.ai_ask))
                            }
                        }
                    }
                }
            }

            // 分析歷史（可回看）
            if (history.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.ai_history),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
                items(history.size, key = { history[it].id }) { index ->
                    val entry = history[index]
                    GlassCard(
                        glassState = glassState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = Formats.date(entry.createdAtEpochMillis) +
                                    " · " + entry.query,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MarkdownText(text = entry.summary)
                        }
                    }
                }
            } else if (config.isConfigured) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        messageRes = R.string.ai_history_empty,
                    )
                }
            }
        }
    }

    // 連線設定對話框
    if (showConfigDialog) {
        AiConfigDialog(
            initial = config,
            onDismiss = { showConfigDialog = false },
            onSave = { baseUrl, apiKey, model ->
                viewModel.saveConfig(baseUrl, apiKey, model) {
                    showConfigDialog = false
                }
            },
        )
    }
}

/** 分析結果卡：總結／分類洞察／異常支出偵測／省錢建議 */
@Composable
private fun AnalysisResultCard(
    glassState: dev.chrisbanes.haze.HazeState,
    analysis: com.hmoney.app.domain.model.AiAnalysis,
) {
    GlassCard(glassState = glassState) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.ai_summary),
                style = MaterialTheme.typography.titleSmall,
            )
            MarkdownText(text = analysis.summary)

            if (analysis.insights.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.ai_insights),
                    style = MaterialTheme.typography.titleSmall,
                )
                analysis.insights.forEach { MarkdownText(text = "• $it") }
            }
            if (analysis.anomalies.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.ai_anomalies),
                    style = MaterialTheme.typography.titleSmall,
                )
                analysis.anomalies.forEach {
                    MarkdownText(text = "• $it")
                }
            }
            if (analysis.tips.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.ai_tips),
                    style = MaterialTheme.typography.titleSmall,
                )
                analysis.tips.forEach { MarkdownText(text = "• $it") }
            }
        }
    }
}

/** AI 連線設定對話框：Base URL／API Key／模型名稱 */
@Composable
private fun AiConfigDialog(
    initial: com.hmoney.app.domain.model.AiConfig,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_config_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.ai_config_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.ai_base_url)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.ai_api_key)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.ai_model)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(baseUrl, apiKey, model) }) {
                Text(stringResource(R.string.ai_config_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
