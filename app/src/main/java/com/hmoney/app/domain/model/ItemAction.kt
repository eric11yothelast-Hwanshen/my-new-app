package com.hmoney.app.domain.model

/**
 * S47 AI 聊天室物品操作意圖（純資料類）：由 AiRepository.parseItemAction 解析產生，
 * 僅供「確認卡 → 使用者確認」流程使用——確認前嚴禁寫入 DB（物品寫入一律走 ItemRepository）。
 * name 必要；其餘欄位可空＝使用者訊息未提及。
 */
enum class ItemActionType { CREATE, UPDATE, DELETE }

data class ItemActionIntent(
    val action: ItemActionType,
    val name: String,
    val storageLocation: String? = null,
    val quantity: Int? = null,
    val expiryDateEpochMillis: Long? = null,
    /** S49 相對「現有到期日」的增減天數（可為負＝提前／縮短）；由 VM 在名稱匹配後換算為絕對日期 */
    val expiryDeltaDays: Int? = null,
)
