package com.hmoney.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * iconKey → Material 圖示對應：分類／帳戶的 icon 以字串存在 DB，於 UI 層對應。
 */
object IconMapper {

    private val map: Map<String, ImageVector> = mapOf(
        "restaurant" to Icons.Outlined.Restaurant,
        "directions_bus" to Icons.Outlined.DirectionsBus,
        "shopping_bag" to Icons.Outlined.ShoppingBag,
        "sports_esports" to Icons.Outlined.SportsEsports,
        "home" to Icons.Outlined.Home,
        "medical_services" to Icons.Outlined.MedicalServices,
        "more_horiz" to Icons.Outlined.MoreHoriz,
        "payments" to Icons.Outlined.Payments,
        "trending_up" to Icons.Outlined.TrendingUp,
        "card_giftcard" to Icons.Outlined.CardGiftcard,
        "add_circle" to Icons.Outlined.AddCircle,
        "account_balance" to Icons.Outlined.AccountBalance,
        "account_balance_wallet" to Icons.Outlined.AccountBalanceWallet,
        "credit_card" to Icons.Outlined.CreditCard,
        "savings" to Icons.Outlined.Savings,
        "send" to Icons.AutoMirrored.Outlined.Send,
    )

    /** 可供新增帳戶挑選的 icon 清單 */
    val accountIconKeys: List<String> = listOf(
        "payments",
        "account_balance",
        "account_balance_wallet",
        "credit_card",
        "savings",
    )

    fun iconOf(key: String): ImageVector = map[key] ?: Icons.Outlined.MoreHoriz
}
