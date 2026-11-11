package com.flowledger.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "account_id")
    val accountId: String,
    
    @ColumnInfo(name = "bank_name")
    val bankName: String,
    
    @ColumnInfo(name = "account_last4")
    val accountLast4: String,
    
    @ColumnInfo(name = "currency_code", defaultValue = "INR")
    val currencyCode: String = "INR",
    
    @ColumnInfo(name = "current_balance")
    val currentBalance: Double,
    
    @ColumnInfo(name = "last_updated_epoch")
    val lastUpdatedEpoch: Long
)
