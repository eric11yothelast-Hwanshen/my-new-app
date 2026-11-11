package com.hmoney.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hmoney.app.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

/** 帳戶 DAO */
@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isHidden = 0 ORDER BY sortOrder, id")
    fun observeVisible(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
