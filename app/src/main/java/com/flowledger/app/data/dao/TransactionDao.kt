package com.flowledger.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowledger.app.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp_epoch DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId ORDER BY timestamp_epoch DESC")
    fun observeByAccount(accountId: String): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE flow_direction = 'INFLOW' AND is_excluded = 0 AND timestamp_epoch >= :monthStartEpoch AND timestamp_epoch <= :monthEndEpoch")
    fun observeMonthlyInflow(monthStartEpoch: Long, monthEndEpoch: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE flow_direction = 'OUTFLOW' AND is_excluded = 0 AND timestamp_epoch >= :monthStartEpoch AND timestamp_epoch <= :monthEndEpoch")
    fun observeMonthlyOutflow(monthStartEpoch: Long, monthEndEpoch: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE flow_direction = 'INFLOW' AND is_excluded = 0 AND timestamp_epoch >= :monthStartEpoch AND timestamp_epoch <= :monthEndEpoch")
    suspend fun getMonthlyInflowOnce(monthStartEpoch: Long, monthEndEpoch: Long): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE flow_direction = 'OUTFLOW' AND is_excluded = 0 AND timestamp_epoch >= :monthStartEpoch AND timestamp_epoch <= :monthEndEpoch")
    suspend fun getMonthlyOutflowOnce(monthStartEpoch: Long, monthEndEpoch: Long): Double

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(transaction: TransactionEntity): Long

    @Query("UPDATE transactions SET is_excluded = :excluded WHERE transaction_id = :transactionId")
    suspend fun setExcluded(transactionId: String, excluded: Boolean)
}
