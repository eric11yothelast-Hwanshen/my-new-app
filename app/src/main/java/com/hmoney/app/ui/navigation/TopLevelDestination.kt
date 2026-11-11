package com.hmoney.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.hmoney.app.R

/**
 * 底部導覽五個頂層頁面：總覽、明細、統計、帳戶、設定。
 */
enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    OVERVIEW("overview", Icons.Outlined.Home, R.string.tab_overview),
    TRANSACTIONS("transactions", Icons.Outlined.ReceiptLong, R.string.tab_transactions),
    STATS("stats", Icons.Outlined.PieChart, R.string.tab_stats),
    ACCOUNTS("accounts", Icons.Outlined.AccountBalanceWallet, R.string.tab_accounts),
    SETTINGS("settings", Icons.Outlined.Settings, R.string.tab_settings),
}
