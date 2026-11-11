package com.flowledger.app.core.parser

import com.flowledger.app.data.model.FlowDirection
import com.flowledger.app.data.model.ParsedTransaction

class SmsParserEngine private constructor() {

    private data class ParserRule(
        val id: String,
        val bankCode: String,
        val bankName: String,
        val currencyCode: String,
        val senderPattern: Regex,
        val bodyPattern: Regex,
        val directionResolver: (MatchResult) -> FlowDirection,
        val priority: Int
    )

    private val rules: List<ParserRule> = listOf(
        // ── HDFC Bank (India) ──
        ParserRule(
            id = "HDFC_V1",
            bankCode = "HDFC",
            bankName = "HDFC Bank",
            currencyCode = "INR",
            senderPattern = Regex(".*(?:HDFC|HDFCBK).*", RegexOption.IGNORE_CASE),
            bodyPattern = Regex(
                """(?i)Rs\.?\s*(?<amount>[\d,]+\.?\d*)\s+(?<direction>debited|credited)\s+(?:from|to)\s+a/?c\s*\**(?<acct>\d{4}).*?(?:(?:Avl\s*)?Bal[:\s]*Rs\.?\s*(?<balance>[\d,]+\.?\d*))?"""
            ),
            directionResolver = { match ->
                if (match.groups["direction"]?.value?.contains("credit", ignoreCase = true) == true)
                    FlowDirection.INFLOW else FlowDirection.OUTFLOW
            },
            priority = 10
        ),
        // ── ICICI Bank (India) ──
        ParserRule(
            id = "ICICI_V1",
            bankCode = "ICICI",
            bankName = "ICICI Bank",
            currencyCode = "INR",
            senderPattern = Regex(".*(?:ICICI|ICICIB).*", RegexOption.IGNORE_CASE),
            bodyPattern = Regex(
                """(?i)Acct\s*[Xx*]*(?<acct>\d{3,4})\s+(?<direction>credited|debited)\s+(?:with\s+)?(?:INR|Rs\.?)\s*(?<amount>[\d,]+\.?\d*).*?(?:Avl\s*Bal\s*(?:INR|Rs\.?)\s*(?<balance>[\d,]+\.?\d*))?"""
            ),
            directionResolver = { match ->
                if (match.groups["direction"]?.value?.contains("credit", ignoreCase = true) == true)
                    FlowDirection.INFLOW else FlowDirection.OUTFLOW
            },
            priority = 10
        ),
        // ── SBI (State Bank of India) ──
        ParserRule(
            id = "SBI_V1",
            bankCode = "SBI",
            bankName = "State Bank of India",
            currencyCode = "INR",
            senderPattern = Regex(".*(?:SBI|SBIBNK|SBIIN).*", RegexOption.IGNORE_CASE),
            bodyPattern = Regex(
                """(?i)a/?c\s*[Xx*]*(?<acct>\d{4})\s*(?<direction>Debited|Credited)\s*Rs\.?\s*(?<amount>[\d,]+\.?\d*).*?(?:(?:Avl\s*)?Bal\s*Rs\.?\s*(?<balance>[\d,]+\.?\d*))?"""
            ),
            directionResolver = { match ->
                if (match.groups["direction"]?.value?.contains("credit", ignoreCase = true) == true)
                    FlowDirection.INFLOW else FlowDirection.OUTFLOW
            },
            priority = 10
        ),
        // ── Axis Bank (India) ──
        ParserRule(
            id = "AXIS_V1",
            bankCode = "AXIS",
            bankName = "Axis Bank",
            currencyCode = "INR",
            senderPattern = Regex(".*(?:AXIS|AXISBK).*", RegexOption.IGNORE_CASE),
            bodyPattern = Regex(
                """(?i)(?:INR|Rs\.?)\s*(?<amount>[\d,]+\.?\d*)\s+(?<direction>sent|received|debited|credited).*?(?:a/?c\s*(?:no\.?\s*)?[Xx*]*(?<acct>\d{4})).*?(?:Bal\.?\s*(?:INR|Rs\.?)?\s*(?<balance>[\d,]+\.?\d*))?"""
            ),
            directionResolver = { match ->
                val dir = match.groups["direction"]?.value?.lowercase() ?: ""
                if (dir.contains("received") || dir.contains("credited"))
                    FlowDirection.INFLOW else FlowDirection.OUTFLOW
            },
            priority = 10
        ),
        // ── Chase Bank (US) ──
        ParserRule(
            id = "CHASE_V1",
            bankCode = "CHASE",
            bankName = "Chase",
            currencyCode = "USD",
            senderPattern = Regex(".*(?:CHASE|72166).*", RegexOption.IGNORE_CASE),
            bodyPattern = Regex(
                """(?i)\$\s*(?<amount>[\d,]+\.?\d*).*?(?<direction>debit|purchase|withdrawal|deposit|credit|refund).*?(?:(?:card|account)\s*(?:ending\s*(?:in\s*)?)?(?<acct>\d{4})).*?(?:(?:Available\s*)?[Bb]alance[:\s]*\$\s*(?<balance>[\d,]+\.?\d*))?"""
            ),
            directionResolver = { match ->
                val dir = match.groups["direction"]?.value?.lowercase() ?: ""
                if (dir.contains("deposit") || dir.contains("credit") || dir.contains("refund"))
                    FlowDirection.INFLOW else FlowDirection.OUTFLOW
            },
            priority = 10
        ),
        // ── GENERIC FALLBACK (priority 99) ──
        ParserRule(
            id = "GENERIC_FALLBACK",
            bankCode = "UNKNOWN",
            bankName = "Unknown Bank",
            currencyCode = "INR",
            senderPattern = Regex(".*"),
            bodyPattern = Regex(
                """(?i)(?:(?:Rs\.?|INR|\$|USD|EUR|GBP)\s*(?<amount>[\d,]+\.?\d*)|(?<amount2>[\d,]+\.?\d*)\s*(?:Rs\.?|INR|\$|USD|EUR|GBP)).*?(?<direction>debit|credit|sent|received|withdraw|deposit|purchase|refund|reversed).*?(?:[Xx*]{2,}(?<acct>\d{4})).*?(?:(?:Avl\.?\s*)?[Bb]al(?:ance)?[:\s]*(?:Rs\.?|INR|\$|USD|EUR|GBP)?\s*(?<balance>[\d,]+\.?\d*))?"""
            ),
            directionResolver = { match ->
                val dir = match.groups["direction"]?.value?.lowercase() ?: ""
                if (dir.contains("credit") || dir.contains("received") ||
                    dir.contains("deposit") || dir.contains("refund") || dir.contains("reversed"))
                    FlowDirection.INFLOW else FlowDirection.OUTFLOW
            },
            priority = 99
        )
    ).sortedBy { it.priority }

