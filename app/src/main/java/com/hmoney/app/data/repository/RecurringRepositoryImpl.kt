package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.RecurringTransactionDao
import com.hmoney.app.data.db.entity.RecurringTransactionEntity
import com.hmoney.app.domain.logic.RecurringScheduler
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.RecurringFrequency
import com.hmoney.app.domain.model.RecurringTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.domain.repository.RecurringRepository
import com.hmoney.app.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 週期交易倉儲實作：到期自動產生交易記錄（補產過去到期者），
 * 並推進規則的下次到期時間。
 */
@Singleton
class RecurringRepositoryImpl @Inject constructor(
    private val recurringDao: RecurringTransactionDao,
    private val transactionRepository: TransactionRepository,
    private val scheduler: RecurringScheduler,
) : RecurringRepository {

    private fun RecurringTransactionEntity.toDomain(): RecurringTransaction =
        RecurringTransaction(
            id = id,
            type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
            amountCents = amountCents,
            accountId = accountId,
            categoryId = categoryId,
            note = note,
            frequency = runCatching { RecurringFrequency.valueOf(frequency) }
                .getOrDefault(RecurringFrequency.MONTHLY),
            dayOfMonth = dayOfMonth,
            dayOfWeek = dayOfWeek,
            nextDueEpochMillis = nextDueEpochMillis,
            isEnabled = isEnabled,
        )

    private fun RecurringTransaction.toEntity(): RecurringTransactionEntity =
        RecurringTransactionEntity(
            id = id,
            type = type.name,
            amountCents = amountCents,
            accountId = accountId,
            categoryId = categoryId,
            note = note,
            frequency = frequency.name,
            dayOfMonth = dayOfMonth,
            dayOfWeek = dayOfWeek,
            nextDueEpochMillis = nextDueEpochMillis,
            isEnabled = isEnabled,
        )

    override fun observeAll(): Flow<List<RecurringTransaction>> =
        recurringDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): RecurringTransaction? =
        recurringDao.getById(id)?.toDomain()

    override suspend fun addRecurring(recurring: RecurringTransaction): Long =
        recurringDao.insert(recurring.toEntity())

    override suspend fun updateRecurring(recurring: RecurringTransaction) {
        require(recurringDao.getById(recurring.id) != null) { "週期規則不存在" }
        recurringDao.update(recurring.toEntity())
    }

    override suspend fun deleteRecurring(id: Long) {
        recurringDao.deleteById(id)
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        val entity = recurringDao.getById(id) ?: return
        recurringDao.update(entity.copy(isEnabled = enabled))
    }

    override suspend fun generateDue(): Int {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        var generated = 0
        recurringDao.getDue(now).forEach { entity ->
            val recurring = entity.toDomain()
            // 產生所有已到期的期別（含補產過去）
            scheduler.dueTimes(recurring, now, zone).forEach { dueMillis ->
                transactionRepository.addTransaction(
                    MoneyTransaction(
                        type = recurring.type,
                        amountCents = recurring.amountCents,
                        accountId = recurring.accountId,
                        categoryId = recurring.categoryId,
                        dateEpochMillis = dueMillis,
                        note = recurring.note,
                    ),
                )
                generated++
            }
            // 推進下次到期時間到未來
            recurringDao.update(
                entity.copy(nextDueEpochMillis = scheduler.nextDueAfter(recurring, now, zone)),
            )
        }
        return generated
    }
}
