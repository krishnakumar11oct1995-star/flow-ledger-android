package com.flowledger.app.data.model

data class ParsedTransaction(
    val amount: Double,
    val flowDirection: FlowDirection,
    val accountLast4: String,
    val availableBalance: Double?,
    val bankCode: String,
    val bankName: String,
    val currencyCode: String,
    val merchantHint: String?,
    val timestamp: Long
)

enum class FlowDirection {
    INFLOW, OUTFLOW
}
