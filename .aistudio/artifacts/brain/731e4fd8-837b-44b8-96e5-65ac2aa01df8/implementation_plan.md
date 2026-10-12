# Multi-Provider BYOK (Bring Your Own Key) Architecture for Receipt Scanner

## Overview & Core Concept

This revision upgrades the Receipt Scanner Android app from a hardcoded single-credential implementation (`BuildConfig.GEMINI_API_KEY`) to a secure, modular **multi-provider BYOK (Bring Your Own Key)** architecture. Users can configure, validate, and manage their own API keys and choose vision-capable models across:
- **Google Gemini**
- **OpenAI**
- **Anthropic**
- **Custom OpenAI-compatible endpoints** (e.g. self-hosted vLLM, Ollama, OpenRouter, LocalAI)

Without an active key, the spreadsheet import, template selection, and built-in **Quick Test Samples** remain fully operational as labeled demonstrations, while camera and gallery scans require explicit provider setup.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed during the clarification phase:
> - **Settings Access**: A dedicated Settings screen accessible via a gear/settings icon on the top app bar.
> - **Model Selection**: Curated, verified vision models in a dropdown menu with support for entering a custom model identifier.
> - **Validation Strategy**: Use non-generative model/account probes where supported (Gemini, OpenAI), or a minimal 1-token test call with clear quota notice (Anthropic, custom endpoints).

---

## 1. Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                              UI LAYER                                  │
│  ReceiptScannerScreen                                                  │
│   ├── TopAppBarSection (App branding, Help dialog, Settings action)     │
│   ├── SettingsDialog / Screen (Provider selection, Key entry, Model,   │
│   │                            Validation, Host confirmation)          │
│   ├── SpreadsheetPickerCard (Preserved: .xlsx, .xls, Templates)        │
│   ├── ReceiptCaptureSection (Camera, Gallery, Quick Demo Samples)      │
│   ├── MappedReviewDialog (Receipt preview, Provider/Model badge,       │
│   │                       Live mapped fields & synced summary)         │
│   └── SpreadsheetTableView (Data grid, live totals, XLSX download)     │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                           VIEWMODEL LAYER                              │
│  ReceiptScannerViewModel                                               │
│   ├── Active Provider & Model State                                    │
│   ├── Provider Credential Validation State                             │
│   ├── Scan State: Idle, Analyzing(provider, model), Review, Error      │
│   ├── Zero Workbook Reset on Settings Changes                          │
│   └── Header Drift Protection (validates headers prior to append)      │
└───────────────────────┬───────────────────────────────┬────────────────┘
                        │                               │
┌───────────────────────▼──────────────┐   ┌────────────▼───────────────┐
│     ENCRYPTED CREDENTIAL STORAGE     │   │   AI PROVIDER ABSTRACTION  │
│  KeystoreCredentialStore             │   │  ReceiptExtractionProvider │
│   - Android Keystore AES-256 GCM     │   │   ├── GeminiProvider       │
│   - App-private storage              │   │   ├── OpenAiProvider       │
│   - Excluded from cloud backup       │   │   ├── AnthropicProvider    │
│   - In-memory clear on key deletion  │   │   └── CustomOpenAiProvider │
│   - Origin-bound custom endpoint keys│   └────────────┬───────────────┘
└──────────────────────────────────────┘                │
                                           ┌────────────▼───────────────┐
                                           │    SECURE OKHTTP CLIENT    │
                                           │  - Strict HTTPS validation │
                                           │  - Cross-origin redirect   │
                                           │    credential stripper     │
                                           │  - Bounded image scaling   │
                                           │  - Exif orientation fix    │
                                           └────────────────────────────┘
