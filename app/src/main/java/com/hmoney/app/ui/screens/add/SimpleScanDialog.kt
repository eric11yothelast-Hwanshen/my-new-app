package com.hmoney.app.ui.screens.add

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hmoney.app.R
import com.hmoney.app.ui.screens.entry.CameraPreview
import com.hmoney.app.domain.scanner.BarcodeScanError

/**
 * 表單條碼掃描 Dialog（S43 新檔）：全螢幕＋共用 CameraPreview＋節流與偵測由 ViewModel 負責；
 * 掃到碼顯示號碼與「使用此條碼」按鈕。不含趨勢／歷史卡片（EntryViewModel 專屬）。
 * 掃碼技術鐵律：綁定用 LocalLifecycleOwner（在 CameraPreview 內）；解析度 1280×720 不得降。
 */
@Composable
fun SimpleScanDialog(
    scannedBarcode: String?,
    scanError: BarcodeScanError?,
    onFrame: (android.graphics.Bitmap, Int) -> Unit,
    onUse: () -> Unit,
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

    LaunchedEffect(scannedBarcode) {
        if (scannedBarcode != null) {
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
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val barcode = scannedBarcode
                        if (barcode == null) {
                            Text(stringResource(R.string.scan_hint))
                            Text(
                                text = stringResource(R.string.scan_hint_steady),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (scanError == BarcodeScanError.NoBarcodeFound) {
                                Text(
                                    text = stringResource(R.string.scan_none_yet),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.scan_detected, barcode),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            ) {
                                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.entry_cancel))
                                }
                                Button(onClick = onUse, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.scan_use_this))
                                }
                            }
                        }
                    }
                }
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
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
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
