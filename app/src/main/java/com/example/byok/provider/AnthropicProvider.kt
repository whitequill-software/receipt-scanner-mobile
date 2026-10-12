package com.example.byok.provider

import android.content.Context
import android.net.Uri
import com.example.byok.model.AiProvider
import com.example.byok.model.ProviderConfig
import com.example.byok.model.ProviderValidationResult
import com.example.byok.model.ScanOutcome
import com.example.byok.net.SecureHttpClient
import com.example.byok.util.ImagePreprocUtil
import com.example.byok.util.ReceiptJsonParser
import com.example.byok.util.SanitizedErrorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AnthropicProvider : ReceiptExtractionProvider {
    override val provider: AiProvider = AiProvider.ANTHROPIC

    override suspend fun validateCredentials(config: ProviderConfig): ProviderValidationResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext ProviderValidationResult.Error(
                "Please enter an API key for Anthropic Claude.",
                com.example.byok.model.ValidationErrorType.AUTHENTICATION
            )
        }

        val model = config.selectedModel.ifBlank { provider.defaultModel }

        try {
            // Anthropic validation: Minimal 1-token message probe
            val jsonPayload = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", config.apiKey.trim())
                .header("anthropic-version", "2023-06-01")
                .post(requestBody)
                .build()

            val response = SecureHttpClient.instance.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful) {
                ProviderValidationResult.Success(
                    message = "Anthropic API key and model verified (minimal 1-token capability probe).",
                    modelAccessible = true,
                    isVisionCapabilityVerified = provider.curatedVisionModels.contains(model)
                )
            } else {
                val (sanitized, type) = SanitizedErrorUtil.sanitizeErrorMessage(body, code)
                ProviderValidationResult.Error(sanitized, type)
            }
        } catch (e: Exception) {
            val (sanitized, type) = SanitizedErrorUtil.sanitizeErrorMessage(e.message)
            ProviderValidationResult.Error(sanitized, type)
        }
    }

    override suspend fun extractReceipt(
        context: Context,
        imageUri: Uri,
        targetColumns: List<String>,
        config: ProviderConfig
    ): ScanOutcome = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext ScanOutcome.Error(
                sanitizedMessage = "No API key configured for Anthropic. Please configure your key in Settings.",
                providerId = provider.id,
                modelUsed = config.selectedModel
            )
        }

        val bitmap = ImagePreprocUtil.loadAndScaleBitmap(context, imageUri)
            ?: return@withContext ScanOutcome.Error(
                sanitizedMessage = "Could not decode receipt image file.",
                providerId = provider.id,
                modelUsed = config.selectedModel
            )

        val model = config.selectedModel.ifBlank { provider.defaultModel }
        val base64Image = ImagePreprocUtil.bitmapToBase64(bitmap)
        val prompt = ReceiptJsonParser.buildSystemPrompt(targetColumns)

        try {
            val jsonPayload = JSONObject().apply {
                put("model", model)
                put("max_tokens", 1500)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "image")
                                put("source", JSONObject().apply {
                                    put("type", "base64")
                                    put("media_type", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", config.apiKey.trim())
                .header("anthropic-version", "2023-06-01")
                .post(requestBody)
                .build()

            val response = SecureHttpClient.instance.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            response.close()

            if (!response.isSuccessful) {
                val (sanitized, _) = SanitizedErrorUtil.sanitizeErrorMessage(body, code)
                return@withContext ScanOutcome.Error(
                    sanitizedMessage = sanitized,
                    providerId = provider.id,
                    modelUsed = model
                )
            }

            val root = JSONObject(body)
            val contentArray = root.optJSONArray("content")
            val firstBlock = contentArray?.optJSONObject(0)
            val text = firstBlock?.optString("text", "") ?: ""

            if (text.isBlank()) {
                return@withContext ScanOutcome.Error(
                    sanitizedMessage = "Anthropic returned an empty response.",
                    providerId = provider.id,
                    modelUsed = model
                )
            }

            val receiptData = ReceiptJsonParser.parseJsonResponse(text, imageUri, targetColumns)
            ScanOutcome.Success(receiptData, provider.id, model)
        } catch (e: Exception) {
            val (sanitized, _) = SanitizedErrorUtil.sanitizeErrorMessage(e.message)
            ScanOutcome.Error(
                sanitizedMessage = sanitized,
                providerId = provider.id,
                modelUsed = model
            )
        }
    }
}
