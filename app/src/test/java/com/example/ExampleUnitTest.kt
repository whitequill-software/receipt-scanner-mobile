package com.example

import com.example.ai.GeminiVisionService
import com.example.model.SpreadsheetWorkbook
import com.example.spreadsheet.DefaultTemplates
import com.example.spreadsheet.XlsxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

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
    fun testSmartColumnMatching() {
        val matchedMerchant = GeminiVisionService.smartColumnMatch(
            colName = "Store / Vendor",
            merchant = "Whole Foods",
            date = "2026-10-12",
            total = "45.00",
            subtotal = "41.50",
            tax = "3.50",
            tip = "0.00",
            category = "Groceries",
            payment = "Apple Pay",
            items = "Apples, Milk"
        )
        assertEquals("Whole Foods", matchedMerchant)

        val matchedTotal = GeminiVisionService.smartColumnMatch(
            colName = "Total Amount",
            merchant = "Whole Foods",
            date = "2026-10-12",
            total = "45.00",
            subtotal = "41.50",
            tax = "3.50",
            tip = "0.00",
            category = "Groceries",
            payment = "Apple Pay",
            items = "Apples, Milk"
        )
        assertEquals("45.00", matchedTotal)
    }
}
