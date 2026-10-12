package com.example.byok.util

import com.example.byok.model.ValidationErrorType

object SanitizedErrorUtil {

    fun sanitizeErrorMessage(rawMessage: String?, httpCode: Int = 0): Pair<String, ValidationErrorType> {
        val msg = rawMessage ?: ""

        val type = when {
            httpCode == 401 || httpCode == 403 || msg.contains("invalid_api_key", ignoreCase = true) ||
                    msg.contains("authentication", ignoreCase = true) || msg.contains("unauthorized", ignoreCase = true) ||
                    msg.contains("permission_denied", ignoreCase = true) -> ValidationErrorType.AUTHENTICATION

            httpCode == 429 || msg.contains("rate_limit", ignoreCase = true) ||
                    msg.contains("quota", ignoreCase = true) || msg.contains("resource_exhausted", ignoreCase = true) -> ValidationErrorType.QUOTA_EXCEEDED

            httpCode == 404 || msg.contains("model_not_found", ignoreCase = true) ||
                    msg.contains("does not exist", ignoreCase = true) -> ValidationErrorType.MODEL_NOT_FOUND

            msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) ||
                    msg.contains("connect", ignoreCase = true) || msg.contains("unreachable", ignoreCase = true) -> ValidationErrorType.NETWORK_TIMEOUT

            msg.contains("security", ignoreCase = true) || msg.contains("https required", ignoreCase = true) ||
                    msg.contains("unconfirmed host", ignoreCase = true) -> ValidationErrorType.SECURITY_VIOLATION

            msg.contains("json", ignoreCase = true) || msg.contains("parse", ignoreCase = true) -> ValidationErrorType.MALFORMED_RESPONSE

            else -> ValidationErrorType.UNKNOWN
        }

        val sanitizedText = when (type) {
            ValidationErrorType.AUTHENTICATION -> "Authentication failed (HTTP ${if (httpCode != 0) httpCode else "401"}). Please check that your API key is active and correctly entered."
            ValidationErrorType.QUOTA_EXCEEDED -> "Account quota or rate limit exceeded (HTTP ${if (httpCode != 0) httpCode else "429"}). Please check your billing or plan credits at your provider console."
            ValidationErrorType.MODEL_NOT_FOUND -> "The selected model was not found or is not accessible on your account. Please check model permissions."
            ValidationErrorType.NETWORK_TIMEOUT -> "Network connection timed out or endpoint is unreachable. Please check your internet connectivity."
            ValidationErrorType.SECURITY_VIOLATION -> "Security policy prevented the request: $msg"
            ValidationErrorType.MALFORMED_RESPONSE -> "Provider responded with invalid or unparseable receipt data."
            ValidationErrorType.UNKNOWN -> if (httpCode != 0) "Provider request failed with HTTP $httpCode." else "Request failed: ${filterTokens(msg)}"
        }

        return Pair(sanitizedText, type)
    }

    private fun filterTokens(text: String): String {
        return text
            .replace(Regex("(?i)bearer\\s+[a-zA-Z0-9_.-]+"), "Bearer [REDACTED]")
            .replace(Regex("(?i)key=[a-zA-Z0-9_.-]+"), "key=[REDACTED]")
            .replace(Regex("(?i)x-api-key:\\s*[a-zA-Z0-9_.-]+"), "x-api-key: [REDACTED]")
            .take(200)
    }
}
