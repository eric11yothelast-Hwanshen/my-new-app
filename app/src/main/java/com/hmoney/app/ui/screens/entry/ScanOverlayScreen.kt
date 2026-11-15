package com.hmoney.app.ui.screens.entry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hmoney.app.R
import com.hmoney.app.domain.scanner.BarcodeScanError
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.common.lineChartPoints

/**
 * 條碼掃描全螢幕覆蓋（S41 新檔案）：
 * CameraX PreviewView（AndroidView 包裝）＋ImageAnalysis 分析端；
 * ImageProxy 在分析結束後必定 close（否則畫面卡死）；
 * scanner client 由 @Singleton 重用（S40 鐵律），幀以 450ms 節流＋同碼去重（ScanThrottle）。
 * 相機權限以 rememberLauncherForActivityResult 要求，拒絕時顯示引導
 * （錯誤型別＝BarcodeScanError.CameraPermissionDenied）。
 */
@Composable
fun ScanOverlayScreen(
    state: EntryViewModel.UiState,
    onFrame: (android.graphics.Bitmap, Int) -> Unit,
    onRecordThis: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    // 掃到新條碼時震動提示
    LaunchedEffect(state.scannedBarcode) {
        if (state.scannedBarcode != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (hasPermission) {
                CameraPreview(onFrame = onFrame)
                ScanInfoCard(
                    state = state,
                    onRecordThis = onRecordThis,
                    onClose = onClose,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.scan_permission_denied),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.padding(8.dp),
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.scan_permission_retry))
                    }
                    TextButton(onClick = onClose) {
                        Text(stringResource(R.string.entry_close), color = Color.White)
                    }
                }
            }
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) { Text(stringResource(R.string.scan_close), color = Color.White) }
        }
    }
}

@Composable
private fun ScanInfoCard(
    state: EntryViewModel.UiState,
    onRecordThis: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val barcode = state.scannedBarcode
            if (barcode == null) {
                Text(stringResource(R.string.scan_hint))
                Text(
                    text = stringResource(R.string.scan_hint_steady),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.scanError == BarcodeScanError.NoBarcodeFound) {
                    Text(
                        text = stringResource(R.string.scan_none_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }
            Text(
                text = stringResource(R.string.scan_detected, barcode),
                style = MaterialTheme.typography.titleSmall,
            )
            val trend = state.trend
            if (trend?.lastPriceCents == null) {
                Text(stringResource(R.string.scan_no_record))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.scan_last_price, Formats.money(trend.lastPriceCents)))
                    Text(stringResource(R.string.scan_lowest_price, Formats.money(trend.lowestPriceCents ?: 0L)))
                }
                // S48 價格趨勢迷你折線：資料＝既有 PriceTrendCalculator 的歷史摘要（新→舊，反轉為時間序）
                val history = state.history
                if (history.size >= 2) {
                    val series = history.sortedBy { it.dateEpochMillis }
                    Text(
                        text = stringResource(R.string.scan_trend_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val lineColor = MaterialTheme.colorScheme.primary
                    val markerColor = MaterialTheme.colorScheme.tertiary
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(64.dp),
                    ) {
                        val points = lineChartPoints(
                            values = series.map { it.amountCents },
                            width = size.width,
                            height = size.height,
                            padding = 4f,
                        )
                        val path = Path()
                        points.forEachIndexed { index, point ->
                            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                        }
                        drawPath(path, lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
                        points.lastOrNull()?.let { drawCircle(markerColor, radius = 6f, center = Offset(it.x, it.y)) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = Formats.date(series.first().dateEpochMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Formats.date(series.last().dateEpochMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                androidx.compose.material3.OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.entry_close))
                }
                Button(onClick = onRecordThis, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.scan_record_this))
                }
            }
        }
    }
}
