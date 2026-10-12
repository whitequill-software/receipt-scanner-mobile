package com.example.byok.provider

import android.content.Context
import android.net.Uri
import com.example.byok.model.AiProvider
import com.example.byok.model.ProviderConfig
import com.example.byok.model.ProviderValidationResult
import com.example.byok.model.ScanOutcome

interface ReceiptExtractionProvider {
    val provider: AiProvider

    /**
     * Minimal or non-generative validation probe.
     */
    suspend fun validateCredentials(config: ProviderConfig): ProviderValidationResult

    /**
     * Extracts receipt data using the configured provider and image.
     */
    suspend fun extractReceipt(
        context: Context,
        imageUri: Uri,
        targetColumns: List<String>,
        config: ProviderConfig
    ): ScanOutcome
}
