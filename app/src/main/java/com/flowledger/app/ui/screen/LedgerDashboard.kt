package com.flowledger.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flowledger.app.data.entity.TransactionEntity
import com.flowledger.app.ui.theme.InflowGreen
import com.flowledger.app.ui.theme.OutflowRed
import com.flowledger.app.ui.theme.TabularFiguresStyle
import com.flowledger.app.ui.viewmodel.LedgerViewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * Main Ledger Dashboard — the secondary audit surface.
 *
 * Strict constraints from the PRD:
 * - NO category tags or classification UI
 * - NO manual "Add Expense" FAB or input forms
 * - Permitted: Swipe to exclude, tap to inspect raw SMS, account filter
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LedgerDashboard(
    viewModel: LedgerViewModel = hiltViewModel()
) {
    val totalBalance by viewModel.totalBalance.collectAsState()
    val monthlyInflow by viewModel.monthlyInflow.collectAsState()
    val monthlyOutflow by viewModel.monthlyOutflow.collectAsState()
    val transactions by viewModel.transactions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Flow Ledger") })
        }
        // NO FloatingActionButton — PRD explicitly prohibits manual "Add Expense"
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Hero Balance Card ──
            HeroBalanceCard(totalBalance)

            // ── Monthly Cash Flow Pills ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentMonth = LocalDate.now().month.name.lowercase()
                    .replaceFirstChar { it.uppercase() }
                FlowPill(
                    modifier = Modifier.weight(1f),
                    label = "In ($currentMonth)",
                    amount = monthlyInflow,
                    isInflow = true
                )
                FlowPill(
                    modifier = Modifier.weight(1f),
                    label = "Out ($currentMonth)",
                    amount = monthlyOutflow,
                    isInflow = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Transaction Ledger (grouped by date) ──
            val groupedTransactions = transactions.groupBy {
                Instant.ofEpochMilli(it.timestampEpoch)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                groupedTransactions.forEach { (date, items) ->
                    stickyHeader {
                        DateHeader(date)
                    }
                    items(
                        items = items,
                        key = { it.transactionId }
                    ) { transaction ->
                        TransactionSwipeRow(
                            transaction = transaction,
                            onToggleExcluded = { id, excluded ->
                                viewModel.toggleExcluded(id, excluded)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Large hero card displaying the aggregate available balance. */
@Composable
fun HeroBalanceCard(balance: Double) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatCurrency(balance),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                style = TabularFiguresStyle
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Current Available Balance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Colored pill displaying monthly inflow (green) or outflow (red) volume.
 * Uses tabular figures to prevent layout jitter on value changes.
 */
@Composable
fun FlowPill(
    modifier: Modifier = Modifier,
    label: String,
    amount: Double,
    isInflow: Boolean
) {
    val color = if (isInflow) InflowGreen else OutflowRed
    val sign = if (isInflow) "+" else "-"
    val icon = if (isInflow) "▲" else "▼"

    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, color = color, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    color = color,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$sign${formatCurrency(amount, includeSymbol = false)}",
                color = color,
                fontWeight = FontWeight.Bold,
                style = TabularFiguresStyle
            )
        }
    }
}

/** Sticky date header with smart labels (TODAY, YESTERDAY, or formatted date). */
@Composable
fun DateHeader(date: LocalDate) {
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)
    val headerText = when (date) {
        today -> "TODAY"
        yesterday -> "YESTERDAY"
        else -> date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")).uppercase()
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = headerText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * Transaction row with swipe-to-exclude. Maps to [TransactionEntity] fields:
 * - accountId for bank + masked account display
 * - flowDirection for color (INFLOW = green, OUTFLOW = red)
 * - merchantHint for payee line
 * - balanceAfter for post-tx balance
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSwipeRow(
    transaction: TransactionEntity,
    onToggleExcluded: (String, Boolean) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onToggleExcluded(transaction.transactionId, !transaction.isExcluded)
            }
            false // Snap back to center after action
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val actionText = if (transaction.isExcluded) "Include" else "Exclude"
            val bgColor = if (transaction.isExcluded)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            Surface(
                color = bgColor,
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(actionText, color = MaterialTheme.colorScheme.onError)
                    Text(actionText, color = MaterialTheme.colorScheme.onError)
                }
            }
        },
        content = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Bank + Account identifier and merchant hint
                    Column(modifier = Modifier.weight(1f)) {
                        // Parse bank name from accountId (format: "BANKCODE_LAST4")
                        val parts = transaction.accountId.split("_")
                        val bankLabel = if (parts.size == 2) "${parts[0]} ••${parts[1]}" else transaction.accountId

                        Text(
                            text = bankLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (transaction.isExcluded)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (transaction.merchantHint != null) {
                            Text(
                                text = transaction.merchantHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Right side: Amount + Balance after
                    Column(horizontalAlignment = Alignment.End) {
                        val isInflow = transaction.flowDirection == "INFLOW"
                        val amountColor = when {
                            transaction.isExcluded -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            isInflow -> InflowGreen
                            else -> OutflowRed
                        }
                        val prefix = if (isInflow) "+" else "-"

                        Text(
                            text = "$prefix${formatCurrency(transaction.amount)}",
                            style = TabularFiguresStyle.copy(
                                fontWeight = FontWeight.Bold,
                                color = amountColor,
                                fontSize = 16.sp
                            )
                        )

                        if (transaction.balanceAfter != null) {
                            Text(
                                text = "Bal: ${formatCurrency(transaction.balanceAfter)}",
                                style = TabularFiguresStyle.copy(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    )
}

/** Format a Double as a currency string using the Indian locale. */
fun formatCurrency(amount: Double, includeSymbol: Boolean = true): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.currency = Currency.getInstance("INR")
    val formatted = format.format(amount)
    return if (includeSymbol) formatted else formatted.replace(format.currency?.symbol ?: "", "").trim()
}
