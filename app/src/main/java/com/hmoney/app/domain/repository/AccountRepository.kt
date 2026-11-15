package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.Account
import kotlinx.coroutines.flow.Flow

/** 帳戶與即時餘額的領域介面 */
interface AccountRepository {

    /** 觀察可見帳戶（含各自即時餘額） */
    fun observeVisibleWithBalance(): Flow<List<Pair<Account, Long>>>

    /** 觀察全部帳戶（含隱藏，含各自即時餘額） */
    fun observeAllWithBalance(): Flow<List<Pair<Account, Long>>>

    /** 觀察全部帳戶（含隱藏） */
    fun observeAll(): Flow<List<Account>>

    suspend fun getAccount(id: Long): Account?

    /** 新增帳戶（名稱不可空白） */
    suspend fun addAccount(account: Account): Long

    /** 更新帳戶（名稱、icon、顏色、期初餘額、幣別） */
    suspend fun updateAccount(account: Account)

    /** 隱藏帳戶（有交易記錄的帳戶只能隱藏，不得實刪） */
    suspend fun hideAccount(id: Long)

    /**
     * 刪除帳戶：僅無交易記錄者可刪。
     * @return true=已刪除；false=有交易記錄，僅能隱藏
     */
    suspend fun deleteAccount(id: Long): Boolean

    /** 全部可見帳戶的總餘額 */
    fun observeTotalBalance(): Flow<Long>
}
