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

class GeminiProvider : ReceiptExtractionProvider {
    override val provider: AiProvider = AiProvider.GEMINI

    override suspend fun validateCredentials(config: ProviderConfig): ProviderValidationResult = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            return@withContext ProviderValidationResult.Error(
                "Please enter an API key for Google Gemini.",
                com.example.byok.model.ValidationErrorType.AUTHENTICATION
            )
        }

        try {
            // Non-generative check: List models with pageSize=1
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1")
                .header("x-goog-api-key", config.apiKey.trim())
                .get()
                .build()

            val response = SecureHttpClient.instance.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful) {
                val hasModels = body.contains("models/")
                val modelName = config.selectedModel.ifBlank { provider.defaultModel }
                val isCurated = provider.curatedVisionModels.contains(modelName)
                ProviderValidationResult.Success(
                    message = "Gemini API key verified successfully (non-generative quota probe).",
                    modelAccessible = hasModels,
                    isVisionCapabilityVerified = isCurated
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
                sanitizedMessage = "No API key configured for Google Gemini. Please configure your key in Settings.",
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
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", config.apiKey.trim())
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
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text", "") ?: ""

            if (text.isBlank()) {
                return@withContext ScanOutcome.Error(
                    sanitizedMessage = "Gemini returned an empty response. Receipt could not be extracted.",
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
