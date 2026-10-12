package com.example.byok.util

import android.net.Uri
import com.example.byok.model.ReceiptData
import org.json.JSONObject
import java.util.Locale

object ReceiptJsonParser {

    fun parseJsonResponse(
        jsonString: String,
        imageUri: Uri?,
        targetColumns: List<String>
    ): ReceiptData {
        // Strip markdown code fences if model returned them
        val cleanJson = jsonString.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val root = JSONObject(cleanJson)

        val merchant = root.optString("merchant", "").trim()
        val date = root.optString("date", "").trim()
        val subtotal = root.optString("subtotal", "").trim()
        val tax = root.optString("tax", "").trim()
        val tip = root.optString("tip", "").trim()
        val total = root.optString("total", "").trim()
        val currency = root.optString("currency", "").trim()
        val category = root.optString("category", "").trim()
        val paymentMethod = root.optString("payment_method", "").trim()
        val itemSummary = root.optString("item_summary", "").ifBlank {
            root.optString("items_summary", "").trim()
        }

        val columnMappingsObj = root.optJSONObject("column_mappings")
        val mappings = mutableMapOf<String, String>()

        targetColumns.forEach { col ->
            var value = columnMappingsObj?.optString(col, "")?.trim() ?: ""
            if (value.isBlank()) {
                value = matchKnownFieldToColumn(col, merchant, date, subtotal, tax, tip, total, currency, category, paymentMethod, itemSummary)
            }
            mappings[col] = value
        }

        return ReceiptData(
            imageUri = imageUri,
            merchant = merchant,
            date = date,
            subtotal = subtotal,
            tax = tax,
            tip = tip,
            total = total,
            currency = currency,
            category = category,
            paymentMethod = paymentMethod,
            itemSummary = itemSummary,
            mappedColumns = mappings,
            isDemo = false
        )
    }

    private fun matchKnownFieldToColumn(
        colName: String,
        merchant: String,
        date: String,
        subtotal: String,
        tax: String,
        tip: String,
        total: String,
        currency: String,
        category: String,
        paymentMethod: String,
        itemSummary: String
    ): String {
        val lower = colName.lowercase(Locale.ROOT)
        return when {
            lower.contains("date") || lower.contains("time") -> date
            lower.contains("vendor") || lower.contains("merchant") || lower.contains("payee") || lower.contains("store") -> merchant
            lower.contains("subtotal") || lower.contains("net") -> subtotal
            lower.contains("tax") || lower.contains("vat") || lower.contains("gst") -> tax
            lower.contains("tip") || lower.contains("gratuity") -> tip
            lower.contains("total") || lower.contains("amount") || lower.contains("cost") || lower.contains("price") -> total
            lower.contains("currency") -> currency
            lower.contains("category") || lower.contains("type") -> category
            lower.contains("payment") || lower.contains("method") || lower.contains("card") -> paymentMethod
            lower.contains("desc") || lower.contains("item") || lower.contains("summary") || lower.contains("purpose") -> itemSummary
            else -> "" // Blank for unknown fields; never invent data!
        }
    }

    fun buildSystemPrompt(targetColumns: List<String>): String {
        val columnsJson = targetColumns.joinToString("\", \"", prefix = "[\"", postfix = "\"]")
        return """
You are a financial receipt extraction assistant.
Inspect the provided receipt image and extract the receipt information into JSON format.
Do NOT invent information that is unreadable or not present on the receipt; leave those fields as empty strings "".

Expected JSON schema:
{
  "merchant": "Vendor or store name",
  "date": "Transaction date, preferred YYYY-MM-DD",
  "subtotal": "Subtotal before taxes as decimal or empty",
  "tax": "Tax amount as decimal or empty",
  "tip": "Tip or gratuity as decimal or empty",
  "total": "Final total amount as decimal",
  "currency": "Currency symbol or code (e.g. $, USD, EUR) or empty",
  "category": "Suggested expense category (e.g. Meals, Office Supplies, Travel, Utilities) or empty",
  "payment_method": "Payment method identified (e.g. Visa *1234, Cash, Amex) or empty",
  "item_summary": "Brief summary of purchased items or service",
  "column_mappings": {
    "TargetColumn": "Best extracted value for this column"
  }
}

The user's spreadsheet columns to map are:
$columnsJson
Ensure each requested column name is present as a key in "column_mappings".
Respond strictly with valid JSON.
""".trimIndent()
    }
}
