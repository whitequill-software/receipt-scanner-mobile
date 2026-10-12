package com.example.byok.provider

import android.content.Context
import android.net.Uri
import com.example.byok.model.AiProvider
import com.example.byok.model.ProviderConfig
import com.example.byok.model.ProviderValidationResult
import com.example.byok.model.ScanOutcome
import com.example.byok.model.ValidationErrorType
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
import java.net.URI

class CustomOpenAiProvider : ReceiptExtractionProvider {
    override val provider: AiProvider = AiProvider.CUSTOM_OPENAI

    companion object {
        private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

        fun normalizeChatEndpoint(baseUrl: String): String {
            val trimmed = baseUrl.trim().removeSuffix("/")
            return if (trimmed.endsWith("/chat/completions")) {
                trimmed
            } else if (trimmed.endsWith("/v1")) {
                "$trimmed/chat/completions"
            } else {
                "$trimmed/v1/chat/completions"
            }
        }

        fun validateEndpointSecurity(urlStr: String, confirmedHost: String): Pair<Boolean, String> {
            val uri = try {
                URI.create(urlStr)
            } catch (e: Exception) {
                return Pair(false, "Invalid endpoint URL format.")
            }

            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""

            if (host.isBlank()) {
                return Pair(false, "Endpoint URL must specify a valid hostname.")
            }

            val isLocal = LOCAL_HOSTS.contains(host)
            if (!isLocal && scheme != "https") {
                return Pair(false, "Remote custom endpoints must use HTTPS. Plain HTTP is only permitted for local development (localhost / 10.0.2.2).")
            }

            if (!host.equals(confirmedHost.trim(), ignoreCase = true)) {
                return Pair(false, "Destination host '$host' has not been confirmed. Please confirm the endpoint host in Settings before sending requests.")
            }

            return Pair(true, "")
        }
    }

    override suspend fun validateCredentials(config: ProviderConfig): ProviderValidationResult = withContext(Dispatchers.IO) {
        val endpointUrl = config.customEndpointUrl.trim()
        if (endpointUrl.isBlank()) {
            return@withContext ProviderValidationResult.Error(
                "Custom endpoint URL is required.",
                ValidationErrorType.SECURITY_VIOLATION
            )
        }

        val (isSecure, secError) = validateEndpointSecurity(endpointUrl, config.confirmedHostname)
        if (!isSecure) {
            return@withContext ProviderValidationResult.Error(secError, ValidationErrorType.SECURITY_VIOLATION)
        }

        val model = config.selectedModel.trim()
        if (model.isBlank()) {
            return@withContext ProviderValidationResult.Error(
                "A model identifier is required for custom endpoints.",
                ValidationErrorType.MODEL_NOT_FOUND
            )
        }

        val targetUrl = normalizeChatEndpoint(endpointUrl)

        try {
            // Minimal 1-token test prompt to verify chat endpoint and model availability
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

            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))

            if (config.apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            val response = SecureHttpClient.instance.newCall(requestBuilder.build()).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful) {
                ProviderValidationResult.Success(
                    message = "Custom endpoint '$targetUrl' responded successfully to model probe.",
                    modelAccessible = true,
                    isVisionCapabilityVerified = false // Needs user verification for custom servers
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
        val endpointUrl = config.customEndpointUrl.trim()
        val (isSecure, secError) = validateEndpointSecurity(endpointUrl, config.confirmedHostname)
        if (!isSecure) {
            return@withContext ScanOutcome.Error(
                sanitizedMessage = secError,
                providerId = provider.id,
                modelUsed = config.selectedModel
            )
        }

        val model = config.selectedModel.trim()
        if (model.isBlank()) {
            return@withContext ScanOutcome.Error(
                sanitizedMessage = "Please specify a model identifier for your custom endpoint in Settings.",
                providerId = provider.id,
                modelUsed = ""
            )
        }

        val bitmap = ImagePreprocUtil.loadAndScaleBitmap(context, imageUri)
            ?: return@withContext ScanOutcome.Error(
                sanitizedMessage = "Could not decode receipt image file.",
                providerId = provider.id,
                modelUsed = model
            )

        val targetUrl = normalizeChatEndpoint(endpointUrl)
        val base64Image = ImagePreprocUtil.bitmapToBase64(bitmap)
        val prompt = ReceiptJsonParser.buildSystemPrompt(targetColumns)

        try {
            val jsonPayload = JSONObject().apply {
                put("model", model)
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
                                })
                            })
                        })
                    })
                })
                put("temperature", 0.1)
            }

            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))

            if (config.apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", "Bearer ${config.apiKey.trim()}")
            }

            val response = SecureHttpClient.instance.newCall(requestBuilder.build()).execute()
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
                    sanitizedMessage = "Custom endpoint returned empty content. Verify that server model supports vision input.",
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
