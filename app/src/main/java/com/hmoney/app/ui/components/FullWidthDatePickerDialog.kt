package com.hmoney.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hmoney.app.ui.common.shortDatePickerFormatter

/**
 * S47→S48 全寬日期對話框：容器用 androidx.compose.ui.window.Dialog＋usePlatformDefaultWidth = false
 * （照 SimpleScanDialog 的全寬模式）。S48 移除內層 Card 與雙層 16.dp padding——
 * M3 CalendarMonth 的星期列與日曆格走 BaseCalendarLayout，把可用寬度「嚴格均分 7 欄」
 * （非依文字測量的 weight），欄寬不足只會讓文字換行／省略，不會裁切末欄；
 * 因此「星期六被壓縮」的根因是 Card＋Column 共 64.dp padding 吃掉日曆寬度。
 * 修法：內容直接放 Dialog（Surface 提供形狀／底色），水平 padding 降到 8.dp，
 * 360dp 寬螢幕＋fontScale 1.3 下每欄仍有約 49dp，單一中文字綽綽有餘。
 * 標題／DatePicker／取消確定列結構照舊；嚴禁改回 AlertDialog 或再加 Card。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullWidthDatePickerDialog(
    state: DatePickerState,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                DatePicker(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    dateFormatter = shortDatePickerFormatter(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(dismissText)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(confirmText)
                    }
                }
            }
        }
    }
}
