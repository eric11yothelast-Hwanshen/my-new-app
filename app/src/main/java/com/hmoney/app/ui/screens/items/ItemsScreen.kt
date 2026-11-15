package com.hmoney.app.ui.screens.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.hmoney.app.R
import com.hmoney.app.domain.logic.ItemExpirySectionType
import com.hmoney.app.domain.logic.expiryTimelineCounts
import com.hmoney.app.domain.logic.sectionTypeForTimelineDate
import com.hmoney.app.domain.model.Item
import com.hmoney.app.ui.common.Formats
import java.time.LocalDate
import java.time.ZoneId
import com.hmoney.app.ui.components.EmptyState
import com.hmoney.app.ui.components.SwipeToRevealRow
import com.hmoney.app.ui.components.GlassCard
import com.hmoney.app.ui.components.glassSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * S46 物品管理頁：清單依到期狀態分節（已過期 → 7 天內到期 → 其他，各節升冪）；
 * 每列顯示名稱／數量／存放位置／到期日，點擊編輯、滑動刪除（刪除需確認）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onGoSettings: () -> Unit = {},
    viewModel: ItemsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val glassState = rememberHazeState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // S48 到期時間軸：未來 30 天有到期的日子畫點（密度＝當日數量）；無到期物品時隱藏
    val timeline = remember(state.sections) {
        expiryTimelineCounts(
            items = state.sections.flatMap { it.items },
            today = LocalDate.now(),
            zone = ZoneId.systemDefault(),
        )
    }

    var editingItem by remember { mutableStateOf<Item?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var deletingItem by remember { mutableStateOf<Item?>(null) }
    var revealedId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingItem = null
                    showEditSheet = true
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.items_add)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .glassSource(glassState),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.items_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                // S50：升頂層分頁，移除返回箭頭（返回鍵＝back to start destination 照系統預設）
            }

            if (timeline.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    timeline.forEach { day ->
                        val dotSize = (8 + minOf(day.count, 4) * 3).dp
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val sectionType = sectionTypeForTimelineDate(
                                    date = day.date,
                                    today = LocalDate.now(),
                                )
                                val sectionIndex = state.sections.indexOfFirst { it.type == sectionType }
                                if (sectionIndex >= 0) {
                                    var headerIndex = 0
                                    for (j in 0 until sectionIndex) {
                                        headerIndex += 1 + state.sections[j].items.size
                                    }
                                    scope.launch { listState.animateScrollToItem(headerIndex) }
                                }
                            },
                        ) {
                            Text(
                                text = day.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(dotSize)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                    ),
                            )
                            Text(
                                text = day.count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.sections.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    messageRes = R.string.items_empty,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 160.dp,
                    ),
                ) {
                    state.sections.forEach { section ->
                        item(key = "header-${section.type}") {
                            Text(
                                text = stringResource(
                                    when (section.type) {
                                        ItemExpirySectionType.EXPIRED -> R.string.items_section_expired
                                        ItemExpirySectionType.SOON -> R.string.items_section_soon
                                        ItemExpirySectionType.OTHER -> R.string.items_section_other
                                    },
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (section.type == ItemExpirySectionType.EXPIRED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(section.items, key = { it.id }) { item ->
                            SwipeToRevealRow(
                                revealed = revealedId == item.id,
                                onReveal = { if (revealedId != item.id) revealedId = item.id },
                                onCollapse = { if (revealedId == item.id) revealedId = null },
                                actions = {
                                    IconButton(onClick = { deletingItem = item }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.common_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                            ) {
                                GlassCard(
                                    glassState = glassState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editingItem = item
                                            showEditSheet = true
                                        },
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = listOfNotNull(
                                                stringResource(R.string.items_quantity) + "：" + item.quantity,
                                                item.storageLocation,
                                                item.expiryDateEpochMillis?.let {
                                                    stringResource(R.string.items_expiry_date) +
                                                        "：" + Formats.date(it)
                                                } ?: stringResource(R.string.items_expiry_none),
                                            ).joinToString("｜"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet) {
        ItemEditSheet(
            editing = editingItem,
            onDismiss = {
                showEditSheet = false
                viewModel.clearSuggestion()
                viewModel.clearScanState()
            },
            onGoSettings = onGoSettings,
            viewModel = viewModel,
        )
    }

    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text(stringResource(R.string.items_delete_title)) },
            text = { Text(stringResource(R.string.items_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        deletingItem = null
                    },
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
