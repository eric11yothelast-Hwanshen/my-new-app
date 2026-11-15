package com.hmoney.app.domain.logic

import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * 草稿確認寫入唯一路徑（S44 自 EntryViewModel.confirmAll 抽出）：
 * 逐筆寫入 TransactionRepository，回傳成功筆數；單筆失敗不中斷其餘（維持既有 runCatching 行為）。
 * EntryViewModel 與 AiChatViewModel 的「全部確認」都必須走這裡——寫入前必經使用者確認的鐵律不變。
 */
class ConfirmDraftsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
) {
    suspend fun execute(transactions: List<MoneyTransaction>): Int {
        var written = 0
        transactions.forEach { transaction ->
            runCatching { transactionRepository.addTransaction(transaction) }
                .onSuccess { written++ }
        }
        return written
    }
}
