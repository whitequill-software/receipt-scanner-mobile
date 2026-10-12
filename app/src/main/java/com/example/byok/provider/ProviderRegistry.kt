package com.example.byok.provider

import com.example.byok.model.AiProvider

object ProviderRegistry {
    private val providers = mapOf(
        AiProvider.GEMINI to GeminiProvider(),
        AiProvider.OPENAI to OpenAiProvider(),
        AiProvider.ANTHROPIC to AnthropicProvider(),
        AiProvider.CUSTOM_OPENAI to CustomOpenAiProvider()
    )

    fun getProvider(aiProvider: AiProvider): ReceiptExtractionProvider {
        return providers[aiProvider] ?: providers[AiProvider.GEMINI]!!
    }

    fun getAllProviders(): List<ReceiptExtractionProvider> = providers.values.toList()
}
