package com.hmoney.app.domain.model

/**
 * AI 記帳草稿（1.3.0，Session 39）：
 * 僅為「待確認草稿」，服務層嚴禁直接寫入 DB——寫入一律走之後 session 的
 * 「草稿預覽 → 使用者確認」流程（防幻覺亂寫的底線）。
 * 欄位缺失時以預設值產出（categoryName 對不上既有分類時為 null，由 UI fallback「其他」），
 * 讓確認流程補齊。
 */
data class TransactionDraft(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountCents: Long = 0L,
    val note: String = "",
    val categoryName: String? = null,
    val accountName: String? = null,
    /** 模型只給自然語言日期時由服務層解析；無法解析時為 null（確認流程補） */
    val dateEpochMillis: Long? = null,
    val tags: List<String> = emptyList(),
    /** 原始對應片段（debug 用） */
    val rawText: String = "",
)
