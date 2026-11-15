package com.hmoney.app.ui.screens.wizard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.domain.model.Vendor
import com.hmoney.app.domain.model.detectVendor

/**
 * 通知與續航設定精靈：偵測 Build.MANUFACTURER，顯示對應廠牌的
 * 圖文步驟，儘可能直接跳轉該廠牌設定頁；無法跳轉則開啟
 * dontkillmyapp 對應說明頁。並提供「忽略電池最佳化」請求（僅在此頁觸發）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryWizardScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vendor = remember { detectVendor(Build.MANUFACTURER) }
    var batteryDone by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wizard_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.wizard_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 忽略電池最佳化（僅在此頁觸發，不濫用）
            Text(
                text = stringResource(R.string.wizard_battery_optimization),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.wizard_battery_optimization_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (batteryDone) {
                Text(
                    text = stringResource(R.string.wizard_battery_done),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(
                    onClick = {
                        // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS（宣告權限後合法使用）
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                        batteryDone = isIgnoringBatteryOptimizations(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.wizard_battery_request))
                }
            }

            // 廠牌專屬步驟
            if (vendor != null) {
                Text(
                    text = stringResource(R.string.wizard_vendor_guide_title, vendor.displayName),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(vendor.stepsRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { launchVendorSettings(context, vendor) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.wizard_vendor_settings, vendor.displayName))
                }
                TextButton(
                    onClick = { openDontKillMyApp(context, vendor) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.wizard_vendor_more))
                }
            } else {
                Text(
                    text = stringResource(R.string.wizard_unknown_vendor),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { openDontKillMyApp(context, null) }) {
                    Text(stringResource(R.string.wizard_vendor_more))
                }
            }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/** 嘗試跳轉該廠牌設定頁；全部失敗則開啟 dontkillmyapp 說明頁 */
private fun launchVendorSettings(context: Context, vendor: Vendor) {
    vendor.intentComponents.forEach { (pkg, cls) ->
        runCatching {
            context.startActivity(
                Intent().setComponent(ComponentName(pkg, cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
    }
    openDontKillMyApp(context, vendor)
}

private fun openDontKillMyApp(context: Context, vendor: Vendor?) {
    val url = if (vendor == null) {
        "https://dontkillmyapp.com/"
    } else {
        "https://dontkillmyapp.com/${vendor.slug}"
    }
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
