package com.hmoney.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hmoney.app.data.db.dao.AccountDao
import com.hmoney.app.data.db.dao.AiAnalysisDao
import com.hmoney.app.data.db.dao.BudgetDao
import com.hmoney.app.data.db.dao.CategoryDao
import com.hmoney.app.data.db.dao.RecurringTransactionDao
import com.hmoney.app.data.db.dao.TransactionDao
import com.hmoney.app.data.db.entity.AccountEntity
import com.hmoney.app.data.db.entity.AiAnalysisEntity
import com.hmoney.app.data.db.entity.BudgetEntity
import com.hmoney.app.data.db.entity.CategoryEntity
import com.hmoney.app.data.db.entity.RecurringTransactionEntity
import com.hmoney.app.data.db.entity.TransactionEntity

/**
 * Room 資料庫：啟用 WAL 模式（於 DI 模組設定），
 * 首次建立時種入預設帳戶（現金、1號銀行、2號銀行）與內建分類。
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        RecurringTransactionEntity::class,
        AiAnalysisEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class HmoneyDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun aiAnalysisDao(): AiAnalysisDao

    companion object {
        /** 首次安裝時的種子資料：預設帳戶與內建分類 */
        val SEED_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // 預設帳戶：現金 + 兩個範本銀行帳戶（名稱可改）
                db.execSQL(
                    "INSERT INTO accounts (name, iconKey, colorArgb, initialBalanceCents, currency, isHidden, sortOrder) VALUES " +
                        "('現金', 'payments', ${0xFF43A047}, 0, 'TWD', 0, 0), " +
                        "('1號銀行', 'account_balance', ${0xFF42A5F5}, 0, 'TWD', 0, 1), " +
                        "('2號銀行', 'account_balance', ${0xFF7E57C2}, 0, 'TWD', 0, 2)",
                )
                // 內建支出分類
                db.execSQL(
                    "INSERT INTO categories (name, type, iconKey, colorArgb, isHidden, sortOrder) VALUES " +
                        "('餐飲', 'EXPENSE', 'restaurant', ${0xFFEF5350}, 0, 0), " +
                        "('交通', 'EXPENSE', 'directions_bus', ${0xFF5C6BC0}, 0, 1), " +
                        "('購物', 'EXPENSE', 'shopping_bag', ${0xFFFFB74D}, 0, 2), " +
                        "('娛樂', 'EXPENSE', 'sports_esports', ${0xFFAB47BC}, 0, 3), " +
                        "('居住', 'EXPENSE', 'home', ${0xFF26A69A}, 0, 4), " +
                        "('醫療', 'EXPENSE', 'medical_services', ${0xFFEC407A}, 0, 5), " +
                        "('其他', 'EXPENSE', 'more_horiz', ${0xFF78909C}, 0, 6)",
                )
                // 內建收入分類
                db.execSQL(
                    "INSERT INTO categories (name, type, iconKey, colorArgb, isHidden, sortOrder) VALUES " +
                        "('薪資', 'INCOME', 'payments', ${0xFF66BB6A}, 0, 0), " +
                        "('投資', 'INCOME', 'trending_up', ${0xFF26C6DA}, 0, 1), " +
                        "('獎金', 'INCOME', 'card_giftcard', ${0xFFFFCA28}, 0, 2), " +
                        "('其他收入', 'INCOME', 'add_circle', ${0xFF9CCC65}, 0, 3)",
                )
            }
        }

        /** 版本 1 → 2：新增預算與週期交易資料表 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets`" +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                        " `type` TEXT NOT NULL," +
                        " `categoryId` INTEGER," +
                        " `amountCents` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_transactions`" +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                        " `type` TEXT NOT NULL," +
                        " `amountCents` INTEGER NOT NULL," +
                        " `accountId` INTEGER NOT NULL," +
                        " `categoryId` INTEGER NOT NULL," +
                        " `note` TEXT NOT NULL," +
                        " `frequency` TEXT NOT NULL," +
                        " `dayOfMonth` INTEGER," +
                        " `dayOfWeek` INTEGER," +
                        " `nextDueEpochMillis` INTEGER NOT NULL," +
                        " `isEnabled` INTEGER NOT NULL)",
                )
            }
        }

        /** 版本 2 → 3：新增 AI 分析歷史資料表 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_analysis`" +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                        " `createdAtEpochMillis` INTEGER NOT NULL," +
                        " `query` TEXT NOT NULL," +
                        " `summary` TEXT NOT NULL," +
                        " `insights` TEXT NOT NULL," +
                        " `anomalies` TEXT NOT NULL," +
                        " `tips` TEXT NOT NULL)",
                )
            }
        }

        /** 未來版本升級時擴充此清單 */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
