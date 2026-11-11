package com.flowledger.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.room.Room
import com.flowledger.app.data.FlowLedgerDatabase
import com.flowledger.app.data.repository.TransactionRepository
import java.text.NumberFormat
import java.time.YearMonth
import java.time.ZoneId
import java.util.Currency
import java.util.Locale

/**
 * Jetpack Glance home screen widget — the primary interaction surface.
 * Reads state from [PreferencesGlanceStateDefinition] (DataStore<Preferences>)
 * since the widget runs in the Launcher's process and cannot access Room directly.
 */
class FlowLedgerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val balance = prefs[BALANCE_KEY] ?: 0.0
            val inflow = prefs[INFLOW_KEY] ?: 0.0
            val outflow = prefs[OUTFLOW_KEY] ?: 0.0
            val updated = prefs[UPDATED_KEY] ?: System.currentTimeMillis()

            WidgetContent(balance, inflow, outflow, updated)
        }
    }

    @Composable
    private fun WidgetContent(
        balance: Double,
        inflow: Double,
        outflow: Double,
        updatedEpoch: Long
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(16.dp)
        ) {
            // ── Header Row ──
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Flow Ledger",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                val minutesAgo = (System.currentTimeMillis() - updatedEpoch) / 60_000
                Text(
                    text = if (minutesAgo == 0L) "Updated just now" else "Updated ${minutesAgo}m ago",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(GlanceModifier.height(12.dp))

            // ── Hero Balance ──
            Text(
                text = formatWidgetCurrency(balance),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            )

            Text(
                text = "Current Available Balance",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )

            Spacer(GlanceModifier.height(12.dp))

            // ── Monthly Cash Flow Pills ──
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                // Inflow Pill
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "▲ In",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF00C853)),
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "+${formatWidgetCurrency(inflow, includeSymbol = false)}",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(GlanceModifier.width(8.dp))

                // Outflow Pill
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "▼ Out",
                        style = TextStyle(
                            color = GlanceTheme.colors.error,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = "-${formatWidgetCurrency(outflow, includeSymbol = false)}",
                        style = TextStyle(
                            color = GlanceTheme.colors.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }

    private fun formatWidgetCurrency(amount: Double, includeSymbol: Boolean = true): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        format.currency = Currency.getInstance("INR")
        val formatted = format.format(amount)
        return if (includeSymbol) formatted else formatted.replace(format.currency?.symbol ?: "", "").trim()
    }

    companion object {
        val BALANCE_KEY = doublePreferencesKey("total_balance")
        val INFLOW_KEY = doublePreferencesKey("monthly_inflow")
        val OUTFLOW_KEY = doublePreferencesKey("monthly_outflow")
        val CURRENCY_KEY = stringPreferencesKey("currency")
        val UPDATED_KEY = longPreferencesKey("updated_at")

        /**
         * Called from [SmsBroadcastReceiver] after persisting a new transaction.
         * Reads one-shot snapshots from Room, writes them to DataStore, and
         * triggers a Glance re-render across all placed widget instances.
         */
        suspend fun forceUpdate(context: Context) {
            val database = Room.databaseBuilder(
                context.applicationContext,
                FlowLedgerDatabase::class.java,
                "flowledger.db"
            ).build()

            val repository = TransactionRepository(
                database.transactionDao(),
                database.accountDao()
            )

            val balance = repository.getTotalBalanceOnce()

            val zoneId = ZoneId.systemDefault()
            val yearMonth = YearMonth.now(zoneId)
            val startOfMonth = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endOfMonth = yearMonth.atEndOfMonth()
                .atTime(23, 59, 59, 999_999_999)
                .atZone(zoneId).toInstant().toEpochMilli()

            val inflow = repository.getMonthlyInflowOnce(startOfMonth, endOfMonth)
            val outflow = repository.getMonthlyOutflowOnce(startOfMonth, endOfMonth)

            val widget = FlowLedgerWidget()
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(widget.javaClass).forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[BALANCE_KEY] = balance
                    prefs[INFLOW_KEY] = inflow
                    prefs[OUTFLOW_KEY] = outflow
                    prefs[UPDATED_KEY] = System.currentTimeMillis()
                }
            }
            widget.updateAll(context)
        }
    }
}
