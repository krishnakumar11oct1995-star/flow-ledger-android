package com.flowledger.app.core.parser

import com.flowledger.app.data.model.FlowDirection
import com.flowledger.app.data.model.ParsedTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@DisplayName("SmsParserEngine - 3-Phase Deterministic Pipeline Tests")
class SmsParserEngineTest {

    private lateinit var parserEngine: SmsParserEngine

    @BeforeEach
    fun setUp() {
        parserEngine = SmsParserEngine.getInstance()
    }

    // =========================================================================
    // POSITIVE TEST SUITES (PHASE 3: REGEX CASCADE ACCURACY)
    // =========================================================================

    @Nested
    @DisplayName("1. HDFC Bank Parser Tests")
    inner class HdfcBankTests {

        @Test
        @DisplayName("HDFC Debit with Merchant Hint and Available Balance")
        fun `should parse HDFC debit transaction correctly`() {
            val sender = "VK-HDFCBK"
            val body = "Rs 5,400.00 debited from a/c **4092 on 24-08-26. Info: Amazon. Avl Bal: Rs 42,100.50"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(5400.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.OUTFLOW, result.flowDirection)
            assertEquals("4092", result.accountLast4)
            assertEquals(42100.50, result.availableBalance!!, 0.001)
            assertEquals("HDFC", result.bankCode)
            assertEquals("HDFC Bank", result.bankName)
            assertEquals("INR", result.currencyCode)
            assertEquals("Amazon", result.merchantHint)
        }

        @Test
        @DisplayName("HDFC Credit (Salary/Deposit) with Available Balance")
        fun `should parse HDFC credit transaction correctly`() {
            val sender = "AD-HDFCBK"
            val body = "Rs 12,000.00 credited to a/c **4092 on 24-08-26. Info: Salary. Avl Bal: Rs 54,100.50"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(12000.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.INFLOW, result.flowDirection)
            assertEquals("4092", result.accountLast4)
            assertEquals(54100.50, result.availableBalance!!, 0.001)
            assertEquals("HDFC", result.bankCode)
            assertEquals("INR", result.currencyCode)
        }

        @Test
        @DisplayName("HDFC Debit without available balance")
        fun `should parse HDFC debit when balance is omitted`() {
            val sender = "VK-HDFC"
            val body = "Rs 850.00 debited from a/c **1122 on 20-08-26."

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(850.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.OUTFLOW, result.flowDirection)
            assertEquals("1122", result.accountLast4)
            assertNull(result.availableBalance)
        }
    }

    @Nested
    @DisplayName("2. ICICI Bank Parser Tests")
    inner class IciciBankTests {

        @Test
        @DisplayName("ICICI Salary Credit with Available Balance")
        fun `should parse ICICI credit transaction correctly`() {
            val sender = "AD-ICICIB"
            val body = "Acct XX102 credited with INR 12,000.00 on 23-Aug-26 by Salary. Avl Bal INR 85,400.00"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(12000.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.INFLOW, result.flowDirection)
            assertEquals("102", result.accountLast4)
            assertEquals(85400.00, result.availableBalance!!, 0.001)
            assertEquals("ICICI", result.bankCode)
            assertEquals("ICICI Bank", result.bankName)
            assertEquals("INR", result.currencyCode)
        }

        @Test
        @DisplayName("ICICI Debit with Available Balance")
        fun `should parse ICICI debit transaction correctly`() {
            val sender = "VK-ICICIB"
            val body = "Acct XX8901 debited with Rs 3,450.75 on 22-Aug-26. Avl Bal Rs 24,000.25"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(3450.75, result!!.amount, 0.001)
            assertEquals(FlowDirection.OUTFLOW, result.flowDirection)
            assertEquals("8901", result.accountLast4)
            assertEquals(24000.25, result.availableBalance!!, 0.001)
            assertEquals("ICICI", result.bankCode)
        }
    }

