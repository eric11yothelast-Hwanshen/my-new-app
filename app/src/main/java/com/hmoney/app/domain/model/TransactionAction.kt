package com.hmoney.app.domain.model

/**
 * S48 AI 聊天室交易刪改意圖（純資料類）：由 AiRepository.parseTransactionAction 解析產生，
 * 僅供「候選／確認卡 → 使用者確認」流程使用——確認前嚴禁寫入 DB。
 * target 重用既有 TransactionQuery 欄位描述要找哪筆交易；
 * UPDATE 另帶 updates（金額／備註／分類／帳戶／日期，皆可空＝不更新該欄位）。
 * 非操作語意回 null（續走物品操作／記帳／查詢／一般 chat）。
 */
enum class TransactionActionType { DELETE, UPDATE }

data class TransactionUpdates(
    val amountCents: Long? = null,
    val note: String? = null,
    val categoryName: String? = null,
    val accountName: String? = null,
    val dateEpochMillis: Long? = null,
)

data class TransactionActionIntent(
    val action: TransactionActionType,
    val target: TransactionQuery = TransactionQuery(limit = 5),
    val updates: TransactionUpdates? = null,
    /** S49 空泛刪除語意（刪除記帳／最新一筆等無標的資訊）＝取最近 5 筆時間倒序 */
    val recent: Boolean = false,
)
