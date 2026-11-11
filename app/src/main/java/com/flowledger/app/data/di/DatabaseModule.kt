package com.flowledger.app.data.di

import android.content.Context
import androidx.room.Room
import com.flowledger.app.data.FlowLedgerDatabase
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFlowLedgerDatabase(
        @ApplicationContext context: Context
    ): FlowLedgerDatabase {
        return Room.databaseBuilder(
            context,
            FlowLedgerDatabase::class.java,
            "flow_ledger.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: FlowLedgerDatabase): TransactionDao {
        return database.transactionDao()
    }

    @Provides
    @Singleton
    fun provideAccountDao(database: FlowLedgerDatabase): AccountDao {
        return database.accountDao()
    }
}
