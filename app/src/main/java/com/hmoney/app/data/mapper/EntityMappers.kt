package com.hmoney.app.data.mapper

import com.hmoney.app.data.db.entity.AccountEntity
import com.hmoney.app.data.db.entity.CategoryEntity
import com.hmoney.app.data.db.entity.TransactionEntity
import com.hmoney.app.domain.model.Account
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.CategoryType
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType

// Entity ↔ Domain 對應：DB 字串列舉在此集中轉換

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    initialBalanceCents = initialBalanceCents,
    currency = currency,
    isHidden = isHidden,
    sortOrder = sortOrder,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    initialBalanceCents = initialBalanceCents,
    currency = currency,
    isHidden = isHidden,
    sortOrder = sortOrder,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = runCatching { CategoryType.valueOf(type) }.getOrDefault(CategoryType.EXPENSE),
    iconKey = iconKey,
    colorArgb = colorArgb,
    isHidden = isHidden,
    sortOrder = sortOrder,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type.name,
    iconKey = iconKey,
    colorArgb = colorArgb,
    isHidden = isHidden,
    sortOrder = sortOrder,
)

fun TransactionEntity.toDomain(): MoneyTransaction = MoneyTransaction(
    id = id,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    amountCents = amountCents,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    feeCents = feeCents,
    dateEpochMillis = dateEpochMillis,
    note = note,
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
)

fun MoneyTransaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = type.name,
    amountCents = amountCents,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    feeCents = feeCents,
    dateEpochMillis = dateEpochMillis,
    note = note,
    tags = tags.joinToString(","),
)
