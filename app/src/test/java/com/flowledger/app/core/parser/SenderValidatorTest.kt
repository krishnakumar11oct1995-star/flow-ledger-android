package com.flowledger.app.core.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Phase 1: Sender Address Gating Tests")
class SenderValidatorTest {

    @ParameterizedTest(name = "Valid TRAI Indian Bank Sender: {0}")
    @ValueSource(
        strings = [
            "VK-HDFCBK",
            "AD-ICICIB",
            "JM-SBIBNK",
            "BP-AXISBK",
            "DZ-KOTAKB",
            "BW-PNBSMS",
            "CP-YESBNK",
            "IX-INDBNK",
            "TM-CANBNK",
            "VK-HDFC",
            "AD-SBIIN"
        ]
    )
    fun `should accept valid Indian TRAI financial sender headers`(sender: String) {
        assertTrue(
            SenderValidator.isBankSender(sender),
            "Expected $sender to be accepted as a valid bank sender header"
        )
    }

    @ParameterizedTest(name = "Valid US Bank Sender / Short Code: {0}")
    @ValueSource(
        strings = [
            "72166",      // Chase
            "32552",      // Bank of America
            "86434",      // Wells Fargo
            "AMEX",
            "CITI",
            "CHASE",
            "BOFA"
        ]
    )
    fun `should accept valid US financial short codes and branded headers`(sender: String) {
        assertTrue(
            SenderValidator.isBankSender(sender),
            "Expected $sender to be accepted as a valid US bank sender"
        )
    }

    @ParameterizedTest(name = "Invalid / Non-financial Sender: {0}")
    @ValueSource(
        strings = [
            "+919876543210",   // Indian personal mobile
            "9876543210",      // 10-digit mobile
            "+12025550143",    // US personal mobile
            "FRIEND",          // Contact name
            "MOM",
            "UBER",            // Non-bank service
            "NETFLIX",
            "SWIGGY",
            "ZOMATO",
            "12345",           // Random short code
            "SPAM-PROMO",
            ""                 // Empty string
        ]
    )
    fun `should reject personal phone numbers, contacts, and non-bank senders`(sender: String) {
        assertFalse(
            SenderValidator.isBankSender(sender),
            "Expected $sender to be rejected in Phase 1 before parsing"
        )
    }
}
