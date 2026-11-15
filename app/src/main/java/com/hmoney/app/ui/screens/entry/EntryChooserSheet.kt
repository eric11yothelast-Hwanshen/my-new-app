package com.hmoney.app.ui.screens.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R

/**
 * 記帳入口選擇清單（S41 新元件，與 AddTransactionSheet 無結構耦合）：
 * 小型 bottom sheet（skipHalfExpanded），項目有限不需捲動。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryChooserSheet(
    options: List<EntryOption>,
    onSelect: (EntryOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.entry_choose_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            options.forEach { option ->
                val (icon, labelRes) = option.meta()
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    leadingContent = { Icon(icon, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) },
                )
            }
        }
    }
}

private fun EntryOption.meta(): Pair<ImageVector, Int> = when (this) {
    EntryOption.MANUAL -> Icons.Outlined.Edit to R.string.entry_option_manual
    EntryOption.AI_TEXT -> Icons.Outlined.AutoAwesome to R.string.entry_option_ai_text
    EntryOption.AI_IMAGE -> Icons.Outlined.PhotoCamera to R.string.entry_option_ai_image
    EntryOption.BARCODE_SCAN -> Icons.Outlined.QrCodeScanner to R.string.entry_option_scan
}
