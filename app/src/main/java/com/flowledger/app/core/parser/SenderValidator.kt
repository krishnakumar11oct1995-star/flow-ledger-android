package com.flowledger.app.core.parser

object SenderValidator {
    private val TRAI_PATTERN = Regex("^[A-Z]{2}-[A-Z]{3,6}(?:BK|BN|BNK|IN)?$", RegexOption.IGNORE_CASE)
    private val KNOWN_US_SENDERS = setOf("72166", "32552", "86434", "AMEX", "CITI")

    fun isBankSender(sender: String): Boolean {
        val cleanSender = sender.trim().uppercase()
        return TRAI_PATTERN.matches(cleanSender) || KNOWN_US_SENDERS.contains(cleanSender)
    }
}