    @Nested
    @DisplayName("3. SBI (State Bank of India) Parser Tests")
    inner class SbiBankTests {

        @Test
        @DisplayName("SBI Debit with Transfer Info and Available Balance")
        fun `should parse SBI debit transaction correctly`() {
            val sender = "JM-SBIBNK"
            val body = "Your a/c X3210 Debited Rs. 245.00 on 24Aug Tfr to JOHN. Avl Bal Rs. 14,020.56"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(245.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.OUTFLOW, result.flowDirection)
            assertEquals("3210", result.accountLast4)
            assertEquals(14020.56, result.availableBalance!!, 0.001)
            assertEquals("SBI", result.bankCode)
            assertEquals("State Bank of India", result.bankName)
            assertEquals("INR", result.currencyCode)
        }

        @Test
        @DisplayName("SBI Credit with Available Balance")
        fun `should parse SBI credit transaction correctly`() {
            val sender = "AD-SBIIN"
            val body = "Your a/c X3210 Credited Rs. 10,000.00 on 24Aug. Avl Bal Rs. 24,020.56"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(10000.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.INFLOW, result.flowDirection)
            assertEquals("3210", result.accountLast4)
            assertEquals(24020.56, result.availableBalance!!, 0.001)
        }
    }

    @Nested
    @DisplayName("4. Axis Bank Parser Tests")
    inner class AxisBankTests {

        @Test
        @DisplayName("Axis Bank UPI Outflow / Sent")
        fun `should parse Axis Bank UPI sent transaction correctly`() {
            val sender = "BP-AXISBK"
            val body = "INR 1,200.00 sent from A/c no. XX4501 to JOHN VIA UPI. UPI Ref 423100842. Bal 5,400.00"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(1200.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.OUTFLOW, result.flowDirection)
            assertEquals("4501", result.accountLast4)
            assertEquals(5400.00, result.availableBalance!!, 0.001)
            assertEquals("AXIS", result.bankCode)
            assertEquals("Axis Bank", result.bankName)
        }

        @Test
        @DisplayName("Axis Bank UPI Inflow / Received")
        fun `should parse Axis Bank UPI received transaction correctly`() {
            val sender = "VK-AXISBK"
            val body = "INR 5,000.00 received in A/c no. XX4501 from RAJ VIA UPI. Bal 10,400.00"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(5000.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.INFLOW, result.flowDirection)
            assertEquals("4501", result.accountLast4)
            assertEquals(10400.00, result.availableBalance!!, 0.001)
            assertEquals("AXIS", result.bankCode)
        }
    }

    @Nested
    @DisplayName("5. Chase Bank (US) Parser Tests")
    inner class ChaseBankTests {

        @Test
        @DisplayName("Chase USD Debit Purchase with Available Balance")
        fun `should parse Chase debit purchase correctly`() {
            val sender = "72166"
            val body = "Chase: You made a $45.20 debit card purchase with card ending in 8912. Available balance: $2,410.00"

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(45.20, result!!.amount, 0.001)
            assertEquals(FlowDirection.OUTFLOW, result.flowDirection)
            assertEquals("8912", result.accountLast4)
            assertEquals(2410.00, result.availableBalance!!, 0.001)
            assertEquals("CHASE", result.bankCode)
            assertEquals("USD", result.currencyCode)
        }

        @Test
        @DisplayName("Chase USD Direct Deposit")
        fun `should parse Chase direct deposit correctly`() {
            val sender = "CHASE"
            val body = "Chase: A $3,200.00 direct deposit was made to checking account ending in 1021."

            val result = parserEngine.parse(sender, body)

            assertNotNull(result)
            assertEquals(3200.00, result!!.amount, 0.001)
            assertEquals(FlowDirection.INFLOW, result.flowDirection)
            assertEquals("1021", result.accountLast4)
            assertNull(result.availableBalance)
            assertEquals("CHASE", result.bankCode)
            assertEquals("USD", result.currencyCode)
        }
    }

    @Nested
    @DisplayName("6. Generic Fallback Rule Tests")
    inner class GenericFallbackTests {

        @ParameterizedTest(name = "Generic Fallback Transaction: {0}")
        @MethodSource("com.flowledger.app.core.parser.SmsParserEngineTest#provideGenericTransactions")
        fun `should parse uncatalogued bank formats using generic fallback`(
            sender: String,
            body: String,
            expectedAmount: Double,
            expectedDirection: FlowDirection,
            expectedLast4: String,
            expectedBalance: Double?
        ) {
            val result = parserEngine.parse(sender, body)

            assertNotNull(result, "Expected generic fallback to parse message: $body")
            assertEquals(expectedAmount, result!!.amount, 0.01)
            assertEquals(expectedDirection, result.flowDirection)
            assertEquals(expectedLast4, result.accountLast4)
            if (expectedBalance != null) {
                assertEquals(expectedBalance, result.availableBalance!!, 0.01)
            } else {
                assertNull(result.availableBalance)
            }
        }
    }

