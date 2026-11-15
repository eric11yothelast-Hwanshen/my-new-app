package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hmoney.app.data.db.entity.ItemEntity
import kotlinx.coroutines.flow.Flow

/** 物品管理 DAO：條碼搜尋與 CRUD */ 
@Dao
interface ItemDao {

    @Query("SELECT * FROM items ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE barcode = :barcode ORDER BY updatedAtEpochMillis DESC")
    suspend fun findByBarcode(barcode: String): List<ItemEntity>

    /** S46 唯讀：到期日早於（含）指定時間的物品（供到期提醒工作檢索） */
    @Query(
        "SELECT * FROM items WHERE expiryDateEpochMillis IS NOT NULL " +
            "AND expiryDateEpochMillis <= :endEpochMillis ORDER BY expiryDateEpochMillis ASC",
    )
    suspend fun findWithExpiryBefore(endEpochMillis: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun findById(id: Long): ItemEntity?

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
