package com.flowledger.app.ui.viewmodel

import app.cash.turbine.test
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.model.FlowDirection
import com.flowledger.app.data.model.ParsedTransaction
import com.flowledger.app.data.repository.FakeAccountDao
import com.flowledger.app.data.repository.FakeTransactionDao
import com.flowledger.app.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("LedgerViewModel Reactive StateFlow Tests (Turbine)")
class LedgerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeTransactionDao: TransactionDao
    private lateinit var fakeAccountDao: AccountDao
    private lateinit var repository: TransactionRepository
    private lateinit var viewModel: LedgerViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTransactionDao = FakeTransactionDao()
        fakeAccountDao = FakeAccountDao()
        repository = TransactionRepository(fakeTransactionDao, fakeAccountDao)
        viewModel = LedgerViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("Initial State: ViewModel StateFlows emit 0 balance, 0 flow, and empty list")
    fun `initial state of viewmodel flows should be zero and empty`() = runTest(testDispatcher) {
        viewModel.totalBalance.test {
            assertEquals(0.0, awaitItem())
        }

        viewModel.monthlyInflow.test {
            assertEquals(0.0, awaitItem())
        }

        viewModel.monthlyOutflow.test {
            assertEquals(0.0, awaitItem())
        }

        viewModel.transactions.test {
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    @DisplayName("Transaction Insertion: StateFlows update reactively without polling")
    fun `inserting transactions reactively cascades to ViewModel StateFlows`() = runTest(testDispatcher) {
        val timestamp = System.currentTimeMillis()

        // 1. Insert a Debit
        repository.recordTransaction(
            parsed = ParsedTransaction(
                amount = 4500.00,
                flowDirection = FlowDirection.OUTFLOW,
                accountLast4 = "4092",
                availableBalance = 35000.00,
                bankCode = "HDFC",
                bankName = "HDFC Bank",
                currencyCode = "INR",
                merchantHint = "Amazon Shopping",
                timestamp = timestamp
            ),
            rawSender = "VK-HDFCBK",
            rawBody = "Rs 4,500.00 debited from a/c **4092. Bal: Rs 35,000.00"
        )
        advanceUntilIdle()

        viewModel.totalBalance.test {
            assertEquals(35000.00, awaitItem())
        }

        viewModel.monthlyOutflow.test {
            assertEquals(4500.00, awaitItem())
        }

        viewModel.transactions.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("HDFC_4092", list[0].accountId)
            assertEquals(4500.00, list[0].amount)
            assertEquals("Amazon Shopping", list[0].merchantHint)
        }

        // 2. Insert a Credit
        repository.recordTransaction(
            parsed = ParsedTransaction(
                amount = 15000.00,
                flowDirection = FlowDirection.INFLOW,
                accountLast4 = "4092",
                availableBalance = 50000.00,
                bankCode = "HDFC",
                bankName = "HDFC Bank",
                currencyCode = "INR",
                merchantHint = "Consulting Fee",
                timestamp = timestamp
            ),
            rawSender = "AD-HDFCBK",
            rawBody = "Rs 15,000.00 credited to a/c **4092. Bal: Rs 50,000.00"
        )
        advanceUntilIdle()

        viewModel.totalBalance.test {
            assertEquals(50000.00, awaitItem())
        }

        viewModel.monthlyInflow.test {
            assertEquals(15000.00, awaitItem())
        }
    }

    @Test
    @DisplayName("toggleExcluded Action: ViewModel updates is_excluded and recalculates monthly flow")
    fun `toggleExcluded on ViewModel updates repository and recalculates flow aggregates`() = runTest(testDispatcher) {
        val timestamp = System.currentTimeMillis()

        repository.recordTransaction(
            parsed = ParsedTransaction(
                amount = 2000.00,
                flowDirection = FlowDirection.OUTFLOW,
                accountLast4 = "4092",
                availableBalance = 48000.00,
                bankCode = "HDFC",
                bankName = "HDFC Bank",
                currencyCode = "INR",
                merchantHint = "Transfer to Self",
                timestamp = timestamp
            ),
            rawSender = "VK-HDFCBK",
            rawBody = "Rs 2,000.00 debited from a/c **4092. Bal: Rs 48,000.00"
        )
        advanceUntilIdle()

        var txId = ""
        viewModel.transactions.test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertFalse(list[0].isExcluded)
            txId = list[0].transactionId
        }

        viewModel.monthlyOutflow.test {
            assertEquals(2000.00, awaitItem())
        }

        // Action: User swipes to exclude
        viewModel.toggleExcluded(txId, true)
        advanceUntilIdle()

        viewModel.transactions.test {
            val list = awaitItem()
            assertTrue(list[0].isExcluded)
        }

        viewModel.monthlyOutflow.test {
            assertEquals(0.0, awaitItem(), "Excluded transaction must be subtracted from monthly outflow sum")
        }
    }
}
