package com.example.byok.model

import android.net.Uri

enum class AiProvider(
    val id: String,
    val displayName: String,
    val setupUrl: String,
    val curatedVisionModels: List<String>,
    val defaultModel: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        setupUrl = "https://aistudio.google.com/app/apikey",
        curatedVisionModels = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-flash-latest", "gemini-1.5-pro-latest"),
        defaultModel = "gemini-2.5-flash"
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        setupUrl = "https://platform.openai.com/api-keys",
        curatedVisionModels = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo"),
        defaultModel = "gpt-4o-mini"
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic Claude",
        setupUrl = "https://console.anthropic.com/settings/keys",
        curatedVisionModels = listOf("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229"),
        defaultModel = "claude-3-5-haiku-20241022"
    ),
    CUSTOM_OPENAI(
        id = "custom_openai",
        displayName = "Custom OpenAI-Compatible",
        setupUrl = "",
        curatedVisionModels = emptyList(),
        defaultModel = ""
    );

    companion object {
        fun fromId(id: String): AiProvider = entries.find { it.id == id } ?: GEMINI
    }
}

data class ProviderProfile(
    val provider: AiProvider,
    val selectedModel: String,
    val customEndpointUrl: String = "",
    val confirmedHostname: String = "",
    val hasKey: Boolean = false
)

data class ProviderConfig(
    val provider: AiProvider,
    val apiKey: String,
    val selectedModel: String,
    val customEndpointUrl: String = "",
    val confirmedHostname: String = ""
)

sealed class ProviderValidationResult {
    object Unvalidated : ProviderValidationResult()
    object Validating : ProviderValidationResult()
    data class Success(
        val message: String,
        val modelAccessible: Boolean,
        val isVisionCapabilityVerified: Boolean
    ) : ProviderValidationResult()
    data class Error(
        val sanitizedMessage: String,
        val errorType: ValidationErrorType
    ) : ProviderValidationResult()
}

enum class ValidationErrorType {
    AUTHENTICATION,
    QUOTA_EXCEEDED,
    MODEL_NOT_FOUND,
    NETWORK_TIMEOUT,
    SECURITY_VIOLATION,
    MALFORMED_RESPONSE,
    UNKNOWN
}

data class ReceiptData(
    val imageUri: Uri? = null,
    val merchant: String = "",
    val date: String = "",
    val subtotal: String = "",
    val tax: String = "",
    val tip: String = "",
    val total: String = "",
    val currency: String = "",
    val category: String = "",
    val paymentMethod: String = "",
    val itemSummary: String = "",
    val mappedColumns: Map<String, String> = emptyMap(),
    val isDemo: Boolean = false
)

sealed class ScanOutcome {
    data class Success(
        val data: ReceiptData,
        val providerId: String,
        val modelUsed: String
    ) : ScanOutcome()

    data class Demo(
        val data: ReceiptData,
        val sampleTitle: String
    ) : ScanOutcome()

    data class Error(
        val sanitizedMessage: String,
        val providerId: String,
        val modelUsed: String,
        val canRetry: Boolean = true
    ) : ScanOutcome()
}
