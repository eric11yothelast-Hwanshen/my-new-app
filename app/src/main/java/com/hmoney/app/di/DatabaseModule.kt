package com.hmoney.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hmoney.app.data.db.HmoneyDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 資料庫模組：提供 Room 資料庫（啟用 WAL 模式）與各 DAO。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HmoneyDatabase =
        Room.databaseBuilder(context, HmoneyDatabase::class.java, "hmoney.db")
            // WAL 模式：提升讀寫並行效能與資料安全
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(HmoneyDatabase.SEED_CALLBACK)
            .addMigrations(*HmoneyDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideAccountDao(database: HmoneyDatabase) = database.accountDao()

    @Provides
    fun provideCategoryDao(database: HmoneyDatabase) = database.categoryDao()

    @Provides
    fun provideTransactionDao(database: HmoneyDatabase) = database.transactionDao()
}
