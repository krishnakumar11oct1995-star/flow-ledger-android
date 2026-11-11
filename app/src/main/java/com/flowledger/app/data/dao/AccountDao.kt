package com.flowledger.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowledger.app.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY last_updated_epoch DESC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT COALESCE(SUM(current_balance), 0.0) FROM accounts")
    fun observeTotalBalance(): Flow<Double>

    @Query("SELECT COALESCE(SUM(current_balance), 0.0) FROM accounts")
    suspend fun getTotalBalanceOnce(): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)
}
