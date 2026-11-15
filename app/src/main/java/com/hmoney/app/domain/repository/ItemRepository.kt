package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.Item
import kotlinx.coroutines.flow.Flow

/** 物品管理的領域介面：觀察清單、條碼查找與 CRUD */
interface ItemRepository {

    fun observeAll(): Flow<List<Item>>

    suspend fun findByBarcode(barcode: String): List<Item>

    /** S46 唯讀：到期日早於（含）指定時間的物品（到期提醒工作用；嚴禁接入任何寫入流程） */
    suspend fun findWithExpiryBefore(endEpochMillis: Long): List<Item>

    suspend fun addItem(item: Item): Long

    suspend fun updateItem(item: Item)

    suspend fun deleteItem(id: Long)
}
