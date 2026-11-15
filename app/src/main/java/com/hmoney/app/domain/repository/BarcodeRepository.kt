package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.PriceTrend
import com.hmoney.app.domain.model.PurchaseSummary
import kotlinx.coroutines.flow.Flow

/** 條碼查詢領域介面（S40，唯讀——嚴禁任何 DB 寫入） */
interface BarcodeRepository {

    /** 同條碼歷史交易摘要（金額／日期／備註／帳戶名），日期新→舊 */
    fun getPurchaseHistory(barcode: String): Flow<List<PurchaseSummary>>

    /** 同條碼價格趨勢（計算由 domain 純函式負責） */
    fun getPriceTrend(barcode: String): Flow<PriceTrend>
}
