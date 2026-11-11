package com.flowledger.app.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.entity.AccountEntity
import com.flowledger.app.data.entity.TransactionEntity
import com.flowledger.app.data.model.FlowDirection
import com.flowledger.app.data.model.ParsedTransaction
import com.flowledger.app.data.repository.FakeAccountDao
import com.flowledger.app.data.repository.FakeTransactionDao
import com.flowledger.app.data.repository.TransactionRepository
import com.flowledger.app.ui.theme.FlowLedgerTheme
import com.flowledger.app.ui.viewmodel.LedgerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerDashboardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fakeTransactionDao: FakeTransactionDao
    private lateinit var fakeAccountDao: FakeAccountDao
    private lateinit var repository: TransactionRepository
    private lateinit var viewModel: LedgerViewModel

    @Before
    fun setUp() {
        fakeTransactionDao = FakeTransactionDao()
        fakeAccountDao = FakeAccountDao()
        repository = TransactionRepository(fakeTransactionDao, fakeAccountDao)
        viewModel = LedgerViewModel(repository)
    }

    @Test
    fun heroBalanceCard_displaysCorrectFormattedBalanceAndLabel() {
        runBlocking {
            fakeAccountDao.upsert(
                AccountEntity(
                    accountId = "HDFC_4092",
                    bankName = "HDFC Bank",
                    accountLast4 = "4092",
                    currencyCode = "INR",
                    currentBalance = 14250.80,
                    lastUpdatedEpoch = System.currentTimeMillis()
                )
            )
        }

        composeTestRule.setContent {
            FlowLedgerTheme {
                LedgerDashboard(viewModel = viewModel)
            }
        }

        // Verify Hero Card label and balance are displayed
        composeTestRule.onNodeWithText("Current Available Balance").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹14,250.80").assertIsDisplayed()
    }

    @Test
    fun flowPills_displayInflowAndOutflowMetrics() {
        runBlocking {
            val timestamp = System.currentTimeMillis()
            // Inflow: +4,200.00
            repository.recordTransaction(
                parsed = ParsedTransaction(
                    amount = 4200.00,
                    flowDirection = FlowDirection.INFLOW,
                    accountLast4 = "4092",
                    availableBalance = 14250.80,
                    bankCode = "HDFC",
                    bankName = "HDFC Bank",
                    currencyCode = "INR",
                    merchantHint = "Freelance Project",
                    timestamp = timestamp
                ),
                rawSender = "AD-HDFCBK",
                rawBody = "Rs 4,200.00 credited to a/c **4092. Bal: Rs 14,250.80"
            )

            // Outflow: -2,180.50
            repository.recordTransaction(
                parsed = ParsedTransaction(
                    amount = 2180.50,
                    flowDirection = FlowDirection.OUTFLOW,
                    accountLast4 = "4092",
                    availableBalance = 12070.30,
                    bankCode = "HDFC",
                    bankName = "HDFC Bank",
                    currencyCode = "INR",
                    merchantHint = "Amazon Web Services",
                    timestamp = timestamp
                ),
                rawSender = "VK-HDFCBK",
                rawBody = "Rs 2,180.50 debited from a/c **4092. Bal: Rs 12,070.30"
            )
        }

        composeTestRule.setContent {
            FlowLedgerTheme {
                LedgerDashboard(viewModel = viewModel)
            }
        }

        // Verify Inflow pill displays +4,200.00
        composeTestRule.onNodeWithText("+4,200.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("▲").assertIsDisplayed()

        // Verify Outflow pill displays -2,180.50
        composeTestRule.onNodeWithText("-2,180.50").assertIsDisplayed()
        composeTestRule.onNodeWithText("▼").assertIsDisplayed()
    }

    @Test
    fun transactionList_rendersDetailsAndSwipeToExcludeRecalculatesFlow() {
        val timestamp = System.currentTimeMillis()
        runBlocking {
            repository.recordTransaction(
                parsed = ParsedTransaction(
                    amount = 5000.00,
                    flowDirection = FlowDirection.OUTFLOW,
                    accountLast4 = "4092",
                    availableBalance = 20000.00,
                    bankCode = "HDFC",
                    bankName = "HDFC Bank",
                    currencyCode = "INR",
                    merchantHint = "Internal Account Transfer",
                    timestamp = timestamp
                ),
                rawSender = "VK-HDFCBK",
                rawBody = "Rs 5,000.00 debited from a/c **4092. Bal: Rs 20,000.00"
            )
        }

        composeTestRule.setContent {
            FlowLedgerTheme {
                LedgerDashboard(viewModel = viewModel)
            }
        }

        // Verify transaction item details are rendered
        composeTestRule.onNodeWithText("HDFC ••4092").assertIsDisplayed()
        composeTestRule.onNodeWithText("Internal Account Transfer").assertIsDisplayed()
        composeTestRule.onNodeWithText("-₹5,000.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("-5,000.00").assertIsDisplayed() // Outflow pill

        // Perform Swipe right gesture on the transaction item to toggle "Exclude from Flow"
        composeTestRule.onNodeWithText("Internal Account Transfer")
            .performTouchInput {
                swipeRight(startX = 10f, endX = 800f, durationMillis = 300)
            }

        // Verify Outflow aggregate recalculates reactively to 0 without manual refresh
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("-0.00").assertIsDisplayed()
    }

    @Test
    fun strictProductConstraint_noCategoryInputsOrFabButtonsExist() {
        composeTestRule.setContent {
            FlowLedgerTheme {
                LedgerDashboard(viewModel = viewModel)
            }
        }

        // STRICT CONSTRAINT 1: Zero category tags/chips
        composeTestRule.onNodeWithText("Food").assertDoesNotExist()
        composeTestRule.onNodeWithText("Transport").assertDoesNotExist()
        composeTestRule.onNodeWithText("Groceries").assertDoesNotExist()
        composeTestRule.onNodeWithText("Rent").assertDoesNotExist()
        composeTestRule.onNodeWithText("Select Category").assertDoesNotExist()

        // STRICT CONSTRAINT 2: No manual "Add Expense" FAB button
        composeTestRule.onNodeWithContentDescription("Add Expense").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Add Transaction").assertDoesNotExist()
        composeTestRule.onNodeWithText("Add Expense").assertDoesNotExist()
    }
}
