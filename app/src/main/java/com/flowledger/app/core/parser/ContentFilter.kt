package com.flowledger.app.core.parser

object ContentFilter {
    private val REJECT_PATTERN = Regex(
        listOf(
            "otp", "one\\.time\\.password", "verification\\.code", "do not share", "don't share", "secret pin", "cvv", "expiry",
            "apply now", "limited offer", "cashback offer",
            "emi due", "payment reminder", "auto\\.debit\\..*due", "password changed", "profile updated", "registered", "dear customer\\..*nominee", "kyc"
        ).joinToString("|"),
        RegexOption.IGNORE_CASE
    )

    fun shouldReject(body: String): Boolean {
        return REJECT_PATTERN.containsMatchIn(body)
    }
}
