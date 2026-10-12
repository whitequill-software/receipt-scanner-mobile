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

class OpenAiProvider : ReceiptExtractionProvider {
    override val provider: AiProvider = AiProvider.OPENAI

    override suspend fun validateCredentials(config: ProviderConfig): ProviderValidationResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext ProviderValidationResult.Error(
                "Please enter an API key for OpenAI.",
                com.example.byok.model.ValidationErrorType.AUTHENTICATION
            )
        }

        try {
            // Non-generative check: List models
            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .header("Authorization", "Bearer ${config.apiKey.trim()}")
                .get()
                .build()

            val response = SecureHttpClient.instance.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful) {
                val targetModel = config.selectedModel.ifBlank { provider.defaultModel }
                val modelFound = body.contains("\"id\": \"$targetModel\"") || body.contains("\"id\":\"$targetModel\"")
                ProviderValidationResult.Success(
                    message = "OpenAI API key verified successfully (non-generative quota check).",
                    modelAccessible = modelFound,
                    isVisionCapabilityVerified = provider.curatedVisionModels.contains(targetModel)
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
                sanitizedMessage = "No API key configured for OpenAI. Please configure your key in Settings.",
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
                put("response_format", JSONObject().apply { put("type", "json_object") })
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", prompt)
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                    put("detail", "high")
                                })
                            })
                        })
                    })
                })
                put("temperature", 0.1)
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey.trim()}")
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
            val choices = root.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val text = message?.optString("content", "") ?: ""

            if (text.isBlank()) {
                return@withContext ScanOutcome.Error(
                    sanitizedMessage = "OpenAI returned an empty content response.",
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
