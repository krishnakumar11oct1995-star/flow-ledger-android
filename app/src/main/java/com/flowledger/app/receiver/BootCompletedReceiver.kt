package com.flowledger.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowledger.app.widget.FlowLedgerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                FlowLedgerWidget.forceUpdate(context.applicationContext)
            }
        }
    }
}
