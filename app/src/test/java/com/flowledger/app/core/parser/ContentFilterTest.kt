package com.flowledger.app.core.parser

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Phase 2: Content Rejection (OTP, Promos, Non-Financial Alerts) Tests")
class ContentFilterTest {

    @ParameterizedTest(name = "OTP / Sensitive Auth Message: {0}")
    @ValueSource(
        strings = [
            "482910 is your secret OTP for transaction of Rs 5,400.00 at Amazon. Do not share with anyone.",
            "Your one-time password (OTP) is 938210. Valid for 5 minutes. Bank will never ask for secret PIN.",
            "Use verification code 492019 to complete your login. Do not disclose.",
            "Never share your OTP, CVV or expiry date with anyone including bank officials.",
            "Dear Customer, your secret pin for NetBanking login has been dispatched.",
            "982143 is your OTP for UPI registration. Don't share with anyone."
        ]
    )
    fun `should reject OTP and authentication security alerts`(message: String) {
        assertTrue(
            ContentFilter.shouldReject(message),
            "Expected OTP message to be rejected: $message"
        )
    }

    @ParameterizedTest(name = "Promotional / Marketing Message: {0}")
    @ValueSource(
        strings = [
            "Congratulations! You are eligible for a pre-approved loan of Rs 5,00,000. Apply now!",
            "Get 10% cashback offer on all Swiggy orders using HDFC Credit Card. Limited offer.",
            "Enjoy zero processing fee on personal loans. Apply now at hdfcbank.com.",
            "Special cashback offer! Spend Rs 2000 and get Rs 200 back."
        ]
    )
    fun `should reject promotional and advertising messages`(message: String) {
        assertTrue(
            ContentFilter.shouldReject(message),
            "Expected promotional message to be rejected: $message"
        )
    }

    @ParameterizedTest(name = "Administrative / Non-Transactional Alert: {0}")
    @ValueSource(
        strings = [
            "Dear Customer, your EMI due date is 05-Sep-26. Please maintain sufficient balance.",
            "Payment reminder: Your credit card bill of Rs 14,250 is due on 28-Aug-26.",
            "Your password changed successfully on 24-Aug-26 12:30 PM. If not done by you, contact bank.",
            "Your email address and profile updated successfully in bank records.",
            "Dear Customer, nominee registration for your account XX4092 is complete.",
            "Kindly complete your periodic KYC update to ensure uninterrupted banking services."
        ]
    )
    fun `should reject administrative and reminder alerts`(message: String) {
        assertTrue(
            ContentFilter.shouldReject(message),
            "Expected administrative alert to be rejected: $message"
        )
    }

    @ParameterizedTest(name = "Legitimate Transaction Message: {0}")
    @ValueSource(
        strings = [
            "Rs 5,400.00 debited from a/c **4092 on 24-08-26. Info: Amazon. Avl Bal: Rs 42,100.50",
            "Acct XX102 credited with INR 12,000.00 on 23-Aug-26 by Salary. Avl Bal INR 85,400.00",
            "Your a/c X3210 Debited Rs. 245.00 on 24Aug Tfr to JOHN. Avl Bal Rs. 14,020.56",
            "INR 1,200.00 sent from A/c no. XX4501 to JOHN VIA UPI. UPI Ref 423100842. Bal 5,400.00",
            "Chase: You made a $45.20 debit card purchase with card ending in 8912. Available balance: $2,410.00"
        ]
    )
    fun `should allow legitimate financial debit and credit transactions to pass`(message: String) {
        assertFalse(
            ContentFilter.shouldReject(message),
            "Expected legitimate transaction message to pass through ContentFilter: $message"
        )
    }
}
