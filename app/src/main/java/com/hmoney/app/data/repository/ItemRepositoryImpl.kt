package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.ItemDao
import com.hmoney.app.data.mapper.toDomain
import com.hmoney.app.data.mapper.toEntity
import com.hmoney.app.domain.model.Item
import com.hmoney.app.domain.repository.ItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 物品管理儲存實作：防呆驗證集中在 Item 建構 */
@Singleton
class ItemRepositoryImpl @Inject constructor(
    private val itemDao: ItemDao,
) : ItemRepository {

    override fun observeAll(): Flow<List<Item>> =
        itemDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findByBarcode(barcode: String): List<Item> =
        itemDao.findByBarcode(barcode).map { it.toDomain() }

    override suspend fun addItem(item: Item): Long =
        itemDao.insert(item.toEntity())

    override suspend fun updateItem(item: Item) {
        require(itemDao.findById(item.id) != null) { "物品不存在" }
        itemDao.update(item.toEntity())
    }

    override suspend fun deleteItem(id: Long) {
        itemDao.deleteById(id)
    }
}
