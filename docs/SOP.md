# Receipt Scanner Mobile — BYOK Standard Operating Procedure (SOP) & Architecture

This document outlines configuration, architecture, credential management, and security procedures for the Bring Your Own Key (BYOK) receipt extraction pipeline in **Receipt Scanner Mobile**.

---

## 1. Multi-Provider BYOK Overview

Receipt Scanner allows users to connect their own AI accounts and quotas directly to scan paper receipts and map extracted values to spreadsheet columns.

Supported Providers:
1. **Google Gemini**: Uses `https://generativelanguage.googleapis.com` with `x-goog-api-key`.
2. **OpenAI**: Uses `https://api.openai.com/v1/chat/completions` with `Authorization: Bearer <key>`.
3. **Anthropic Claude**: Uses `https://api.anthropic.com/v1/messages` with `x-api-key` and `anthropic-version`.
4. **Custom OpenAI-Compatible**: Self-hosted vLLM, Ollama, OpenRouter, or LocalAI servers.

---

## 2. User Setup Guide

### Step 1: Obtain an API Key
- **Gemini**: Create a key in [Google AI Studio](https://aistudio.google.com/app/apikey).
- **OpenAI**: Generate an API key in the [OpenAI Platform](https://platform.openai.com/api-keys).
- **Anthropic**: Create a key in the [Anthropic Console](https://console.anthropic.com/settings/keys).
- *Important*: Consumer subscriptions (e.g., ChatGPT Plus, Claude Pro, Gemini Advanced) do **not** include developer API access. Users must have an active developer account with billing/credits configured.

### Step 2: Configure & Validate Key
1. In the app, tap the **Settings (gear)** icon on the top navigation bar.
2. Select your AI provider.
3. Paste your API key. Keys are masked by default with an optional visibility toggle.
4. Select a recommended vision-capable model (e.g. `gemini-2.5-flash`, `gpt-4o-mini`, `claude-3-5-haiku-20241022`) or type a custom model identifier.
5. Tap **Validate Key**. The app performs an honest non-generative or minimal 1-token probe to verify connectivity and authentication without wasteful token spending.
6. Tap **Save Credentials**.

### Step 3: Scan Receipts & Map Columns
- Upload an existing `.xlsx` / `.xls` spreadsheet or select a built-in business template.
- Tap **Camera** or **Gallery** to scan.
- Review mapped values in the review dialog. Editing fields updates summary cards in real time before appending to the spreadsheet.

---

## 3. Security & Credential Protection

### Local Storage & Android Keystore (API 24+)
- **Cipher**: AES-256 GCM (`AES/GCM/NoPadding`) with 128-bit authentication tags.
- **Key Generation**: 256-bit AES master key generated and held inside the hardware-backed `AndroidKeyStore`.
- **App-Private Storage**: Encrypted credential payloads are stored in `byok_credentials.enc` in internal storage (`context.filesDir`).
- **Backup Exclusion**: `byok_credentials.enc` and credential preferences are explicitly excluded from Android cloud auto-backup and device-to-device transfers (`backup_rules.xml` and `data_extraction_rules.xml`).
- **Memory Lifetime**: Plaintext credentials are kept in memory only when executing API requests and are cleared from memory when deleted.
- **Honest Security Boundary**: While client-side encryption protects keys at rest against other unprivileged apps, device-level compromise or rooted devices cannot guarantee absolute memory confidentiality.

### Deleting Keys vs. Revocation
- Tapping **Delete Key** inside the app permanently wipes the local encrypted credential and memory cache.
- **Local deletion does NOT revoke the key at the provider**. Users who wish to permanently invalidate an API key must revoke or rotate it directly in their provider developer console.

### Custom Endpoints & Redirect Security
- Remote custom endpoints require **HTTPS**. Cleartext HTTP is strictly rejected for remote addresses and only permitted for local development (e.g. `localhost`, `10.0.2.2`).
- Users must explicitly confirm the target hostname before the app transmits credentials or receipt images.
- A dedicated OkHttp interceptor detects cross-origin redirections and automatically strips authorization headers to prevent credential leakage.

---

## 4. Migration & Shared Credential Revocation

> [!CAUTION]
> **Important Migration Notice**:
> Earlier versions of this application loaded a shared API key from `BuildConfig.GEMINI_API_KEY`.
> This revision completely removes the scanning dependency on `BuildConfig.GEMINI_API_KEY` and cleans up environment configurations.
>
> However, previously distributed APKs or builds might still contain that key in their compiled binaries. Removing the key from future builds **does NOT revoke it on Google Cloud/AI Studio**.
>
> The project administrator must independently review usage, rotate, or delete the previously used key directly in the Google AI Studio / Google Cloud Console.

---

## 5. Troubleshooting Common Provider Errors

| Error | Cause | Recommended Action |
| :--- | :--- | :--- |
| **HTTP 401 / 403** | Invalid or expired API key | Check key spelling, ensure no extra whitespace, verify key is active in provider console. |
| **HTTP 429** | Quota / rate limit exceeded | Check credit balance or monthly spend cap in your provider billing console. |
| **HTTP 404** | Model not found / inaccessible | Verify that your account has access to the chosen model; select a curated model. |
| **Timeout / Connection Error** | Network or endpoint failure | Check internet connection or verify that custom server is running and accessible. |
| **Unconfirmed Host** | Custom endpoint security gate | Tap "Confirm Host" in Settings for the custom endpoint URL. |