    /**
     * Main entry point: parses an SMS into a [ParsedTransaction].
     * Returns null if the SMS is not a parseable financial transaction.
     *
     * Phase 1: Sender address gating
     * Phase 2: OTP / promotional content rejection
     * Phase 3: Priority-ordered regex cascade with named capture groups
     */
    fun parse(sender: String, body: String): ParsedTransaction? {
        // Phase 1: Sender Gate
        if (!SenderValidator.isBankSender(sender)) return null
        // Phase 2: Content Rejection
        if (ContentFilter.shouldReject(body)) return null

        // Phase 3: Regex Cascade
        for (rule in rules) {
            if (!rule.senderPattern.matches(sender)) continue

            val match = rule.bodyPattern.find(body) ?: continue

            val rawAmount = (match.groups["amount"] ?: match.groups["amount2"])?.value ?: continue
            val amount = rawAmount.replace(",", "").toDoubleOrNull() ?: continue
            if (amount <= 0.0) continue

            val accountLast4 = match.groups["acct"]?.value ?: "0000"
            val balance = match.groups["balance"]?.value?.replace(",", "")?.toDoubleOrNull()

            // Resolve currency for generic fallback rule
            val currencyCode = if (rule.currencyCode == "INR" && rule.id == "GENERIC_FALLBACK") {
                when {
                    body.contains("$") || body.contains("USD", ignoreCase = true) -> "USD"
                    body.contains("EUR", ignoreCase = true) -> "EUR"
                    body.contains("GBP", ignoreCase = true) -> "GBP"
                    else -> "INR"
                }
            } else {
                rule.currencyCode
            }

            return ParsedTransaction(
                amount = amount,
                flowDirection = rule.directionResolver(match),
                accountLast4 = accountLast4,
                availableBalance = balance,
                bankCode = rule.bankCode,
                bankName = rule.bankName,
                currencyCode = currencyCode,
                merchantHint = extractMerchantHint(body),
                timestamp = System.currentTimeMillis()
            )
        }

        return null
    }

    /**
     * Best-effort merchant name extraction from common SMS patterns.
     */
    private fun extractMerchantHint(body: String): String? {
        val patterns = listOf(
            Regex("""(?i)(?:Info|Ref|at|to|from|VIA)\s*[:\-]?\s*([A-Za-z0-9\s&.']+?)(?:\s*(?:on|Ref|UPI|IMPS|NEFT|\.|$))"""),
            Regex("""(?i)(?:towards|for)\s+([A-Za-z0-9\s&.']+?)(?:\s*(?:on|via|\.|$))""")
        )
        for (p in patterns) {
            val m = p.find(body)
            if (m != null) return m.groupValues[1].trim().take(50)
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: SmsParserEngine? = null

        fun getInstance(): SmsParserEngine =
            instance ?: synchronized(this) {
                instance ?: SmsParserEngine().also { instance = it }
            }
    }
}
