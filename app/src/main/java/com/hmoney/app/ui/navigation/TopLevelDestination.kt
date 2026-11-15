package com.hmoney.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.ui.graphics.vector.ImageVector
import com.hmoney.app.R

/**
 * 底部導覽四個頂層頁面：總覽、明細、統計、帳戶。
 * 設定頁已移出底部分頁（各頁右上角齒輪進入），route 保留供導航。
 * 請勿誤改：設定 route 以 SETTINGS_ROUTE 為準，勿再加回 SETTINGS 分頁。
 */
const val SETTINGS_ROUTE = "settings"
const val ITEMS_ROUTE = "items"

enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    OVERVIEW("overview", Icons.Outlined.Home, R.string.tab_overview),
    TRANSACTIONS("transactions", Icons.Outlined.ReceiptLong, R.string.tab_transactions),
    STATS("stats", Icons.Outlined.PieChart, R.string.tab_stats),
    ITEMS("items", Icons.Outlined.Inventory2, R.string.tab_items),
    ACCOUNTS("accounts", Icons.Outlined.AccountBalanceWallet, R.string.tab_accounts),
}

/**
 * S50 頂層分頁 route→index 唯一映射函式：一律以「過濾後清單」計算（inventoryEnabled 關閉時
 * 物品分頁不顯示），tabCount／selectedTabIndex／onSelect 全部走同一清單，嚴禁 hardcode index。
 * route 不在清單時回 0（非頂層頁 fallback，與既有行為一致）。
 */
fun topLevelTabIndex(destinations: List<TopLevelDestination>, route: String?): Int =
    destinations.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0
