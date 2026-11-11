package com.flowledger.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.entity.AccountEntity
import com.flowledger.app.data.entity.TransactionEntity

@Database(entities = [AccountEntity::class, TransactionEntity::class], version = 1, exportSchema = false)
abstract class FlowLedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
}
