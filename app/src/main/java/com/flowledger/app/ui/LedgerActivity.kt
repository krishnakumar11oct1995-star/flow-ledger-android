package com.flowledger.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.flowledger.app.ui.screen.LedgerDashboard
import com.flowledger.app.ui.theme.FlowLedgerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LedgerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlowLedgerTheme {
                LedgerDashboard()
            }
        }
    }
}
