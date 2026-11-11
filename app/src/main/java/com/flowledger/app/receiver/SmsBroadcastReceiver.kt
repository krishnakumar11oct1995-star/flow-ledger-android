package com.flowledger.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.room.Room
import com.flowledger.app.core.parser.SmsParserEngine
import com.flowledger.app.data.FlowLedgerDatabase
import com.flowledger.app.data.repository.TransactionRepository
import com.flowledger.app.widget.FlowLedgerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Intercepts incoming SMS broadcasts. Uses [goAsync] to acquire a
 * [PendingResult] wake lock, offloading parsing and DB insertion to a
 * background coroutine without risking an ANR on the main thread.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }

        // Acquire wake lock token — keeps process alive for background work
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val appContext = context.applicationContext
                val parser = SmsParserEngine.getInstance()

                val database = Room.databaseBuilder(
                    appContext,
                    FlowLedgerDatabase::class.java,
                    "flowledger.db"
                ).build()

                val repository = TransactionRepository(
                    database.transactionDao(),
                    database.accountDao()
                )

                // Three-phase parse: Sender Gate → Content Rejection → Regex Cascade
                val parsed = parser.parse(sender, body) ?: return@launch

                // Atomic DB write: insert transaction + upsert account balance
                repository.recordTransaction(parsed, sender, body)

                // Push updated values to home screen widget
                FlowLedgerWidget.forceUpdate(appContext)
            } finally {
                // Release the wake lock — signals OS that async work is done
                pendingResult.finish()
            }
        }
    }
}
