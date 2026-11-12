package com.hmoney.app.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hmoney.app.R
import com.hmoney.app.domain.logic.CategoryRules
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.ui.common.IconMapper
import com.hmoney.app.ui.theme.AccountPalette
import kotlinx.coroutines.launch

/**
 * 分類管理頁：支出／收入兩個 tab；列表＝圖示＋名稱＋色點＋隱藏狀態；
 * 新增／編輯（名稱／圖示／顏色／排序）、隱藏／恢復、刪除（僅限無關聯）。
 * 「其他」「其他收入」為 fallback 保護分類，不可隱藏／刪除（CategoryRules）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    val savedMessage = stringResource(R.string.categories_saved)
    val duplicateMessage = stringResource(R.string.categories_duplicate)
    val deletedMessage = stringResource(R.string.categories_deleted)
    val deleteBlockedMessage = stringResource(R.string.categories_delete_blocked)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wizard_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.categories_add)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = if (state.type == CategoryType.EXPENSE) 0 else 1) {
                Tab(
                    selected = state.type == CategoryType.EXPENSE,
                    onClick = { viewModel.setType(CategoryType.EXPENSE) },
                    text = { Text(stringResource(R.string.add_type_expense)) },
                )
                Tab(
                    selected = state.type == CategoryType.INCOME,
                    onClick = { viewModel.setType(CategoryType.INCOME) },
                    text = { Text(stringResource(R.string.add_type_income)) },
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = 120.dp,
                ),
            ) {
                items(state.categories, key = { it.id }) { category ->
                    CategoryRow(
                        category = category,
                        onClick = { editingCategory = category },
                        onMoveUp = { viewModel.move(category.id, up = true) },
                        onMoveDown = { viewModel.move(category.id, up = false) },
                    )
                }
            }
        }
    }

    // 新增分類
    if (showAddDialog) {
        CategoryDialog(
            title = stringResource(R.string.categories_add),
            initialCategory = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, iconKey, colorArgb ->
                viewModel.addCategory(name, iconKey, colorArgb) { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) savedMessage else duplicateMessage)
                    }
                    if (ok) showAddDialog = false
                }
            },
        )
    }

    // 編輯分類
    editingCategory?.let { category ->
        CategoryDialog(
            title = stringResource(R.string.categories_edit),
            initialCategory = category,
            onDismiss = { editingCategory = null },
            onConfirm = { name, iconKey, colorArgb ->
                viewModel.updateCategory(category, name, iconKey, colorArgb) { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) savedMessage else duplicateMessage)
                    }
                    if (ok) editingCategory = null
                }
            },
            onHide = if (!category.isHidden) {
                {
                    viewModel.setHidden(category.id, hidden = true)
                    editingCategory = null
                }
            } else {
                null
            },
            onRestore = if (category.isHidden) {
                {
                    viewModel.setHidden(category.id, hidden = false)
                    editingCategory = null
                }
            } else {
                null
            },
            onDelete = {
                deletingCategory = category
                editingCategory = null
            },
        )
    }

    // 刪除確認（僅無關聯分類可刪；有關聯或受保護時提示只能隱藏）
    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text(stringResource(R.string.common_delete)) },
            text = {
                Text(stringResource(R.string.categories_delete_confirm, category.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(category.id) { deleted ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (deleted) deletedMessage else deleteBlockedMessage,
                                )
                            }
                        }
                        deletingCategory = null
                    },
                ) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * 分類列表列：圖示（帳戶色 tint）＋名稱＋色點＋隱藏標記＋上移／下移。
 */
@Composable
private fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val accentColor = Color(category.colorArgb)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = IconMapper.iconOf(category.iconKey),
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(24.dp),
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = category.name, style = MaterialTheme.typography.bodyLarge)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accentColor, CircleShape),
            )
            if (category.isHidden) {
                Text(
                    text = stringResource(R.string.categories_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.KeyboardArrowUp,
                contentDescription = stringResource(R.string.categories_move_up),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = stringResource(R.string.categories_move_down),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 分類新增／編輯對話框：名稱（必填、防重複）＋圖示選擇（沿用帳戶頁做法）＋色票選擇。
 * 編輯模式另提供 隱藏／恢復 與 刪除（fallback 保護分類不顯示這兩顆）。
 */
@Composable
private fun CategoryDialog(
    title: String,
    initialCategory: Category?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, iconKey: String, colorArgb: Long) -> Unit,
    onHide: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialCategory?.name ?: "") }
    var iconKey by remember { mutableStateOf(initialCategory?.iconKey ?: "more_horiz") }
    var color by remember { mutableStateOf(initialCategory?.colorArgb ?: AccountPalette.colorArgbs[0]) }
    var nameError by remember { mutableStateOf(false) }
    val protectedCategory = initialCategory != null &&
        CategoryRules.isProtectedFallback(initialCategory.name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.categories_name)) },
                    isError = nameError,
                    supportingText = {
                        if (nameError) {
                            Text(stringResource(R.string.categories_name_required))
                        }
                    },
                    singleLine = true,
                )

                Text(stringResource(R.string.accounts_icon), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(IconMapper.accountIconKeys) { key ->
                        Icon(
                            imageVector = IconMapper.iconOf(key),
                            contentDescription = null,
                            tint = if (iconKey == key) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { iconKey = key }
                                .padding(4.dp),
                        )
                    }
                }

                Text(stringResource(R.string.accounts_color), style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AccountPalette.colorArgbs) { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(c), CircleShape)
                                .border(
                                    width = if (color == c) 3.dp else 1.dp,
                                    color = if (color == c) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onConfirm(name.trim(), iconKey, color)
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (onHide != null) {
                    TextButton(onClick = onHide) {
                        Text(stringResource(R.string.categories_hide))
                    }
                }
                if (onRestore != null) {
                    TextButton(onClick = onRestore) {
                        Text(stringResource(R.string.categories_restore))
                    }
                }
                if (onDelete != null && !protectedCategory) {
                    TextButton(onClick = onDelete) {
                        Text(
                            stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        },
    )
}
