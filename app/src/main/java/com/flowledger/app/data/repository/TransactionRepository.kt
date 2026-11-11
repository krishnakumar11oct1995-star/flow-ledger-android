package com.flowledger.app.data.repository

import androidx.room.Transaction
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.entity.AccountEntity
import com.flowledger.app.data.entity.TransactionEntity
import com.flowledger.app.data.model.ParsedTransaction
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import javax.inject.Inject

class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) {
    @Transaction
    suspend fun recordTransaction(parsed: ParsedTransaction, rawSender: String, rawBody: String) {
        val transactionId = generateHash("$rawSender|${parsed.timestamp}|$rawBody")
        val accountId = "${parsed.bankCode}_${parsed.accountLast4}"

        val transactionEntity = TransactionEntity(
            transactionId = transactionId,
            accountId = accountId,
            amount = parsed.amount,
            flowDirection = parsed.flowDirection.name,
            balanceAfter = parsed.availableBalance,
            merchantHint = parsed.merchantHint,
            rawSender = rawSender,
            rawBody = rawBody,
            timestampEpoch = parsed.timestamp
        )

        val inserted = transactionDao.insertIfAbsent(transactionEntity)

        if (inserted > 0L && parsed.availableBalance != null) {
            val accountEntity = AccountEntity(
                accountId = accountId,
                bankName = parsed.bankName,
                accountLast4 = parsed.accountLast4,
                currencyCode = parsed.currencyCode,
                currentBalance = parsed.availableBalance,
                lastUpdatedEpoch = parsed.timestamp
            )
            accountDao.upsert(accountEntity)
        }
    }

    fun observeTransactions(): Flow<List<TransactionEntity>> = transactionDao.observeAll()

    fun observeTotalBalance(): Flow<Double> = accountDao.observeTotalBalance()

    fun observeMonthlyInflow(start: Long, end: Long): Flow<Double> =
        transactionDao.observeMonthlyInflow(start, end)

    fun observeMonthlyOutflow(start: Long, end: Long): Flow<Double> =
        transactionDao.observeMonthlyOutflow(start, end)

    suspend fun getTotalBalanceOnce(): Double = accountDao.getTotalBalanceOnce()

    suspend fun getMonthlyInflowOnce(start: Long, end: Long): Double =
        transactionDao.getMonthlyInflowOnce(start, end)

    suspend fun getMonthlyOutflowOnce(start: Long, end: Long): Double =
        transactionDao.getMonthlyOutflowOnce(start, end)

    suspend fun setExcluded(txId: String, excluded: Boolean) {
        transactionDao.setExcluded(txId, excluded)
    }

    private fun generateHash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
