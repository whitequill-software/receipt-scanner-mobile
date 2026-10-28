# Receipt Scanner Mobile BYOK Standard Operating Procedure

Repository: [whitequill-software/receipt-scanner-mobile](https://github.com/whitequill-software/receipt-scanner-mobile)  
Reviewed commit: [`ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500`](https://github.com/whitequill-software/receipt-scanner-mobile/tree/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500) on `main`  
Reviewed: 12 October 2026, Asia/Manila  
Repository file: `docs/SOP.md`

## 1. Purpose and current status

This procedure covers the revised Android receipt scanner: provider setup, user-owned API keys, receipt processing, spreadsheet export, development, verification, deployment, maintenance, security, and troubleshooting. It describes the committed implementation, including remaining limitations. It replaces the earlier shared-key SOP.

The source now has separate Gemini, OpenAI, Anthropic, and custom OpenAI-compatible adapters. Real scan failures return errors instead of the previous invented sample data. Demo samples are separate and labeled. API keys are entered in the app, stored encrypted, and sent in authentication headers rather than request URLs.

**Use controlled evaluation before public release.** The updated source was inspected, but no APK, live API request, device test, or build was executed in this review. Custom-endpoint credential isolation and redirect handling still need fixes and testing. The model list also contains retired models. This document does not certify production readiness.

**Always export and reopen the workbook before ending a session.** Workbook rows still live in ViewModel memory and can be lost on process restart. Changing providers preserves the workbook, but changing templates or importing another file replaces it.

## 2. Responsibilities and operating rules

- **Operator:** choose the correct provider, manage their own key, verify receipt values, remove sample rows, export, and check the saved file.
- **Maintainer:** maintain provider adapters/model options, verify security and compatibility, run tests, and update this SOP with implementation changes.
- **Release owner:** approve acceptance evidence, protect signing credentials, record release versions/artifacts, and manage rollback.

Use only receipt data approved for the selected provider. Each scan sends an image and target spreadsheet column names directly to that provider or custom server. Keep original receipts in approved storage; exported workbooks do not embed receipt images or provide an immutable audit trail.

## 3. Choose and configure an AI provider

### 3.1 What is implemented

| Provider option | Request implementation | Default in the reviewed app |
| --- | --- | --- |
| Google Gemini | `generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`; `x-goog-api-key`; inline JPEG; JSON response mode | `gemini-2.5-flash` |
| OpenAI | `api.openai.com/v1/chat/completions`; Bearer authorization; image data URL; JSON object response mode | `gpt-4o-mini` |
| Anthropic Claude | `api.anthropic.com/v1/messages`; `x-api-key` and `anthropic-version: 2023-06-01`; Base64 JPEG | `claude-3-5-haiku-20241022` |
| Custom OpenAI-Compatible | Normalized chat-completions URL; optional Bearer header at adapter level; text plus image data URL | User must enter a model |

These are code defaults, not assurances of current availability. A provider integration does not make every model compatible. Choose a currently available image-capable model that accepts the adapter's request format.

**Anthropic action required:** all three Anthropic dropdown entries in this commit are retired according to the [official retirement table](https://platform.claude.com/docs/en/about-claude/model-deprecations): Sonnet 3.5 retired 28 October 2025, Haiku 3.5 retired 19 February 2026, and Opus 3 retired 5 January 2026. Do not use the default as a working recommendation. Enter a current accessible image-capable model manually, validate, and test it; maintainers must refresh the dropdown.

Check [Gemini models](https://ai.google.dev/gemini-api/docs/models), [OpenAI models](https://developers.openai.com/api/docs/models), or the selected provider's documentation before release. No model/account combination was live-tested in this review.

### 3.2 First-time setup

1. Obtain your own API credential from the selected provider: [Google AI Studio](https://aistudio.google.com/app/apikey), [OpenAI Platform](https://platform.openai.com/api-keys), or [Anthropic Console](https://console.anthropic.com/settings/keys). For a custom server, obtain its documented credential and model from its administrator.
2. Review your API account's credit, quota, and billing settings. Consumer chat subscriptions and developer API billing are separate; do not assume a chat subscription supplies API credits.
3. Tap the gear icon to open **AI Provider & Key Settings**.
4. Select the provider. This changes the active provider immediately; **Done** closes the dialog without saving typed key/model changes and does not undo the provider selection.
5. Enter the key. It is masked by default, with a visibility toggle. A saved key is not displayed back in the field; **Stored Encrypted** indicates that one exists.
6. Select or type an image-capable model. For custom endpoints, complete section 4 first.
7. Tap **Validate Key** once and wait. Validation sends no receipt, but Anthropic and custom validation perform a small generation request that may incur usage.
8. Tap **Save Credentials** to persist the entered key and model/profile. Validation alone does not save them. Saving is not gated on successful validation.
9. Run one sanitized test receipt and compare every field. Only this checks the actual image extraction path for that configuration.

Provider selection resets to **Gemini** when a new ViewModel is created, including after process restart. Saved per-provider keys and model metadata remain available, but users must reselect another provider before scanning. Confirm the ready message on the Scanner tab each session.

### 3.3 What validation actually proves

| Option | Probe sent by this app | Interpretation |
| --- | --- | --- |
| Gemini | GET model list with `pageSize=1` | API accepted the key for a model-list request; it does not test the selected model or receipt images |
| OpenAI | GET `/v1/models` | API accepted the model-list request; code checks model presence with a string match, but the success banner does not expose that result |
| Anthropic | Text `ping` to the selected model with `max_tokens=1` | Small text request succeeded; may incur usage; image input was not tested |
| Custom | Text `ping` to normalized chat endpoint with `max_tokens=1` | Endpoint handled this text request; may incur usage; vision and JSON extraction remain unverified |

The internal vision-validation flag is inferred from a hardcoded model list for built-in providers, not an image probe. A green **Validation Successful** banner does not establish image support, available scan quota, or correct receipt extraction. Repeat validation after changing key, model, or endpoint; the old banner can remain visible after form edits.

### 3.4 Replace or delete a key

- To replace: select the provider, enter a new key, validate, and tap **Save Credentials**. Leaving the key field blank preserves the existing stored key when saving model/profile changes.
- To delete locally: select the provider, tap **Delete Key**, and confirm. Repeat for each provider you want to remove. This does not revoke the key at its provider.
- To revoke: use the provider's console. Review usage first if exposure is suspected. A scan already in progress may still hold its credential snapshot; local deletion is not a cancellation/revocation guarantee.
- If credential storage becomes unreadable, the app treats it as empty. Re-enter the necessary keys. Saving one key after a corrupt store may replace the unreadable credential set, so check every provider.

## 4. Custom endpoint procedure

Custom support targets a chat-completions service that accepts text and a Base64 JPEG `image_url` and returns JSON text inside `choices[0].message.content`. Compatibility with Ollama, vLLM, OpenRouter, LocalAI, or another service is not established merely by its name. Its route, model, image format, and response behavior must match this adapter.

1. Select **Custom OpenAI-Compatible**.
2. Use a dedicated key for this service. Do not paste a Gemini, OpenAI, or Anthropic key unless that exact service is its authorized destination.
3. Enter a full HTTPS endpoint, preferably ending in `/v1/chat/completions`. The code keeps that suffix, adds `/chat/completions` to `/v1`, or otherwise appends `/v1/chat/completions`. Unusual base paths can therefore be changed incorrectly.
4. Enter the server's image-capable model identifier.
5. Inspect **Target Host**, then tap its confirmation control. Confirmation checks hostname only, not scheme/port/full origin.
6. Validate, save, and perform a sanitized receipt scan. Check the destination server and provider usage.

**Current restrictions and workarounds:**

- Only one custom provider slot exists. Its saved key is associated with `custom_openai`, not an exact endpoint. A hostname change can be confirmed while retaining the previous key. Before changing destination, delete the old custom key, close and reopen Settings, then enter the destination-specific key and confirm the new endpoint. Do not change settings during an active request.
- Although the custom adapter allows an empty key, the ViewModel blocks every real scan with a blank key. Keyless local servers are therefore not supported end to end. Do not enter a dummy credential to bypass this limitation.
- The endpoint validator permits local HTTP for `localhost`, `127.0.0.1`, and `10.0.2.2`, without restricting that exception to debug builds. The manifest does not explicitly allow cleartext traffic; local HTTP may still be blocked by Android. Use HTTPS for this SOP; local HTTP is not a verified supported flow.
- An interceptor attempts redirect credential stripping, but automatic redirects remain enabled. Treat credential protection across redirects as unverified and avoid redirecting endpoints until this is fixed and tested.

Production follow-up: bind credentials and consent to exact origins, reject authenticated cross-origin redirects, validate URLs strictly, and support keyless endpoints deliberately if required.

## 5. Daily receipt and spreadsheet workflow

### 5.1 Prepare the workbook

1. On **Scanner & Setup**, choose Business Expenses, Travel & Per-Diem, Tax Deductions, or Contractor & Freelance; alternatively tap **Upload .xlsx/.xls**.
2. Business Expenses and Travel & Per-Diem contain seeded example rows. Delete them before real records are added.
3. Use a copy of any imported workbook. Check headers, row count, and several source rows before scanning. Use unique, nonempty column headers.
4. Export before choosing another template or importing another workbook; these actions replace current data without a save prompt.

The unchanged custom spreadsheet engine reads only XLSX `xl/worksheets/sheet1.xml`. Export reconstructs one sheet and does not preserve other sheets, formatting, formulas, charts, or workbook features. Sparse cells may shift values because coordinates are ignored; duplicate headers can overwrite mapped values. Date formatting and rich text shared strings are not fully interpreted. Legacy binary XLS is not implemented despite the upload label. SpreadsheetML XML and basic comma/tab/semicolon-delimited text are recognized, with limited quoting support. Prefer simple contiguous XLSX data and independently verify import/export.

### 5.2 Capture and review

1. Confirm the active provider and model. Settings changes do not erase workbook rows.
2. Place one receipt flat in good light with merchant, date, and total visible. Tap **Camera** or **Gallery**. Without a configured key, the app directs you to setup.
3. Wait for analysis. The image is sampled before decoding, rotated for common EXIF rotation values, bounded to 1280 pixels on its longest side, and sent as JPEG quality 85. Mirrored EXIF orientations are not specifically handled.
4. A successful response opens **Review Mapped Data**, showing provider/model. Failures should show an error without invented sample values.
5. Compare every mapped field with the original receipt. Correct amounts, dates, merchant, category, payment, and custom columns; leave unknowns blank. AI output is not independently verified by the app.
6. Check the editable final-amount field directly. The summary looks for the first column containing total/amount/cost; **Subtotal** can match before **Total Amount** and show the wrong number.
7. Tap **Add to Sheet** once and verify the appended row in the Spreadsheet tab. There is no duplicate receipt detection.

Do not change provider, model, endpoint, or workbook during analysis or review. Provider/configuration are captured for a scan, and a changed header list blocks append, but switching to another workbook with identical headers is not detected. Discard and rescan if you change the target workbook.

The parser leaves missing fields blank, but it accepts any parseable JSON object without enforcing receipt completeness or numeric/date types. Even `{}` may become an empty successful receipt. Reject unrelated, empty, or implausible results manually.

### 5.3 Demo mode

Use **DEMO SAMPLES (NO KEY REQUIRED)** for learning the workflow. Review identifies **Demo Mode** and allows a test append. Demo rows are ordinary workbook rows after append; export does not retain a separate demo classification. Keep a separate test workbook and remove all demo/sample rows before real use.

### 5.4 Save and hand off

1. Delete an incorrect row with **Delete Row**. There is no table edit or undo interface; rescan or correct an exported copy externally.
2. Tap **Download .xlsx**, select an approved destination, and save a dated copy.
3. Open that file in an external spreadsheet reader. Verify headers, row count, last row, amounts, and absence of repair warnings. The export code can report success even if the document provider returns no output stream.
4. Use the share icon for an approved receiving app. Verify delivery there; the app's share status only establishes that it opened the chooser.
5. Retain original receipts under your organization's policy. At the next session, reimport the last verified export.

Do not rely on the displayed grand total: it sums every total/amount/cost-like column and can count Subtotal plus Total Amount. Dollar symbols are not currency conversion. Verify the final-amount column and currency externally.

## 6. Development setup and running

The app remains Kotlin/Compose with OkHttp, minimum API 24, compile SDK 36 minor API 1, target SDK 36, application ID `com.aistudio.receiptscanner.xkvjtw`, namespace `com.example`, version code 1/version name 1.0.

Install Git, compatible Android Studio, Android SDK Platform 36.1, Build Tools 36.0.0, platform tools, and a device/emulator. Pins remain AGP 9.1.1, Gradle 9.3.1, Kotlin Compose 2.2.10, and KSP 2.3.5. Use JDK 17 for Gradle; Java source/target 11 in the build does not change this runtime requirement. See the [official AGP compatibility table](https://developer.android.com/build/releases/agp-9-1-0-release-notes).

```powershell
git clone https://github.com/whitequill-software/receipt-scanner-mobile.git
Set-Location receipt-scanner-mobile
git rev-parse HEAD
```

Open the repository root in Android Studio and configure the SDK location. The repository now includes the official Gradle 9.3.1 wrapper; verify it with:

```powershell
.\gradlew.bat --version
```

The wrapper was generated in a separate minimal project using official Gradle tooling and verified against Gradle's published checksum. See [Android build verification](ANDROID_BUILD.md) for provenance, CI behavior, and current results.

Debug signing expects root `debug.keystore`, which is absent and ignored. Restore the original for updates, or generate a new development-only key for a fresh install:

```powershell
keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

Never use that key for production. A different signing key cannot update an existing installation signed by the original key.

No shared AI key is required in `.env`. Configure user credentials through the running app. The Secrets Gradle plugin still reads `.env`; ensure any old local `.env` containing GEMINI_API_KEY is removed or sanitized before building, because removing the code reference alone does not establish that the plugin cannot generate/package that stale value. Firebase libraries remain declared, but receipt calls use direct provider REST APIs; `google-services.json` is absent and configured to warn.

After prerequisites are ready:

```powershell
.\gradlew.bat :app:assembleDebug
adb devices
.\gradlew.bat :app:installDebug
adb shell am start -n com.aistudio.receiptscanner.xkvjtw/com.example.MainActivity
```

Expected debug APK: `app/build/outputs/apk/debug/app-debug.apk`. On macOS/Linux use `./gradlew`. Run through Android Studio if preferred. Verify demo mode first, then configure an evaluation key and test a sanitized receipt.

## 7. Testing and acceptance

### Automated checks

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The device test requires a connected emulator/device. Retain exit codes and reports under `app/build/reports/tests/`, `app/build/reports/androidTests/connected/`, and the lint report location printed by Gradle.

The updated Robolectric tests use SDK 34 and cover encrypted credential lifecycle/provider separation, corrupt-file recovery, profile metadata, workbook preservation on settings change, header drift, summary edits, and demo outcomes. Other tests cover simple XLSX round trips, column letters, basic receipt parsing, selected sanitized errors, endpoint security checks, and URL normalization. The instrumented test checks the app package.

These tests do not establish real Android Keystore persistence: the credential class contains an in-memory JVM AES fallback when AndroidKeyStore is unavailable. There are no provider network mock/redirect/cancellation tests in the inspected test files. Run device tests for Keystore and backup behavior before release.

### Manual acceptance record

Record commit, APK version/hash, device/OS, provider/model, sanitized fixture, expected/actual outcome, and pass/fail. Never record keys.

- Save two different provider keys, restart, reselect the intended provider, validate and scan. Confirm profile/key persistence and workbook behavior separately.
- Replace/delete one key without affecting another; simulate invalidation/corruption and verify recovery guidance. Verify credentials are absent from backups/transfers.
- Test no key, invalid key, retired/inaccessible model, rate limit, timeout, malformed/empty output, and a refused request. Confirm no fake receipt is appended.
- Compare validation results with a real sanitized image scan for each provider/model being advertised.
- Verify custom exact-origin isolation, redirects, keyless behavior, and HTTPS enforcement after relevant fixes. Current implementation has known gaps; record failures rather than accepting them.
- Test camera/gallery, cancellation, permission denial, and image rotation on API 24 and a current Android device. No runtime CAMERA permission request appears in the reviewed capture component.
- Edit merchant/date/total and subtotal; verify review summary and appended fields. Change headers and confirm append is blocked; also test replacement with a workbook using identical headers.
- Append a demo only in a test workbook; verify that removing all sample rows is part of handoff.
- Import/export simple and sparse XLSX, duplicate headers, date/formula/multi-sheet fixtures, and currency-prefixed amounts. Independently open output and reconcile totals.
- Export to approved local/cloud document providers, cancel export/share, and verify actual destination files/delivery.
- Inspect a release APK to ensure no old shared key is present. Do not print extracted values in reports.

**This SOP's verification status:** complete source/configuration/test-tree inspection and document evidence checks only. `java`, `gradle`, `adb`, and `keytool` were unavailable on the review shell's PATH. Builds, automated tests, device behavior, live provider calls, and APK credential inspection were not run. Commands here are procedures, not reported passing checks.

## 8. Deployment and recovery

No `.github/workflows` files are present. Use a manual release process until a reviewed pipeline exists.

1. Select a reviewed commit and resolve the release gates in section 10, including endpoint isolation/redirect handling, model options, credential fallback, and acceptance failures.
2. Export users' work before upgrade. Increase `versionCode` above the last distributed version and set `versionName`; retain the application ID.
3. Provision the correct release/upload keystore outside the repository. Existing configuration reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`; alias is `upload`. Root `my-upload-key.jks` is the fallback path but is absent. Keep signing secrets out of source and reports.
4. Sanitize old build environment files, then build:

   ```powershell
   .\gradlew.bat :app:assembleRelease
   .\gradlew.bat :app:bundleRelease
   ```

5. Verify signing with SDK `apksigner`, inspect the release artifact for stale shared credentials, and run acceptance tests on that release build. Expected artifacts: `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/bundle/release/app-release.aab`; confirm actual paths from build output.
6. Distribute APKs through an approved direct channel or an AAB through a configured store/internal-testing process. No store listing or release approval was verified here.
7. Record source commit, version, artifact SHA-256, signing certificate fingerprint, checks, known limitations, and distribution location. Keep passwords/private keys out of the record.

For a faulty release, stop distribution, preserve verified exports, fix/revert the source defect, and issue a tested higher-version build with the required signing identity. Avoid uninstalling as a first recovery step: it can remove local credentials/cache and require re-entry. Reimport the verified workbook and check for duplicates.

## 9. Security and migration

Credentials are AES-256-GCM encrypted into app-private `byok_credentials.enc`, with a key normally generated in AndroidKeyStore. Hardware-backed storage is device dependent and is not enforced or verified by this code. If the Keystore cannot initialize, the same production class falls back to an in-memory JVM AES key; this is not restricted to test builds. Maintainers must make production storage fail closed and separate the test implementation.

Provider model/endpoint metadata is saved separately in plaintext `byok_profiles.json`; it should contain no keys. Backup XML excludes `byok_credentials.enc` and `byok_prefs.xml`, but the actual metadata JSON is not excluded. Do not put credentials or secrets in endpoint URLs; its URL/profile can be backed up or displayed.

Keys are decrypted when needed, but loading a single provider key decrypts the entire stored key map. Plaintext strings exist temporarily in the settings/request flow. Do not claim that keys exist only during requests, that local deletion guarantees forensic erasure, or that a rooted/compromised device cannot access them. The UI does not set screenshot protection for the key dialog.

Before migration from a shared-key version:

1. Export/reopen existing workbooks.
2. Remove old shared-key values from build environments and inspect new release artifacts.
3. Have each user configure their own provider/key and perform a sanitized scan.
4. The previous shared key may still exist in old distributed APKs. Review its provider usage and rotate/revoke it at the provider; a new BYOK build does not revoke it.

Protect devices with screen lock and approved storage. Avoid screenshots of revealed keys; clear copied credentials from clipboard tools where applicable. Keep raw receipt/API details out of public issues. Error sanitization is partial: security-error branches may return raw text and the ViewModel's outer catch interpolates exception messages. Maintainers must close these gaps before claiming comprehensive redaction.

Signing assets matching `*.keystore`, `*.jks`, and `*.p12` are ignored. Keep release keys external. CI generates a disposable debug key and never requires release credentials.

## 10. Remaining findings and release gates

These findings describe the reviewed source, not fixes made by this SOP.

| Priority | Finding | Required follow-up |
| --- | --- | --- |
| High | Custom key bound to one provider slot; consent bound only to hostname | Bind credentials and confirmation to exact origin; require new credentials on destination change |
| High | Automatic redirects remain enabled; application interceptor acts after OkHttp redirect processing | Reject authenticated cross-origin redirects and test all authentication headers; avoid redirecting endpoints meanwhile |
| High | Production credential store permits an ephemeral fallback when Keystore is unavailable | Fail closed on Android; inject a test-only credential store; verify on-device restart/invalidation |
| High | Workbook remains memory-only; import/template changes replace it | Add durable save/recovery and replacement prompts; export/reopen operationally until fixed |
| High | Sparse XLSX cells can shift values; exports reconstruct one sheet | Validate/reject unsupported imports or fix parser; retain source copies and reconcile |
| Medium | All current Anthropic dropdown models are retired | Refresh model choices and test adapter compatibility; allow current documented model identifiers |
| Medium | Validation flags/banner overstate selected-model/vision checks | Report exactly which probe succeeded; invalidate results when inputs change |
| Medium | Custom keyless server blocked by ViewModel; local HTTP exception not debug-only | Align settings, gate, networking, and documented support |
| Medium | Summary picks Subtotal as a total; grand total double-counts amount-like columns | Select an explicit final-amount column for summary and aggregation |
| Medium | `{}` can parse as a successful empty receipt; response completeness/types/finish status not enforced | Validate schema and meaningful extracted fields before Success |
| Medium | Provider calls use blocking execute, broad exception catches, default connection retry and no call timeout | Implement/test coroutine cancellation and deliberate retry policy; avoid rapid manual retries |
| Medium | Null export stream still reports success; share delivery not verified | Require successful output/write/close and precise status |
| Medium | Device test coverage minimal | Add provider-network/Keystore/device regression tests; wrapper restoration and CI are described in [Android build verification](ANDROID_BUILD.md) |
| Medium | Credential metadata backup and error redaction incomplete | Review actual files and all error paths; keep secrets out of URLs/messages |

Unchanged spreadsheet issues also include numeric export testing values after stripping `$` but writing the original text into numeric XML, unbounded ZIP/XML import reads, and no explicit XML external-entity hardening. Use trusted small files and plain decimal amounts until corrected. Changing release shrinking from its current disabled state requires regression tests and does not secure credentials by itself.

## 11. Troubleshooting and maintenance

| Symptom | Action |
| --- | --- |
| No key configured after restart | Select the intended provider; restart defaults to Gemini. If its stored key is missing, re-enter and save it |
| Validation succeeded but scan fails | Test selected model access and image support; validation did not upload an image or establish available generation quota |
| Anthropic default gives model error | The default is retired; enter a current accessible image-capable model and validate/test it |
| Authentication error | Check correct provider, entered key, permissions and provider-console status; replace and save if needed |
| Quota/rate-limit error | Check provider usage/credits/limits; wait where appropriate; avoid repeated scans that may be billed |
| Custom endpoint error | Verify full URL/path, HTTPS, model, hostname confirmation, and dedicated key; avoid redirects |
| Local keyless server cannot scan | Not supported through the current ViewModel gate; requires a code fix |
| Saved keys vanish or cannot decrypt | Re-enter keys and check all provider profiles; maintainer investigates Keystore initialization/invalidation |
| Headers changed warning | Discard the result, choose the intended workbook, then scan again |
| Blank or irrelevant receipt despite Success | Discard; parser does not enforce meaningful receipt content; try a clearer image and report sanitized evidence |
| Workbook missing after closing | Reimport last verified export; unsaved in-memory rows have no recovery implementation |
| Total looks wrong | Inspect final-amount fields externally; Subtotal matching and multiple amount columns can distort summaries |
| Export banner but no valid file | Check actual destination, retry to approved local storage, and reopen the file |
| Build fails on fresh clone | Restore wrapper, correct JDK/SDK, and debug keystore; do not add a shared provider key to solve setup |

Each session, confirm provider/model, remove demo rows, and export/reopen. Each release, review model retirements, credential isolation, billing disclosures, signing setup, and device acceptance evidence. Periodically review dependency security updates, provider usage, and encrypted signing backups. Remove cached receipt/share files only after verified export and required retention.

Bug reports should include commit/app version, device/OS, provider/model, sanitized fixture, steps, and expected/actual behavior. Do not include keys, full authenticated requests, or private receipts. Report limitations honestly rather than treating a green validation banner as proof of a working release.

## 12. Evidence map

All implementation links below are pinned to the reviewed commit.

- [Provider options and defaults](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/model/ProviderModels.kt)
- [Settings interface](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/ui/components/SettingsDialog.kt)
- [Workbook and provider lifecycle](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/ui/ReceiptScannerViewModel.kt)
- [Credential encryption and profile storage](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/crypto/KeystoreCredentialStore.kt)
- [Gemini adapter](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/provider/GeminiProvider.kt), [OpenAI adapter](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/provider/OpenAiProvider.kt), [Anthropic adapter](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/provider/AnthropicProvider.kt), and [custom adapter](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/provider/CustomOpenAiProvider.kt)
- [HTTP client and redirects](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/net/SecureHttpClient.kt), [receipt parser](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/util/ReceiptJsonParser.kt), and [error sanitizer](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/byok/util/SanitizedErrorUtil.kt)
- [Spreadsheet engine](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/spreadsheet/XlsxEngine.kt) and [review dialog](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/java/com/example/ui/components/MappedReviewDialog.kt)
- [Build/signing configuration](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/build.gradle.kts), [manifest](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/AndroidManifest.xml), [backup rules](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/res/xml/backup_rules.xml), and [transfer rules](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/main/res/xml/data_extraction_rules.xml)
- [Unit tests](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/test/java/com/example/ExampleUnitTest.kt) and [Robolectric tests](https://github.com/whitequill-software/receipt-scanner-mobile/blob/ad9d9c5fdfcfa8f2d505a2e123d70a3bee660500/app/src/test/java/com/example/ExampleRobolectricTest.kt)

Update this document's commit, date, behavior, acceptance results, and findings when code changes. Do not copy security claims forward without checking the implementation.
