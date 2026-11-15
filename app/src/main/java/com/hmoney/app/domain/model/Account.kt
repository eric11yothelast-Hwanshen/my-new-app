package com.hmoney.app.domain.model

/**
 * 帳戶：所有金額以「分」為單位的 Long 儲存，避免浮點誤差。
 */
data class Account(
    val id: Long = 0L,
    val name: String,
    val iconKey: String,
    val colorArgb: Long,
    val initialBalanceCents: Long = 0L,
    val currency: String = "TWD",
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
)
