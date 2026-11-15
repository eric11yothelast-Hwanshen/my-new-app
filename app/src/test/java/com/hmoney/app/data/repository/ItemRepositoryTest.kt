package com.hmoney.app.data.repository

import com.hmoney.app.data.db.dao.ItemDao
import com.hmoney.app.data.db.entity.ItemEntity
import com.hmoney.app.domain.repository.ItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * S46 物品管理：ItemRepository CRUD 與到期查詢（JVM fake DAO 慣例）。
 * 嚴禁寫入交易——物品寫入一律走 ItemRepository，不經 ConfirmDraftsUseCase。
 */
class ItemRepositoryTest {

    private lateinit var dao: FakeItemDao
    private lateinit var repository: ItemRepository

    private fun entity(
        id: Long,
        name: String,
        expiryDateEpochMillis: Long? = null,
        barcode: String? = null,
    ): ItemEntity = ItemEntity(
        id = id,
        name = name,
        barcode = barcode,
        expiryDateEpochMillis = expiryDateEpochMillis,
        createdAtEpochMillis = 1000L,
        updatedAtEpochMillis = 1000L,
    )

    @Before
    fun setUp() {
        dao = FakeItemDao()
        repository = ItemRepositoryImpl(dao)
    }

    @Test
    fun `新增物品寫入並可觀察`() = runTest {
        val now = System.currentTimeMillis()
        repository.addItem(
            com.hmoney.app.domain.model.Item(
                name = "鮮奶",
                quantity = 2,
                expiryDateEpochMillis = now,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        assertEquals(1, dao.rows.size)
        assertEquals("鮮奶", dao.rows.single().name)
    }

    @Test
    fun `更新物品需存在且欄位反映`() = runTest {
        dao.rows += entity(id = 5, name = "舊名")
        repository.updateItem(
            com.hmoney.app.domain.model.Item(
                id = 5,
                name = "新名",
                quantity = 3,
                createdAtEpochMillis = 1000L,
                updatedAtEpochMillis = 2000L,
            ),
        )
        assertEquals("新名", dao.rows.single().name)
        assertEquals(3, dao.rows.single().quantity)
    }

    @Test
    fun `刪除物品`() = runTest {
        dao.rows += entity(id = 7, name = "果汁")
        repository.deleteItem(7)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `到期查詢只含到期日早於等於門檻者且升冪`() = runTest {
        dao.rows += listOf(
            entity(id = 1, name = "遠期", expiryDateEpochMillis = 900L),
            entity(id = 2, name = "無到期日"),
            entity(id = 3, name = "已過期", expiryDateEpochMillis = 100L),
            entity(id = 4, name = "門檻當天", expiryDateEpochMillis = 500L),
            entity(id = 5, name = "更晚", expiryDateEpochMillis = 10_000L),
        )
        val result = repository.findWithExpiryBefore(500L)
        assertEquals(listOf(100L, 500L), result.map { it.expiryDateEpochMillis })
        assertEquals(listOf("已過期", "門檻當天"), result.map { it.name })
    }

    @Test
    fun `依條碼查詢`() = runTest {
        dao.rows += listOf(
            entity(id = 1, name = "鮮奶", barcode = "4711234567890"),
            entity(id = 2, name = "優酪乳", barcode = null),
        )
        val result = repository.findByBarcode("4711234567890")
        assertEquals(listOf("鮮奶"), result.map { it.name })
        assertEquals("4711234567890", result.single().barcode)
    }
}

/** 記憶體版 ItemDao：模擬 SQL 到期查詢行為（唯讀查詢供到期提醒工作） */
private class FakeItemDao : ItemDao {
    val rows = mutableListOf<ItemEntity>()

    override fun observeAll(): Flow<List<ItemEntity>> = MutableStateFlow(rows.toList())

    override suspend fun findByBarcode(barcode: String): List<ItemEntity> =
        rows.filter { it.barcode == barcode }

    override suspend fun findWithExpiryBefore(endEpochMillis: Long): List<ItemEntity> =
        rows.filter { it.expiryDateEpochMillis != null && it.expiryDateEpochMillis <= endEpochMillis }
            .sortedBy { it.expiryDateEpochMillis }

    override suspend fun findById(id: Long): ItemEntity? = rows.firstOrNull { it.id == id }

    override suspend fun insert(item: ItemEntity): Long {
        rows += item
        return item.id
    }

    override suspend fun update(item: ItemEntity) {
        val index = rows.indexOfFirst { it.id == item.id }
        if (index >= 0) rows[index] = item
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }
}