    // =========================================================================
    // NEGATIVE TEST SUITES (PHASE 1 & PHASE 2 DROPPED MESSAGES)
    // =========================================================================

    @Nested
    @DisplayName("7. Negative Rejection Tests (Zero DB Write Guarantee)")
    inner class NegativeRejectionTests {

        @ParameterizedTest(name = "Phase 1 Invalid Sender Rejection: {0}")
        @MethodSource("com.flowledger.app.core.parser.SmsParserEngineTest#provideInvalidSenders")
        fun `should drop messages from invalid or personal senders in Phase 1`(sender: String, body: String) {
            val result = parserEngine.parse(sender, body)
            assertNull(result, "Message from personal/invalid sender must be dropped in Phase 1: $sender")
        }

        @ParameterizedTest(name = "Phase 2 OTP and Promo Rejection: {1}")
        @MethodSource("com.flowledger.app.core.parser.SmsParserEngineTest#provideOtpAndPromos")
        fun `should drop OTPs, promos, and non-transactions in Phase 2 before DB writes`(sender: String, body: String) {
            val result = parserEngine.parse(sender, body)
            assertNull(result, "Sensitive or non-transaction message must be dropped in Phase 2: $body")
        }

        @Test
        @DisplayName("Zero or Negative Amount Rejection")
        fun `should reject messages with zero amount`() {
            val sender = "VK-HDFCBK"
            val body = "Rs 0.00 debited from a/c **4092 on 24-08-26."

            val result = parserEngine.parse(sender, body)
            assertNull(result, "Transactions with 0 amount must be ignored")
        }
    }

    companion object {
        @JvmStatic
        fun provideGenericTransactions(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "DZ-KOTAKB",
                "Your Account XX9988 has been debited for INR 1,500.00 on 24-Aug-26. Avl Bal: INR 15,000.00",
                1500.00,
                FlowDirection.OUTFLOW,
                "9988",
                15000.00
            ),
            Arguments.of(
                "CP-YESBNK",
                "INR 7,200.00 has been credited to your account XX5544. Bal Rs 32,400.00",
                7200.00,
                FlowDirection.INFLOW,
                "5544",
                32400.00
            ),
            Arguments.of(
                "32552", // BOA
                "Bank of America: A purchase of $150.00 was made on card ending in 4422. Balance: $1,200.00",
                150.00,
                FlowDirection.OUTFLOW,
                "4422",
                1200.00
            ),
            Arguments.of(
                "IX-INDBNK",
                "Rs 2,300.00 reversed to your A/C XX7711 on 24-Aug-26. Avl Bal Rs 18,900.00",
                2300.00,
                FlowDirection.INFLOW, // Refund/reversed maps to INFLOW
                "7711",
                18900.00
            )
        )

        @JvmStatic
        fun provideInvalidSenders(): Stream<Arguments> = Stream.of(
            Arguments.of("+919876543210", "Rs 5,400.00 debited from a/c **4092. Bal: Rs 42,100"),
            Arguments.of("9876543210", "Acct XX102 credited with INR 12,000.00. Avl Bal INR 85,400.00"),
            Arguments.of("+14155552671", "Chase: You made a $45.20 debit card purchase with card ending in 8912."),
            Arguments.of("SPAMMER", "Rs 10,000 credited to account XX4092."),
            Arguments.of("SWIGGY", "Your order of Rs 450 is placed."),
            Arguments.of("UBER", "Your trip receipt: $25.40")
        )

        @JvmStatic
        fun provideOtpAndPromos(): Stream<Arguments> = Stream.of(
            Arguments.of("VK-HDFCBK", "928341 is your secret OTP for transaction of Rs 500 at Amazon. Do not share."),
            Arguments.of("AD-ICICIB", "Your one-time password (OTP) is 102938. Valid for 3 mins. Bank never asks for OTP."),
            Arguments.of("JM-SBIBNK", "Use verification code 492019 to authenticate NetBanking login."),
            Arguments.of("BP-AXISBK", "Never share your secret pin, CVV or OTP with anyone."),
            Arguments.of("VK-HDFCBK", "Get pre-approved personal loan of Rs 5 Lakhs at zero processing fee. Apply now!"),
            Arguments.of("AD-ICICIB", "Get 15% cashback offer on Amazon shopping. Limited offer."),
            Arguments.of("72166", "Chase: 849201 is your one-time verification code. Do not share it.")
        )
    }
}
