package com.hmoney.app.di

import com.hmoney.app.data.repository.AccountRepositoryImpl
import com.hmoney.app.data.repository.CategoryRepositoryImpl
import com.hmoney.app.data.repository.TransactionRepositoryImpl
import com.hmoney.app.domain.repository.AccountRepository
import com.hmoney.app.domain.repository.CategoryRepository
import com.hmoney.app.domain.repository.TransactionRepository
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
}