```

---

## 2. Key Product Decisions & Trade-Offs

### Credential Protection & Storage (API 24+)
- **Approach**: Android Keystore AES-256 GCM key (`KeyGenParameterSpec`) encrypting per-provider configuration stored in app-private storage (`byok_credentials.enc`).
- **Backup Exclusion**: Update `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` to exclude the encrypted credentials file from cloud auto-backup and device migration.
- **Key Deletion**: Explicit "Delete Key" action that deletes the encrypted payload, clears memory buffers, and informs the user that local removal does not revoke the key at the provider console.
- **Honest Security Posture**: Clear in-app notice that on a rooted or compromised device, runtime memory cannot guarantee absolute secrecy.

### Elimination of Shared Build-Time Credentials
- Remove reliance on `BuildConfig.GEMINI_API_KEY`.
- Clean up `.env.example` to remove the default `GEMINI_API_KEY` placeholder.
- Real scanning requires a user-configured key; unconfigured states prompt the user with a setup button.

### Elimination of Fabricated Results
- Completely remove fallback simulators from the production scanning path (`createSimulatedResult` for failed scans is removed).
- Failures (missing credentials, network timeouts, bad responses, quota limits, unreadable images) yield explicit sanitized errors.
- Quick Test Samples are strictly labeled as **DEMO MODE** during review, preventing confusion.

### Header Drift & Scan Snapshot
- Every scan captures an immutable snapshot of `(providerId, modelId, workbookHeaders)`.
- If the user modifies, switches, or uploads a new spreadsheet while a scan is in review, the app detects the column mismatch and requires re-matching rather than appending corrupted data.
- Editing fields in `MappedReviewDialog` updates the summary cards (Merchant, Date, Total) in real time so the user sees exactly what will be written to the spreadsheet.

---

## 3. Provider Implementations & Request Formats

### 1. Google Gemini (`GeminiProvider`)
- **Authentication**: `x-goog-api-key: <key>` header (never sent as a query parameter in URLs).
- **Endpoint**: `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
- **Validation**: Non-generative `GET https://generativelanguage.googleapis.com/v1beta/models?pageSize=1` with `x-goog-api-key`.
- **Curated Models**: `gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-1.5-flash-latest`, `gemini-1.5-pro-latest` (verified mid-2024 / current preview).

### 2. OpenAI (`OpenAiProvider`)
- **Authentication**: `Authorization: Bearer <key>` header.
- **Endpoint**: `https://api.openai.com/v1/chat/completions`
- **Image Payload**: `messages[].content[]` with `type: "image_url"`, `image_url: { "url": "data:image/jpeg;base64,..." }`.
- **Validation**: Non-generative `GET https://api.openai.com/v1/models` checking account authorization.
- **Curated Models**: `gpt-4o`, `gpt-4o-mini`, `gpt-4-turbo`.

### 3. Anthropic (`AnthropicProvider`)
- **Authentication**: `x-api-key: <key>`, `anthropic-version: 2023-06-01`.
- **Endpoint**: `https://api.anthropic.com/v1/messages`
- **Image Payload**: `messages[].content[]` with `type: "image"`, `source: { "type": "base64", "media_type": "image/jpeg", "data": "..." }`.
- **Validation**: Minimal 1-token test prompt (`max_tokens: 1`) with an explicit warning explaining that minimal quota may be consumed.
- **Curated Models**: `claude-3-5-sonnet-20241022`, `claude-3-5-haiku-20241022`, `claude-3-opus-20240229`.

### 4. Custom OpenAI-Compatible (`CustomOpenAiProvider`)
- **Validation**: HTTPS enforced for remote endpoints; cleartext HTTP strictly restricted to localhost/10.0.2.2 in debug builds.
- **Host Confirmation**: Explicit user confirmation of the target hostname required before sending credentials or images.
- **Redirect Security**: OkHttp redirect interceptor that prevents sending `Authorization` headers across domain boundaries.

---

## 4. Testing & Verification Strategy

### Automated Unit & Integration Tests (`app/src/test/java/com/example/`)
1. **`KeystoreCredentialStoreTest`**: Verify credential encryption, decryption, per-provider isolation, deletion, and handling of corrupted storage.
2. **`ProviderRequestFormatTest`**: MockWebServer tests verifying headers (`x-goog-api-key`, `Authorization: Bearer`, `x-api-key`), image base64 payloads, and structured JSON parsing for all four providers.
3. **`ErrorHandlingAndNoFabricationTest`**: Verify that 401 Unauthorized, 429 Quota Exceeded, 500 Server Error, and empty/malformed responses trigger explicit sanitized `ScanState.Error`, with zero fabricated values.
4. **`ReviewFlowReliabilityTest`**: Verify that review summary updates when mapped fields are edited, and header drift prevents incompatible appends.

### Build & Verification Commands
- `compile_applet`: Ensures clean compilation of all Kotlin and Compose components.
- `gradle :app:testDebugUnitTest`: Runs all local JVM and Robolectric unit tests.
