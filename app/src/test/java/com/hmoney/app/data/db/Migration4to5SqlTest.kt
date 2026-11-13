package com.hmoney.app.data.db

import com.hmoney.app.data.db.HmoneyDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DB v4 → v5 遷移 SQL 驗證（Session 38，沙箱無 Android SDK，以結構斷言驗證遷移邏輯）：
 * 既有欄位不動；transactions 新增三欄且 source 預設 'manual'；
 * items 表欄位齊全、quantity 預設 1、FK 刪除設 NULL，並建立兩個索引。
 */
class Migration4to5SqlTest {

    @Test
    fun `遷移共產生 6 條 SQL 語句`() {
        assertEquals(6, HmoneyDatabase.MIGRATION_4_5_SQL.size)
    }

    @Test
    fun `transactions 新增三欄且 source 預設 manual`() {
        val alterStatements = HmoneyDatabase.MIGRATION_4_5_SQL.filter { it.startsWith("ALTER TABLE") }
        assertEquals(3, alterStatements.size)
        assertTrue(alterStatements.any { it.contains("ADD COLUMN `source` TEXT NOT NULL DEFAULT 'manual'") })
        assertTrue(alterStatements.any { it.contains("ADD COLUMN `barcode` TEXT") })
        assertTrue(alterStatements.any { it.contains("ADD COLUMN `receiptImagePath` TEXT") })
    }

    @Test
    fun `items 表欄位齊全且 quantity 預設 1`() {
        val createTable = HmoneyDatabase.MIGRATION_4_5_SQL.first { it.startsWith("CREATE TABLE IF NOT EXISTS `items`") }
        listOf(
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL",
            "`name` TEXT NOT NULL",
            "`barcode` TEXT",
            "`storageLocation` TEXT",
            "`quantity` INTEGER NOT NULL DEFAULT 1",
            "`expiryDateEpochMillis` INTEGER",
            "`purchaseTransactionId` INTEGER",
            "`createdAtEpochMillis` INTEGER NOT NULL",
            "`updatedAtEpochMillis` INTEGER NOT NULL",
            "FOREIGN KEY(`purchaseTransactionId`) REFERENCES `transactions`(`id`)",
            "ON DELETE SET NULL",
        ).forEach { column -> assertTrue("缺少：$column", createTable.contains(column)) }
    }

    @Test
    fun `items 建立 barcode 與到期日兩個索引`() {
        val indexStatements = HmoneyDatabase.MIGRATION_4_5_SQL.filter { it.startsWith("CREATE INDEX") }
        assertEquals(2, indexStatements.size)
        assertTrue(indexStatements.any { it.contains("index_items_barcode") && it.contains("(`barcode`)") })
        assertTrue(indexStatements.any { it.contains("index_items_expiryDateEpochMillis") && it.contains("(`expiryDateEpochMillis`)") })
    }

    @Test
    fun `遷移不觸及既有欄位的破壞性語句`() {
        HmoneyDatabase.MIGRATION_4_5_SQL.forEach { sql ->
            assertTrue("遷移不得包含破壞性語句：$sql", !sql.contains("DROP TABLE", ignoreCase = true))
            assertTrue("遷移不得修改既有欄位：$sql", !sql.trim().startsWith("UPDATE", ignoreCase = true))
        }
    }
}
