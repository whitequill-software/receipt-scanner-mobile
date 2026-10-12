package com.example

import com.example.byok.model.ValidationErrorType
import com.example.byok.provider.CustomOpenAiProvider
import com.example.byok.util.ReceiptJsonParser
import com.example.byok.util.SanitizedErrorUtil
import com.example.model.SpreadsheetWorkbook
import com.example.spreadsheet.XlsxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun testColumnIndexToLetter() {
        assertEquals("A", XlsxEngine.columnIndexToLetter(0))
        assertEquals("B", XlsxEngine.columnIndexToLetter(1))
        assertEquals("Z", XlsxEngine.columnIndexToLetter(25))
        assertEquals("AA", XlsxEngine.columnIndexToLetter(26))
    }

    @Test
    fun testGenerateAndParseXlsx() {
        val originalWorkbook = SpreadsheetWorkbook(
            name = "Test_Expenses.xlsx",
            headers = listOf("Date", "Vendor", "Total", "Category"),
            rows = listOf(
                mapOf("Date" to "2026-10-12", "Vendor" to "Cafe Nero", "Total" to "14.50", "Category" to "Meals")
            )
        )

        val xlsxBytes = XlsxEngine.generateXlsxBytes(originalWorkbook)
        assertNotNull(xlsxBytes)
        assertTrue(xlsxBytes.isNotEmpty())

        val parsedWorkbook = XlsxEngine.parseSpreadsheet(ByteArrayInputStream(xlsxBytes), "Test_Expenses.xlsx")
        assertEquals(4, parsedWorkbook.headers.size)
        assertEquals("Date", parsedWorkbook.headers[0])
        assertEquals("Vendor", parsedWorkbook.headers[1])
        assertEquals("Total", parsedWorkbook.headers[2])
        assertEquals("Category", parsedWorkbook.headers[3])
        assertEquals(1, parsedWorkbook.rows.size)
        assertEquals("Cafe Nero", parsedWorkbook.rows[0]["Vendor"])
        assertEquals("14.50", parsedWorkbook.rows[0]["Total"])
    }

    @Test
    fun testReceiptJsonParserWithoutFabrication() {
        val jsonPayload = """
        {
            "merchant": "Target Express",
            "date": "2026-10-12",
            "total": "38.50",
            "subtotal": "35.00",
            "tax": "3.50",
            "currency": "USD",
            "category": "Office Supplies",
            "payment_method": "Visa *1234",
            "item_summary": "Printer paper, Sticky notes",
            "column_mappings": {
                "Vendor": "Target Express",
                "Transaction Date": "2026-10-12",
                "Total Amount": "38.50",
                "Unmatched Custom Column": ""
            }
        }
        """.trimIndent()

        val columns = listOf("Vendor", "Transaction Date", "Total Amount", "Unmatched Custom Column")
        val receiptData = ReceiptJsonParser.parseJsonResponse(jsonPayload, null, columns)

        assertEquals("Target Express", receiptData.merchant)
        assertEquals("2026-10-12", receiptData.date)
        assertEquals("38.50", receiptData.total)
        assertEquals("35.00", receiptData.subtotal)
        assertEquals("3.50", receiptData.tax)
        assertEquals("USD", receiptData.currency)
        assertEquals("Target Express", receiptData.mappedColumns["Vendor"])
        assertEquals("2026-10-12", receiptData.mappedColumns["Transaction Date"])
        assertEquals("38.50", receiptData.mappedColumns["Total Amount"])
        // Must be empty string when not detected; never fabricated!
        assertEquals("", receiptData.mappedColumns["Unmatched Custom Column"])
    }

    @Test
    fun testSanitizedErrorMessages() {
        val (authMessage, authType) = SanitizedErrorUtil.sanitizeErrorMessage("Invalid API Key: Bearer sk-1234567890abcdef", 401)
        assertEquals(ValidationErrorType.AUTHENTICATION, authType)
        assertFalse(authMessage.contains("sk-1234567890abcdef"))
        assertTrue(authMessage.contains("Authentication failed"))

        val (quotaMessage, quotaType) = SanitizedErrorUtil.sanitizeErrorMessage("rate_limit_exceeded: quota reached", 429)
        assertEquals(ValidationErrorType.QUOTA_EXCEEDED, quotaType)
        assertTrue(quotaMessage.contains("quota or rate limit exceeded"))
    }

    @Test
    fun testCustomEndpointSecurityValidation() {
        // Plain HTTP on remote host must fail
        val (isRemoteHttpValid, err1) = CustomOpenAiProvider.validateEndpointSecurity("http://api.remote-llm.com/v1", "api.remote-llm.com")
        assertFalse(isRemoteHttpValid)
        assertTrue(err1.contains("HTTPS"))

        // Unconfirmed host must fail
        val (isUnconfirmedValid, err2) = CustomOpenAiProvider.validateEndpointSecurity("https://api.remote-llm.com/v1", "other-host.com")
        assertFalse(isUnconfirmedValid)
        assertTrue(err2.contains("has not been confirmed"))

        // Confirmed HTTPS host must pass
        val (isHttpsValid, _) = CustomOpenAiProvider.validateEndpointSecurity("https://api.remote-llm.com/v1", "api.remote-llm.com")
        assertTrue(isHttpsValid)

        // Local development host allows HTTP
        val (isLocalValid, _) = CustomOpenAiProvider.validateEndpointSecurity("http://10.0.2.2:8000/v1", "10.0.2.2")
        assertTrue(isLocalValid)
    }

    @Test
    fun testCustomEndpointNormalize() {
        assertEquals("https://api.example.com/v1/chat/completions", CustomOpenAiProvider.normalizeChatEndpoint("https://api.example.com/v1"))
        assertEquals("https://api.example.com/v1/chat/completions", CustomOpenAiProvider.normalizeChatEndpoint("https://api.example.com/v1/chat/completions"))
        assertEquals("https://api.example.com/v1/chat/completions", CustomOpenAiProvider.normalizeChatEndpoint("https://api.example.com"))
    }
}
