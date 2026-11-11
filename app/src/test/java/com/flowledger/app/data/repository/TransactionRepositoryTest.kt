package com.flowledger.app.data.repository

import app.cash.turbine.test
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.entity.AccountEntity
import com.flowledger.app.data.entity.TransactionEntity
import com.flowledger.app.data.model.FlowDirection
import com.flowledger.app.data.model.ParsedTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TransactionRepository Reactive Turbine Tests")
class TransactionRepositoryTest {

    private lateinit var fakeTransactionDao: FakeTransactionDao
    private lateinit var fakeAccountDao: FakeAccountDao
    private lateinit var repository: TransactionRepository

    @BeforeEach
    fun setUp() {
        fakeTransactionDao = FakeTransactionDao()
        fakeAccountDao = FakeAccountDao()
        repository = TransactionRepository(fakeTransactionDao, fakeAccountDao)
    }

    @Test
    @DisplayName("Initial state: all flows emit zero / empty lists")
    fun `should emit initial default values when database is empty`() = runTest {
        repository.observeTotalBalance().test {
            assertEquals(0.0, awaitItem())
        }

        repository.observeMonthlyInflow(0L, Long.MAX_VALUE).test {
            assertEquals(0.0, awaitItem())
        }

        repository.observeMonthlyOutflow(0L, Long.MAX_VALUE).test {
            assertEquals(0.0, awaitItem())
        }

        repository.observeTransactions().test {
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    @DisplayName("Record Debit: triggers InvalidationTracker and re-emits updated balance, outflow, and transaction list")
    fun `recording debit transaction should reactively update balance, outflow and transactions list`() = runTest {
        val timestamp = System.currentTimeMillis()
        val parsed = ParsedTransaction(
            amount = 5400.00,
            flowDirection = FlowDirection.OUTFLOW,
            accountLast4 = "4092",
            availableBalance = 42100.50,
            bankCode = "HDFC",
            bankName = "HDFC Bank",
            currencyCode = "INR",
            merchantHint = "Amazon",
            timestamp = timestamp
        )

        repository.observeTotalBalance().test {
            assertEquals(0.0, awaitItem()) // Initial

            repository.recordTransaction(
                parsed = parsed,
                rawSender = "VK-HDFCBK",
                rawBody = "Rs 5,400.00 debited from a/c **4092. Bal: Rs 42,100.50"
            )

            assertEquals(42100.50, awaitItem()) // Reactively updated
        }

        repository.observeMonthlyOutflow(timestamp - 1000, timestamp + 1000).test {
            assertEquals(5400.00, awaitItem())
        }

        repository.observeTransactions().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("HDFC_4092", list[0].accountId)
            assertEquals(5400.00, list[0].amount)
            assertEquals("OUTFLOW", list[0].flowDirection)
            assertEquals(42100.50, list[0].balanceAfter)
            assertEquals("Amazon", list[0].merchantHint)
        }
    }

    @Test
    @DisplayName("Record Credit: reactively updates inflow and balance")
    fun `recording credit transaction should reactively update inflow and balance`() = runTest {
        val timestamp = System.currentTimeMillis()
        val parsedCredit = ParsedTransaction(
            amount = 12000.00,
            flowDirection = FlowDirection.INFLOW,
            accountLast4 = "102",
            availableBalance = 85400.00,
            bankCode = "ICICI",
            bankName = "ICICI Bank",
            currencyCode = "INR",
            merchantHint = "Salary",
            timestamp = timestamp
        )

        repository.observeMonthlyInflow(timestamp - 1000, timestamp + 1000).test {
            assertEquals(0.0, awaitItem())

            repository.recordTransaction(
                parsed = parsedCredit,
                rawSender = "AD-ICICIB",
                rawBody = "Acct XX102 credited with INR 12,000.00 by Salary. Avl Bal INR 85,400.00"
            )

            assertEquals(12000.00, awaitItem())
        }

        repository.observeTotalBalance().test {
            assertEquals(85400.00, awaitItem())
        }
    }

    @Test
    @DisplayName("Idempotency: duplicate SMS produces identical SHA-256 and is ignored")
    fun `recording duplicate SMS should be silently ignored and not double count`() = runTest {
        val timestamp = System.currentTimeMillis()
        val parsed = ParsedTransaction(
            amount = 500.00,
            flowDirection = FlowDirection.OUTFLOW,
            accountLast4 = "4092",
            availableBalance = 41600.50,
            bankCode = "HDFC",
            bankName = "HDFC Bank",
            currencyCode = "INR",
            merchantHint = "Uber",
            timestamp = timestamp
        )

        val rawSender = "VK-HDFCBK"
        val rawBody = "Rs 500.00 debited from a/c **4092. Bal: Rs 41,600.50"

        // First insert
        repository.recordTransaction(parsed, rawSender, rawBody)
        // Duplicate insert (e.g. telecom retry / re-broadcast)
        repository.recordTransaction(parsed, rawSender, rawBody)

        repository.observeTransactions().test {
            val list = awaitItem()
            assertEquals(1, list.size, "Duplicate transaction must be ignored by OnConflictStrategy.IGNORE")
        }

        repository.observeMonthlyOutflow(timestamp - 1000, timestamp + 1000).test {
            assertEquals(500.00, awaitItem(), "Outflow must not be double-counted")
        }
    }

    @Test
    @DisplayName("Exclude from Flow: toggling is_excluded immediately recalculates monthly aggregates")
    fun `excluding a transaction should reactively recalculate monthly flow without polling`() = runTest {
        val timestamp = System.currentTimeMillis()
        val parsed1 = ParsedTransaction(
            amount = 3000.00,
            flowDirection = FlowDirection.OUTFLOW,
            accountLast4 = "4092",
            availableBalance = 38600.50,
            bankCode = "HDFC",
            bankName = "HDFC Bank",
            currencyCode = "INR",
            merchantHint = "Self Transfer",
            timestamp = timestamp
        )

        repository.recordTransaction(
            parsed = parsed1,
            rawSender = "VK-HDFCBK",
            rawBody = "Rs 3,000.00 debited from a/c **4092 to Self. Bal: Rs 38,600.50"
        )

        var txId = ""
        repository.observeTransactions().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertFalse(list[0].isExcluded)
            txId = list[0].transactionId
        }

        repository.observeMonthlyOutflow(timestamp - 1000, timestamp + 1000).test {
            assertEquals(3000.00, awaitItem()) // Before exclusion

            // User swipes to exclude internal transfer
            repository.setExcluded(txId, true)

            assertEquals(0.0, awaitItem()) // Reactively recalculated to 0
        }

        repository.observeTransactions().test {
            val list = awaitItem()
            assertTrue(list[0].isExcluded)
        }
    }
}

