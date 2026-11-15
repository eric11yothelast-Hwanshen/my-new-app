package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.PurchaseHistoryRow
import com.hmoney.app.data.db.dao.TransactionDao
import com.hmoney.app.domain.logic.computePriceTrend
import com.hmoney.app.domain.model.PriceTrend
import com.hmoney.app.domain.model.PurchaseSummary
import com.hmoney.app.domain.repository.BarcodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 條碼查詢儲存實作（S40，唯讀——嚴禁任何 DB 寫入） */
@Singleton
class BarcodeRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
) : BarcodeRepository {

    override fun getPurchaseHistory(barcode: String): Flow<List<PurchaseSummary>> =
        transactionDao.observePurchaseHistory(barcode.trim()).map { rows ->
            rows.map { it.toDomain() }
        }

    override fun getPriceTrend(barcode: String): Flow<PriceTrend> =
        getPurchaseHistory(barcode).map { summaries -> computePriceTrend(summaries) }

    private fun PurchaseHistoryRow.toDomain(): PurchaseSummary = PurchaseSummary(
        amountCents = amountCents,
        dateEpochMillis = dateEpochMillis,
        note = note,
        accountName = accountName,
    )
}
