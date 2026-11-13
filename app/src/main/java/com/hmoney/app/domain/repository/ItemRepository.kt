package com.hmoney.app.domain.repository

import com.hmoney.app.domain.model.Item
import kotlinx.coroutines.flow.Flow

/** 物品管理的領域介面：觀察清單、條碼查找與 CRUD */
interface ItemRepository {

    fun observeAll(): Flow<List<Item>>

    suspend fun findByBarcode(barcode: String): List<Item>

    suspend fun addItem(item: Item): Long

    suspend fun updateItem(item: Item)

    suspend fun deleteItem(id: Long)
}