// =============================================================================
// FAKE IN-MEMORY DAOS SIMULATING ROOM REACTIVE FLOWS & INVALIDATION TRACKER
// =============================================================================

class FakeTransactionDao : TransactionDao {
    private val transactionsFlow = MutableStateFlow<List<TransactionEntity>>(emptyList())

    override fun observeAll(): Flow<List<TransactionEntity>> =
        transactionsFlow.map { list -> list.sortedByDescending { it.timestampEpoch } }

    override fun observeByAccount(accountId: String): Flow<List<TransactionEntity>> =
        transactionsFlow.map { list ->
            list.filter { it.accountId == accountId }.sortedByDescending { it.timestampEpoch }
        }

    override fun observeMonthlyInflow(monthStartEpoch: Long, monthEndEpoch: Long): Flow<Double> =
        transactionsFlow.map { list ->
            list.filter {
                it.flowDirection == "INFLOW" &&
                    !it.isExcluded &&
                    it.timestampEpoch in monthStartEpoch until monthEndEpoch
            }.sumOf { it.amount }
        }

    override fun observeMonthlyOutflow(monthStartEpoch: Long, monthEndEpoch: Long): Flow<Double> =
        transactionsFlow.map { list ->
            list.filter {
                it.flowDirection == "OUTFLOW" &&
                    !it.isExcluded &&
                    it.timestampEpoch in monthStartEpoch until monthEndEpoch
            }.sumOf { it.amount }
        }

    override suspend fun getMonthlyInflowOnce(monthStartEpoch: Long, monthEndEpoch: Long): Double =
        transactionsFlow.value.filter {
            it.flowDirection == "INFLOW" &&
                !it.isExcluded &&
                it.timestampEpoch in monthStartEpoch until monthEndEpoch
        }.sumOf { it.amount }

    override suspend fun getMonthlyOutflowOnce(monthStartEpoch: Long, monthEndEpoch: Long): Double =
        transactionsFlow.value.filter {
            it.flowDirection == "OUTFLOW" &&
                !it.isExcluded &&
                it.timestampEpoch in monthStartEpoch until monthEndEpoch
        }.sumOf { it.amount }

    override suspend fun insertIfAbsent(transaction: TransactionEntity): Long {
        val current = transactionsFlow.value
        if (current.any { it.transactionId == transaction.transactionId }) {
            return -1L // IGNORE duplicate
        }
        transactionsFlow.update { it + transaction }
        return 1L
    }

    override suspend fun setExcluded(transactionId: String, excluded: Boolean) {
        transactionsFlow.update { list ->
            list.map {
                if (it.transactionId == transactionId) it.copy(isExcluded = excluded) else it
            }
        }
    }
}

class FakeAccountDao : AccountDao {
    private val accountsFlow = MutableStateFlow<Map<String, AccountEntity>>(emptyMap())

    override fun observeAll(): Flow<List<AccountEntity>> =
        accountsFlow.map { it.values.sortedByDescending { acc -> acc.lastUpdatedEpoch } }

    override fun observeTotalBalance(): Flow<Double> =
        accountsFlow.map { it.values.sumOf { acc -> acc.currentBalance } }

    override suspend fun getTotalBalanceOnce(): Double =
        accountsFlow.value.values.sumOf { it.currentBalance }

    override suspend fun upsert(account: AccountEntity) {
        accountsFlow.update { map ->
            map + (account.accountId to account)
        }
    }
}
