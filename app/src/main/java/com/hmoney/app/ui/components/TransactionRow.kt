package com.hmoney.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hmoney.app.R
import com.hmoney.app.domain.model.Category
import com.hmoney.app.domain.model.MoneyTransaction
import com.hmoney.app.domain.model.TransactionType
import com.hmoney.app.ui.common.Formats
import com.hmoney.app.ui.common.IconMapper

/**
 * 交易列：分類 icon、備註／分類名、帳戶資訊與金額（依類型變色）。
 */
@Composable
fun TransactionRow(
    transaction: MoneyTransaction,
    category: Category?,
    accountName: String?,
    toAccountName: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = category?.let { IconMapper.iconOf(it.iconKey) }
                ?: IconMapper.iconOf("send"),
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color(
                category?.colorArgb ?: 0xFF78909C,
            ),
            modifier = Modifier.size(28.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = when (transaction.type) {
                    TransactionType.TRANSFER ->
                        stringResource(
                            R.string.transactions_transfer_to,
                            toAccountName ?: "",
                        )
                    else -> category?.name ?: ""
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            val subtitle = buildList {
                add(Formats.date(transaction.dateEpochMillis))
                accountName?.let { add(it) }
                if (transaction.note.isNotBlank()) add(transaction.note)
                if (transaction.feeCents > 0) {
                    add(
                        stringResource(
                            R.string.transactions_fee_note,
                            Formats.money(transaction.feeCents),
                        ),
                    )
                }
            }.joinToString(" · ")
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = when (transaction.type) {
                TransactionType.EXPENSE -> "-" + Formats.money(transaction.amountCents)
                TransactionType.INCOME -> "+" + Formats.money(transaction.amountCents)
                TransactionType.TRANSFER -> Formats.money(transaction.amountCents)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when (transaction.type) {
                TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
                TransactionType.INCOME -> MaterialTheme.colorScheme.tertiary
                TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
