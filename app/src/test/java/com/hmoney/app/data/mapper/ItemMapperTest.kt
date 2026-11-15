package com.hmoney.app.data.mapper

import com.hmoney.app.data.db.entity.ItemEntity
import com.hmoney.app.data.db.entity.TransactionEntity
import com.hmoney.app.domain.model.Item
import com.hmoney.app.domain.model.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.3.0 資料層 mapper 測試（Session 38）：
 * Item entity ↔ domain 往返一致；TransactionEntity 新欄位（source/barcode/receiptImagePath）
 * 預設值與未知 source 字串的容錯轉換。
 */
class ItemMapperTest {

    @Test
    fun `Item entity 與 domain 往返欄位一致`() {
        val entity = ItemEntity(
            id = 7L,
            name = "鮮奶",
            barcode = "4711234567890",
            storageLocation = "冰箱",
            quantity = 3,
            expiryDateEpochMillis = 1_735_689_600_000L,
            purchaseTransactionId = 42L,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L,
        )
        val roundTrip = entity.toDomain().toEntity()
        assertEquals(entity, roundTrip)
    }

    @Test
    fun `Item 可為空的欄位往返保持 null`() {
        val entity = ItemEntity(
            name = "未知物品",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        val domain = entity.toDomain()
        assertNull(domain.barcode)
        assertNull(domain.storageLocation)
        assertNull(domain.expiryDateEpochMillis)
        assertNull(domain.purchaseTransactionId)
        assertEquals(1, domain.quantity)
        assertEquals(entity, domain.toEntity())
    }

    @Test
    fun `交易新欄位 entity 轉 domain 預設為 manual 且 null 保持 null`() {
        val entity = TransactionEntity(
            type = "EXPENSE",
            amountCents = 100L,
            accountId = 1L,
            dateEpochMillis = 123L,
        )
        val domain = entity.toDomain()
        assertEquals(TransactionSource.MANUAL, domain.source)
        assertNull(domain.barcode)
        assertNull(domain.receiptImagePath)
        assertEquals("manual", domain.toEntity().source)
    }

    @Test
    fun `交易來源標記往返 ai_image 與條碼欄位一致`() {
        val domain = TransactionEntity(
            type = "EXPENSE",
            amountCents = 250L,
            accountId = 1L,
            dateEpochMillis = 456L,
            source = "ai_image",
            barcode = "1234567890123",
            receiptImagePath = "/tmp/receipt.jpg",
        ).toDomain()
        assertEquals(TransactionSource.AI_IMAGE, domain.source)
        assertEquals("1234567890123", domain.barcode)
        assertEquals("/tmp/receipt.jpg", domain.receiptImagePath)
        val entity = domain.toEntity()
        assertEquals("ai_image", entity.source)
        assertEquals("1234567890123", entity.barcode)
        assertEquals("/tmp/receipt.jpg", entity.receiptImagePath)
    }

    @Test
    fun `未知來源字串容錯為 manual`() {
        val domain = TransactionEntity(
            type = "EXPENSE",
            amountCents = 1L,
            accountId = 1L,
            dateEpochMillis = 1L,
            source = "unknown_future",
        ).toDomain()
        assertEquals(TransactionSource.MANUAL, domain.source)
    }
}
