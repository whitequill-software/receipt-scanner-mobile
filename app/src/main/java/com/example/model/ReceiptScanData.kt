package com.example.model

import android.net.Uri

data class ReceiptExtractionResult(
    val imageUri: Uri?,
    val merchant: String = "",
    val date: String = "",
    val totalAmount: String = "",
    val subtotal: String = "",
    val taxAmount: String = "",
    val tipAmount: String = "",
    val category: String = "",
    val paymentMethod: String = "",
    val itemsSummary: String = "",
    val mappedColumns: Map<String, String> = emptyMap(),
    val rawAiResponse: String = "",
    val isAiPowered: Boolean = true
)

data class SampleReceipt(
    val title: String,
    val merchant: String,
    val date: String,
    val total: String,
    val subtotal: String,
    val tax: String,
    val category: String,
    val payment: String,
    val items: String,
    val drawableRes: Int? = null
)
