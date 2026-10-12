# Receipt Scanner Mobile

An Android app that turns receipt photos into reviewed spreadsheet rows using your own AI API key.

Built with Kotlin and Jetpack Compose, Receipt Scanner Mobile lets you capture a receipt, map its extracted values to your spreadsheet columns, review and edit the results, then export or share an XLSX file.

## Features

- Capture receipts with Camera or choose an image from Gallery.
- Bring your own API key for Google Gemini, OpenAI, or Anthropic Claude.
- Configure a custom OpenAI-compatible chat-completions endpoint with image support.
- Choose a spreadsheet template or import a supported spreadsheet.
- Review and edit mapped values before adding a row.
- Export or share an XLSX copy.
- Explore labeled demo samples without an API key.
- Save per-provider credentials encrypted in app-private storage, normally using Android Keystore.

## Project status

The Android source and multi-provider BYOK implementation are committed to this repository. The operating procedure is available at [docs/SOP.md](docs/SOP.md).

This is an evaluation-stage implementation with known limitations. Source presence does not establish a successful build or production readiness. No GitHub Release or downloadable APK was published when this README was prepared on 12 October 2026. Android builds, device behavior, and live provider requests were not independently run during the documentation review.

## Quick start for users

An installed development or approved release build is required. Source code alone is not an installable app; see [Development setup](#development-setup) to build it.

1. Open **Settings** using the gear icon.
2. Choose your provider and enter your own API key.
3. Select or type a currently available image-capable model. Complete endpoint and hostname confirmation for a custom server.
4. Tap **Validate Key**, then **Save Credentials**.
5. On **Scanner & Setup**, choose a template or import a simple spreadsheet copy.
6. Tap **Camera** or **Gallery** and select a receipt.
7. Compare every mapped field with the original, correct errors, and tap **Add to Sheet**.
8. Tap **Download .xlsx** and open the saved file in an external spreadsheet reader to confirm it is correct. Use Share if needed and verify delivery in the receiving app.

**Export before closing the app, switching templates, or importing another workbook.** Workbook changes remain in memory and are not automatically saved. Business Expenses and Travel & Per-Diem start with example rows; delete those rows before real use. Keep demo work separate from real records.

## AI providers and BYOK

| Provider | Implemented API | Key setup |
| --- | --- | --- |
| Google Gemini | Gemini `generateContent` with inline JPEG | [Google AI Studio](https://aistudio.google.com/app/apikey) |
| OpenAI | Chat Completions with image data URL | [OpenAI Platform](https://platform.openai.com/api-keys) |
| Anthropic Claude | Messages API with Base64 JPEG | [Anthropic Console](https://console.anthropic.com/settings/keys) |
| Custom compatible server | OpenAI-style `/v1/chat/completions` accepting images | Obtain configuration from the server administrator |

Each provider requires a compatible model and account access. The selected provider receives the receipt image and spreadsheet column names. Requests use your API account's quota and applicable billing; do not assume a consumer chat subscription includes API credits.

**Model choices need review:** the Anthropic dropdown currently contains retired models. Enter a current accessible image-capable model manually and test it. Consult the [official Anthropic retirement table](https://platform.claude.com/docs/en/about-claude/model-deprecations), [Gemini model catalog](https://ai.google.dev/gemini-api/docs/models), or [OpenAI model catalog](https://developers.openai.com/api/docs/models).

Validation is limited: Gemini and OpenAI list models; Anthropic and custom endpoints send a small text probe that may consume usage. A successful validation does not prove receipt-image extraction works. Test with a sanitized receipt before using real data.

Saved credentials are separate by provider. Active provider selection resets to Gemini after process restart, so reselect your intended provider each session. Deleting a key in the app removes its local saved copy; revoke it at the provider to invalidate it.

### Custom endpoints

Use a trusted HTTPS endpoint with a compatible image-capable model. Prefer an explicit URL ending in `/v1/chat/completions` and confirm its hostname in Settings.

Custom support is conditional, not universal. The current UI/scanning flow requires a nonblank key, including for custom servers. Only one custom profile slot exists, and credentials are not bound to an exact endpoint origin. Delete the old custom key before changing destinations. Avoid redirecting endpoints until redirect protection is fixed and tested. Local HTTP is not a verified supported flow. See the [SOP](docs/SOP.md) for details.

## Development setup

### Requirements

- Git and Android Studio compatible with Android Gradle Plugin 9.1.1
- JDK 17 for Gradle
- Android SDK Platform 36.1, Build Tools 36.0.0, and platform tools
- Android device/emulator running API 24 or later
- Gradle 9.3.1 for the initial wrapper repair described below

Project pins include AGP 9.1.1, Kotlin Compose 2.2.10, and KSP 2.3.5. Java source/target compatibility is 11; Gradle runs with JDK 17. See [AGP compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

```powershell
git clone https://github.com/whitequill-software/receipt-scanner-mobile.git
Set-Location receipt-scanner-mobile
```

Open the root in Android Studio and configure the SDK path.

**Fresh-clone prerequisites:** the repository currently contains wrapper properties but lacks `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar`. Restore trusted original wrapper files or generate them using an official Gradle 9.3.1 installation:

```powershell
gradle wrapper --gradle-version 9.3.1 --distribution-type bin
```

If project configuration prevents generation, follow the separate minimal-project procedure in the SOP. Do not download wrapper JARs from arbitrary sources.

Debug signing expects a root `debug.keystore`, which is intentionally not uploaded. Restore the original key for updates to an existing installation, or follow the SOP to generate a development-only key for a fresh install.

No shared AI credential is required in `.env`; enter user keys in the app. Remove any stale shared-key value from local build environment files before building, because the Secrets Gradle plugin remains configured.

### Build and run

After restoring the wrapper and debug signing prerequisites:

```powershell
.\gradlew.bat :app:assembleDebug
adb devices
.\gradlew.bat :app:installDebug
adb shell am start -n com.aistudio.receiptscanner.xkvjtw/com.example.MainActivity
```

Expected debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Alternatively, run the `app` module's debug variant through Android Studio. On macOS/Linux use `./gradlew`.

### Tests

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The second command requires a device/emulator. Existing tests cover basic spreadsheet round trips, receipt mapping, selected errors, endpoint checks, credential lifecycle, profile metadata, settings/workbook preservation, header drift, and demo behavior. They do not prove live API compatibility or device Keystore/redirect security. See the SOP's acceptance checklist.

## Security and data handling

- No shared Gemini key is required by the current receipt-scanning code. Previously distributed APKs may still contain an old key; removing it from new code does not revoke it.
- User credentials are encrypted with AES-GCM. The store normally uses Android Keystore, but currently contains an unrestricted in-memory fallback if Keystore initialization fails. Hardware-backed protection is not guaranteed.
- Credential files are excluded from configured backups; provider metadata is stored separately and is not excluded. Never put secrets in endpoint URLs.
- Local encryption cannot guarantee secrecy on a compromised device. Keep revealed keys out of screenshots, logs, and public issues.
- Custom-origin isolation, redirect handling, and error redaction require further hardening before broad distribution.

## Known data limitations

- No durable workbook storage, duplicate detection, table edit/undo flow, or receipt-image audit trail.
- XLSX import reads only `sheet1.xml`; export rebuilds a single sheet and does not preserve other sheets, formatting, or formulas.
- Sparse cells and duplicate headers can misalign or overwrite imported data. Legacy binary `.xls` is not supported despite the upload label.
- The displayed summary can select Subtotal, and the grand total can double-count multiple amount columns. Reconcile final amounts externally.
- Parsed JSON is not fully validated for meaningful receipt content. Review every field even after a successful scan.
- Export/share success messages do not prove a valid saved file or completed delivery. Always check the destination.

Use simple spreadsheet copies with unique headers and plain decimal amounts. Preserve originals and independently reopen every exported workbook.

## Source layout

```text
app/src/main/java/com/example/
├── byok/crypto/       Encrypted credential and profile storage
├── byok/model/        Provider configuration and scan result types
├── byok/net/          Shared HTTP client
├── byok/provider/     Provider adapters and registry
├── byok/util/         Image processing, JSON parsing, error handling
├── model/            Workbook and sample receipt models
├── spreadsheet/      Templates and XLSX engine
└── ui/               ViewModel, screens, settings, review and table controls
docs/SOP.md           Full operating and maintenance procedure
```

## Releases and maintenance

There is no committed GitHub Actions workflow. Release signing uses `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`, with alias `upload`; keep release keystores outside the repository. Preserve the application ID and signing identity for updates, and increase version code before distribution.

Follow [docs/SOP.md](docs/SOP.md) for release gates, signing, acceptance evidence, recovery, maintenance, and troubleshooting. Report defects with sanitized reproduction steps, device/OS, source commit, provider/model, and expected versus actual results. Never include API keys or private receipt images in public reports.
