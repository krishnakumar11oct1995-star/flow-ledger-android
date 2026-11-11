package com.flowledger.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["account_id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["account_id"]),
        Index(value = ["timestamp_epoch"]),
        Index(value = ["flow_direction"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "transaction_id")
    val transactionId: String,
    
    @ColumnInfo(name = "account_id")
    val accountId: String,
    
    @ColumnInfo(name = "amount")
    val amount: Double,
    
    @ColumnInfo(name = "flow_direction")
    val flowDirection: String,
    
    @ColumnInfo(name = "balance_after")
    val balanceAfter: Double?,
    
    @ColumnInfo(name = "merchant_hint")
    val merchantHint: String?,
    
    @ColumnInfo(name = "raw_sender")
    val rawSender: String,
    
    @ColumnInfo(name = "raw_body")
    val rawBody: String,
    
    @ColumnInfo(name = "timestamp_epoch")
    val timestampEpoch: Long,
    
    @ColumnInfo(name = "is_excluded", defaultValue = "false")
    val isExcluded: Boolean = false,
    
    @ColumnInfo(name = "created_at_epoch")
    val createdAtEpoch: Long = System.currentTimeMillis()
)
