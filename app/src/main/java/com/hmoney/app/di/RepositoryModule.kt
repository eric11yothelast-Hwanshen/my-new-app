package com.hmoney.app.di

import com.hmoney.app.data.repository.AccountRepositoryImpl
import com.hmoney.app.data.repository.AiRepositoryImpl
import com.hmoney.app.data.repository.BarcodeRepositoryImpl
import com.hmoney.app.data.repository.BudgetRepositoryImpl
import com.hmoney.app.data.repository.CategoryRepositoryImpl
import com.hmoney.app.data.repository.ChatRepositoryImpl
import com.hmoney.app.data.repository.ItemRepositoryImpl
import com.hmoney.app.data.repository.ReceiptImageStoreImpl
import com.hmoney.app.data.repository.RecurringRepositoryImpl
import com.hmoney.app.data.repository.SettingsRepositoryImpl
import com.hmoney.app.data.repository.TransactionRepositoryImpl
import com.hmoney.app.data.scanner.MlKitBarcodeScannerSource
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.AiRepository
import com.hmoney.app.domain.repository.BarcodeRepository
import com.hmoney.app.domain.repository.BudgetRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.ChatRepository
import com.hmoney.app.domain.repository.ItemRepository
import com.hmoney.app.data.repository.ReceiptImageStore
import com.hmoney.app.domain.repository.RecurringRepository
import com.hmoney.app.domain.repository.SettingsRepository
import com.hmoney.app.domain.repository.TransactionRepository
import com.hmoney.app.domain.scanner.BarcodeScannerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 倉儲綁定：domain 介面 → data 實作 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindRecurringRepository(impl: RecurringRepositoryImpl): RecurringRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindItemRepository(impl: ItemRepositoryImpl): ItemRepository

    @Binds
    @Singleton
    abstract fun bindBarcodeRepository(impl: BarcodeRepositoryImpl): BarcodeRepository

    // 條碼掃描器（S40）：ML Kit 實作，介面在 domain/scanner（S41 掃描畫面使用）
    @Binds
    @Singleton
    abstract fun bindBarcodeScannerSource(impl: MlKitBarcodeScannerSource): BarcodeScannerSource

    // S41：拍照記帳的憑證圖片解碼／存檔
    @Binds
    @Singleton
    abstract fun bindReceiptImageStore(impl: ReceiptImageStoreImpl): ReceiptImageStore
}
