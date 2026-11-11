package com.hmoney.app

import android.app.Application
import com.hmoney.app.di.ApplicationScope
import com.hmoney.app.domain.repository.RecurringRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 應用程式進入點：Hilt 全域元件由此建立。
 * 啟動時檢查週期交易是否到期，到期者自動補產交易記錄
 * （推播通知與背景排程於「通知與防後台終止」階段加入）。
 */
@HiltAndroidApp
class HmoneyApp : Application() {

    @Inject lateinit var recurringRepository: RecurringRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { recurringRepository.generateDue() }
        }
    }
}
