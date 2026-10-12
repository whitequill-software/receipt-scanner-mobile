# Receipt Scanner Mobile Standard Operating Procedure

Repository: [whitequill-software/receipt-scanner-mobile](https://github.com/whitequill-software/receipt-scanner-mobile)  
Reviewed source: [`008bd3778d73e521ad0e7df20134492a5943dcd9`](https://github.com/whitequill-software/receipt-scanner-mobile/tree/008bd3778d73e521ad0e7df20134492a5943dcd9) on `main`  
Review date: 12 October 2026, Asia/Manila  
Intended repository location: `docs/SOP.md`

## 1. Purpose and operating limits

Use this procedure to set up, run, test, distribute, and maintain the Android Receipt Scanner app, and to capture receipts into a spreadsheet safely. It is based on the committed source and configuration. Commands below are procedures to execute on a prepared workstation; they were not executed during this review.

**Current release assessment: use for controlled evaluation until the data integrity and security gaps in section 11 are addressed.** Missing or placeholder API keys, network/API failures, and invalid responses can produce invented receipt values. The review dialog does not expose the error or clearly identify these values as simulated. Always compare every field with the original receipt before adding it to a real workbook.

The app keeps workbook changes in memory. Export and independently reopen the file after every working session and before switching templates, importing another workbook, closing the app, or installing an update. Android backup settings do not save this in-memory workbook.

### What the repository implements

| Area | Actual implementation |
| --- | --- |
| Platform | Native Android, Kotlin, Jetpack Compose; Android 7.0/API 24 minimum |
| App identity | `com.aistudio.receiptscanner.xkvjtw`; namespace `com.example`; version code `1`, version name `1.0` |
| Receipt input | External camera through `TakePicture`, Android visual media picker, or fixed demo samples |
| AI | Direct OkHttp POST to Google Gemini `gemini-3.5-flash:generateContent`; key read from `BuildConfig.GEMINI_API_KEY` |
| Image transfer | Image decoded, resized to a maximum dimension of 1024 pixels, JPEG quality 85, Base64 embedded in the request |
| Spreadsheet handling | Custom ZIP/XML engine; XLSX, SpreadsheetML XML, and basic delimited text import; newly generated single-sheet XLSX export |
| Storage | ViewModel/StateFlow memory; camera images in `cache/receipt_images`, share files in `cache/exports`; explicit export through Android's file chooser |
| Backend and accounts | No application backend, sign-in flow, database persistence, or cloud workbook synchronization implemented in the reviewed source |
| Automation | No `.github/workflows` files; no GitHub Releases returned during review |

The separate `whitequill-software/receipt-scanner` repository contains Google Apps Script versions. This SOP covers the Android repository; do not apply the Apps Script deployment steps to this app.

## 2. Responsibilities

- **Operator:** obtain an approved receipt image, verify every mapped field, remove demo rows, export, and confirm the saved workbook.
- **Maintainer:** prepare the build environment, configure evaluation credentials, fix defects, run checks, and update this SOP when behavior changes.
- **Release owner:** control signing credentials, approve the tested artifact, record its version and source commit, and retain recovery copies.

## 3. Prepare the development workstation

### 3.1 Required tools and versions

Install Git, an Android Studio version supporting AGP 9.1.1, Android SDK Platform 36.1, SDK Build Tools 36.0.0, platform tools, and an emulator or physical Android device. Set the Gradle JDK to JDK 17. Java source/target compatibility is 11 in the project; this does not replace the Gradle runtime JDK requirement.

Repository pins: Android Gradle Plugin `9.1.1`, Gradle `9.3.1`, Kotlin Compose plugin `2.2.10`, KSP `2.3.5`, compile SDK `36` minor API `1`, target SDK `36`. Keep these pins together for the initial build. [Google's AGP compatibility table](https://developer.android.com/build/releases/agp-9-1-0-release-notes) confirms Gradle 9.3.1 and JDK 17 for AGP 9.1.1.

### 3.2 Obtain the project

Run in PowerShell in your chosen development folder:

```powershell
git clone https://github.com/whitequill-software/receipt-scanner-mobile.git
Set-Location receipt-scanner-mobile
git rev-parse HEAD
```

Record the source commit. Open the repository root in Android Studio. Set the SDK location through the IDE; the resulting `local.properties` is ignored by Git.

### 3.3 Restore missing build prerequisites

The reviewed tree has `gradle/wrapper/gradle-wrapper.properties` but lacks `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`. Wrapper commands will fail until these are restored. Obtain the original trusted wrapper files, or install Gradle 9.3.1 from its official distribution and generate them from the root:

```powershell
gradle --version
gradle wrapper --gradle-version 9.3.1 --distribution-type bin
```

If project configuration prevents generation, create the wrapper in a separate minimal Gradle project and copy only the generated wrapper files into this project. Review and commit the scripts, wrapper JAR, and properties as a separate maintenance change. Do not download a wrapper JAR from an arbitrary repository. These repairs are instructions, not changes included with this SOP.

The debug build explicitly expects `debug.keystore` at the repository root. It is ignored and absent in the reviewed tree. Restore the original debug keystore when an installed evaluation app must accept an update. For a new local installation only, create a development key with the alias/passwords expected by `app/build.gradle.kts`:

```powershell
keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

Do not use this debug key for a production release. A newly generated key will not update an app signed with a different key.

### 3.4 Configure Gemini for evaluation

1. Create or obtain a project-approved Gemini API key through Google AI Studio. Confirm that the project can access the configured model and has appropriate quota. No live API call was made in this review.
2. Copy `.env.example` to `.env` in the repository root:

   ```powershell
   Copy-Item .env.example .env
   ```

3. Edit `.env` locally and replace `MY_GEMINI_API_KEY` with the evaluation key. Keep the Java-properties form `GEMINI_API_KEY=value`. Do not paste keys into tickets, screenshots, source, or this SOP.
4. Sync and rebuild the app. The Secrets Gradle plugin generates the value read through `BuildConfig`; editing `.env` does not update an already installed APK.
5. Use a sanitized test receipt and compare the output with the original. Demo samples do not prove the API works.

The default placeholder permits demo behavior without a key. Despite Firebase libraries and the Google Services plugin being declared, the receipt scanning implementation uses the Gemini REST endpoint directly. No Firebase initialization or App Check protection of this request is implemented. `google-services.json` is absent and the build is configured to warn about its absence; do not invent a Firebase setup requirement for this scanning path.

The model name is hardcoded in `GeminiVisionService.kt`. Check [the official model page](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash) and project access before each release. A model change requires a source change, rebuild, and regression test.

## 4. Build and run

After completing section 3, sync Gradle and select the `app` module and `debug` variant in Android Studio. Start an API 24+ device/emulator and run the app. Prefer a physical device for camera testing and an API 36 emulator for the current test configuration.

PowerShell alternatives from the repository root:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
adb devices
.\gradlew.bat :app:installDebug
adb shell am start -n com.aistudio.receiptscanner.xkvjtw/com.example.MainActivity
```

Expected APK: `app/build/outputs/apk/debug/app-debug.apk`. Confirm the app opens with **Scanner & Setup** and **Spreadsheet** tabs. The initial Business Expenses workbook contains a sample row; remove it before real use.

For macOS/Linux, use `./gradlew` after making the script executable. Android SDK/JDK and signing requirements remain the same.

## 5. Daily receipt processing

### 5.1 Choose the workbook

1. Export any current work before changing the target workbook.
2. On **Scanner & Setup**, choose a starter template or tap **Upload .xlsx/.xls**. The picker accepts any file type, so select a supported spreadsheet deliberately.
3. Available templates: **Business Expenses**, **Travel & Per-Diem**, **Tax Deductions**, and **Contractor & Freelance**. The first two include example rows; delete them for real records.
4. For an imported file, compare the headers, row count, and several rows against the original before scanning. Use unique, nonempty headers such as Date, Merchant, Category, Tax, and Total Amount.

Import limitations matter: only the hardcoded XLSX `xl/worksheets/sheet1.xml` is read. Other sheets, formatting, formulas, charts, and workbook features are not preserved on export. Sparse cells can shift values because cell coordinates are ignored. Duplicate headers can overwrite values in the row map. Excel date formatting is not interpreted. Rich text shared strings and complex CSV quoting are not fully handled. Legacy binary `.xls` is not implemented; convert it to a simple XLSX first. Use a copy of the source workbook, never its only copy.

### 5.2 Capture and review

1. Keep one receipt flat, fully visible, and well lit. Include the merchant, date, line items, and final total. Use only receipt data approved for transfer to Google.
2. Tap **Camera** or **Gallery**. Camera capture uses an external camera application; if launching it fails, the code attempts to open the gallery. Gallery uses Android's visual media picker.
3. Wait for **Review Mapped Data**. Request connect/read/write timeouts are each 60 seconds; there is no explicit end-to-end call timeout or user-facing retry control.
4. Compare each editable column with the original receipt. Correct date, merchant, amounts, category, and payment details. Leave unknown values blank rather than guessing. Check custom columns manually.
5. Treat sample-looking values, unrelated merchants, today's date substituted for an unknown date, or unexpected defaults as unverified. An API failure may still produce the normal review dialog. Discard the scan or manually replace every field using the receipt.
6. Tap **Add to Sheet** once. The app appends a row and switches to the Spreadsheet tab. It does not detect duplicate receipts.
7. Check the appended row. Editable mapped fields are authoritative: the merchant/total summary card and success message may still show the original extracted values after edits.

The fallback matcher guesses categories and can supply `Yes` for reimbursable/deductible, `Local` for location, and a random receipt reference. These are not verified business facts. The UI's `$` symbols do not establish the receipt currency; mixed currencies and exchange conversion are not implemented.

### 5.3 Correct, save, and finish

1. Use the row's **Delete Row** action to remove an incorrect row. There is no table edit interface or undo flow; rescan and correct the review fields, or correct the exported workbook externally.
2. Tap **Download .xlsx** and choose a destination and filename. Save a dated copy to an approved folder.
3. Open the saved file in Excel or another approved spreadsheet reader. Verify headers, row count, latest row, key amounts, and that the file opens without repair warnings. An app success banner alone is insufficient: the export code can report success even when the destination provider returns a null output stream.
4. To hand off a copy, tap the share icon and select an approved destination. Opening the Android share chooser does not prove delivery; check the destination yourself. Sharing generates a cached XLSX copy.
5. Retain the original receipt according to your organization's policy. Exported workbooks contain mapped values, not embedded receipt images or a durable image audit trail.

Reimport the last verified export to continue after an app restart. Do not switch templates or import a different workbook until current work is safely exported; these actions replace the current workbook without a save prompt.

## 6. Verification before release

### 6.1 Automated checks

Run after the environment and wrapper are prepared:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

The second command requires a connected device/emulator. Preserve task exit codes and reports, normally under `app/build/reports/tests/`, `app/build/reports/androidTests/connected/`, and `app/build/reports/lint-results-debug.html`.

Existing tests cover column letters through AA, one simple XLSX generate/parse round trip, two fallback column matches, the app name under Robolectric SDK 36, and the application package on device. They do not validate live Gemini behavior, error labeling, camera permissions, persistence, complex spreadsheet import, or export delivery. Passing these tests alone does not establish release readiness.

### 6.2 Manual acceptance checklist

Use sanitized fixtures and a separate test workbook. Record device/OS, app version, commit, expected outcome, actual outcome, and pass/fail.

- Launch, navigate both tabs, and open/close Help.
- Load every template and identify/remove seeded rows.
- Exercise a quick sample, edit a mapped field, add once, verify the table value, and delete the row.
- Capture with camera and select through Gallery; test cancel and denied camera access. The manifest declares CAMERA, but no runtime permission request is implemented in the reviewed capture UI; document behavior on each supported device.
- Scan a real test receipt with a valid key and compare every field. Separately test placeholder key, invalid key, airplane mode, and malformed/failed API response. Confirm operators cannot mistake simulation for successful extraction; the reviewed code has a known failure here.
- Import/export a simple contiguous XLSX; compare all cells. Also test sparse cells, duplicate headers, formulas, dates, multiple sheets, CSV quoting, and binary XLS to document unsupported cases and prevent silent corruption.
- Download to local storage and an approved document provider; cancel the picker; verify actual output bytes and reopening. Confirm shared output reaches the intended recipient.
- Export, force-stop/restart, and reimport. Verify restoration from the export; do not expect unsaved state to survive process death.
- Compare the displayed total with independently calculated totals. The implementation sums every column whose name contains total, amount, or cost, so Subtotal and Total Amount can both be counted.

**Review validation status:** source, configuration, test files, full repository tree, branch metadata, and releases were inspected. No build, automated tests, installation, live receipt scan, or signed release was executed. `java`, `gradle`, `adb`, and `keytool` were not available on the review shell's PATH. No release readiness certification is implied.

## 7. Deployment and updates

There is no committed automated build/deployment pipeline. The following is a manual release procedure to use after the acceptance and security gates pass.

1. Choose a reviewed commit and complete section 6. Resolve the production blockers in section 11, especially simulated success, exposed credentials, and loss of unsaved data.
2. Increase `versionCode` above the last distributed build and set `versionName` in `app/build.gradle.kts`. Keep the application ID stable for updates.
3. Obtain the correct release/upload keystore and store it outside the repository. The release configuration reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`; alias is fixed to `upload`. The fallback keystore path is root `my-upload-key.jks`, which is absent. Prefer an explicit external path.
4. Provide signing variables through a secure local session or secret manager. Do not commit keys or passwords. If using Android Studio's signed bundle wizard, ensure the existing Gradle signing configuration receives the required environment variables; restart the IDE from that configured environment if needed.
5. Build the chosen distribution artifact:

   ```powershell
   .\gradlew.bat :app:assembleRelease
   .\gradlew.bat :app:bundleRelease
   ```

6. Verify APK signing with the installed Android SDK `apksigner` tool. Expected outputs are `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/bundle/release/app-release.aab`. Verify actual paths in the build output. APK is for approved direct installation; AAB is for a configured app-store distribution process.
7. Install the release APK on a test device, or use the store's internal testing channel. Repeat a real scan and export/reopen test with release configuration. Debug success is insufficient.
8. Before upgrading a user's app, export and verify its workbook. For direct APK updates, retain the same signing identity. Record source commit, version, checks, artifact SHA-256, signing certificate fingerprint, release notes, and distribution location. Keep passwords and private keys out of this record.
9. Distribute through the release owner's approved channel. Store account setup, listing, privacy disclosures, and store approval are outside the committed repository and must be completed for public distribution. Do not infer an existing Play listing from this source.

Release shrinking is currently disabled (`isMinifyEnabled = false`). Changing that requires regression testing; obfuscation would not make an embedded API key secret.

### Recovery from a bad release

Stop distributing the affected artifact. Preserve exported workbooks and the failing version/commit details. Revert the defect in source, build a corrected artifact with a higher version code and the same required signing identity, rerun acceptance checks, and distribute the fix. Avoid uninstalling as a first recovery step: app-local data/cache can be lost, and a lower-version APK may not install as an update. Reimport a verified workbook after recovery and reconcile duplicate rows.

## 8. Maintenance routine

- **Each session:** verify workbook destination and baseline rows; export/reopen before exit; retain approved receipt originals.
- **Each release:** review this SOP against code changes; verify model access, error behavior, signing configuration, dependency compatibility, import/export fixtures, and privacy disclosures.
- **Monthly or after an incident:** review Gemini quota/usage and unusual charges in the relevant Google project, rotate exposed evaluation keys, review dependency/security updates, and verify access to encrypted signing-key backups. Do not upgrade all pinned dependencies blindly.
- **Periodically:** remove stale shared copies and receipt caches only after verified exports and required evidence retention. Cache is temporary and the app has no dedicated retention/cleanup UI.

Recommended engineering follow-ups are explicit scan status and error handling, durable workbook storage, save prompts before replacement, import validation, accurate single-amount totals, input limits, and meaningful regression tests. Introduce CI only after restoring a complete wrapper; run build, unit tests, and lint on documentation-appropriate paths and code changes, with a separate protected signing/release process.

## 9. Troubleshooting

| Symptom | Likely repository-specific cause | Action |
| --- | --- | --- |
| `gradlew.bat` or wrapper JAR missing | Wrapper scripts/JAR absent from reviewed tree | Complete section 3.3; use trusted Gradle 9.3.1 tooling |
| Gradle cannot start or SDK unavailable | Wrong Gradle JDK or missing SDK 36.1 | Select JDK 17 for Gradle, install matching SDK/platform tools, sync again |
| Debug signing fails | Root `debug.keystore` missing | Restore original key or generate a new local evaluation key as appropriate |
| Update rejected due to signature | Installed app uses another signing key | Retrieve the correct key; export before considering reinstall |
| Release signing fails | Missing keystore/passwords or alias mismatch | Verify external path, secure variables, and alias `upload`; never print passwords |
| Google Services warning | `google-services.json` absent | Direct REST scanning does not use Firebase initialization; investigate only if Firebase features are introduced |
| Unrelated merchant or plausible fake amounts | Placeholder key, network/API failure, response parsing fallback | Discard or replace every field from the original receipt; maintainer checks credentials, model access, HTTP status and quota |
| Camera opens Gallery or no scan follows | Camera launch failed, permission denied, cancellation, or no camera app | Use Gallery; check app permissions/device camera support; maintainer implements/tests runtime permission handling |
| Import shows wrong columns/empty rows | Unsupported binary XLS, malformed input, sparse cells, duplicate headers or fallback parser | Reopen original externally, convert a copy to simple XLSX with contiguous cells and unique headers, verify every imported row |
| Excel reports repair or bad values | Limited custom exporter, unsupported input or currency-formatted numeric text | Preserve original; export plain decimal amounts without currency prefixes; inspect output in an external reader before use |
| Total too high | Summary includes Subtotal and multiple amount-like columns | Calculate using the intended final-amount column externally; fix summary logic before relying on it |
| Changes disappear | Process death/restart or workbook replaced | Reimport last verified export; unsaved in-memory changes have no implemented recovery |
| Export says success but file missing | Null provider stream, canceled/wrong destination, provider problem | Inspect destination and open file; retry to an approved local folder and verify bytes |
| Share says done but recipient has nothing | App opened chooser; completion not verified | Check receiving app/destination; retry using a verified saved export |

When reporting a defect, include source commit, app version, device/OS, action sequence, sanitized fixture, and expected/actual result. Capture only relevant sanitized Logcat/error details. Raw API errors are stored in `rawAiResponse` but are not surfaced by the review dialog; do not ask operators to recover them from that screen.

## 10. Security and data handling

1. **Credential boundary:** `.env` is ignored by Git but its Gemini value is compiled into the app and used in the request URL. It can be extracted from a distributed APK. Use only restricted evaluation credentials for the current implementation; move production access behind an authenticated service or another reviewed client-safe architecture before public distribution. Google's [API key guidance](https://ai.google.dev/gemini-api/docs/api-key) warns against exposing keys in production client applications.
2. **Signing protection:** keep release keystores outside the repository, encrypted and access controlled, with recovery backups. The current `.gitignore` excludes `debug.keystore` but does not broadly exclude release `*.jks`, `*.keystore`, or `my-upload-key.jks`; add suitable exclusions in a separate reviewed change before introducing signing files.
3. **Receipt disclosure:** the resized receipt image and column names go to Google's Gemini API over HTTPS. No in-app consent or configurable retention policy is implemented. Confirm organizational approval for the data and review applicable provider terms before real use. No legal compliance conclusion is made here.
4. **Local and shared data:** camera receipts and generated share files remain in app cache until removed by app/system actions; there is no explicit cleanup routine. Exports can outlive the app. Use approved storage and recipients, device lock/encryption, and organization-defined retention.
5. **Backup review:** `allowBackup=true` and backup/data extraction XML contains template rules. Review backup scope before adding persistent sensitive data. These rules do not implement an audit trail or workbook recovery.
6. **Untrusted files:** import reads entire files/ZIP entries into memory; no explicit size limits, decompression quotas, or XML external-entity hardening is configured in the parser. Accept only trusted, reasonably sized workbooks until these controls are implemented. Large images are decoded before resizing and can also cause memory pressure.
7. **Integrity controls:** do not rely on AI guesses for payment, currency, reference numbers, reimbursement, or financial classifications. Preserve originals and manually verify. No duplicate detection, immutable audit log, or receipt-to-export attachment is implemented.
8. **Incident response:** revoke a suspected exposed key, review project usage, issue a corrected build, and remove affected shared files using approved procedures. Signing-key compromise requires the release owner's distribution-specific recovery process. Avoid posting receipt contents, credentials, or full request URLs in public issues.

## 11. Findings and recommended priorities

These are source-review findings; fixes are not included in this documentation change.

| Priority | Finding and operational effect | Evidence / recommended fix |
| --- | --- | --- |
| High | Failures can yield fabricated receipt data with normal success UI. Missing-key simulation sets `isAiPowered=true` when no error string is supplied; review never uses that flag or `rawAiResponse`. | `GeminiVisionService.createSimulatedResult`, `parseGeminiJsonResponse`, `MappedReviewDialog`, `processReceiptImage`; separate demo, success and error states and prevent accidental commit |
| High | Workbook has no durable persistence; changing template/import replaces it. | `ReceiptScannerViewModel`; add persistence and explicit save/replace handling |
| High | Gemini credential is embedded in APK; declared Firebase/App Check dependencies do not protect the direct REST call. | `app/build.gradle.kts`, `GeminiVisionService`; implement production credential boundary |
| High | XLSX sparse cell coordinates are ignored, duplicate headers overwrite map entries, and only `sheet1.xml` is imported; export reconstructs a single sheet. | `XlsxEngine.parseWorksheetXml`, `parseXlsxFromBytes`, `generateXlsxBytes`; validate/reject unsupported structures or use a robust engine |
| Medium | Summary can double-count Subtotal and Total Amount. | `SpreadsheetTableView` matches all total/amount/cost headers; select one explicit final-amount field |
| Medium | Export may report success with no output stream; share completion is not confirmed. | `exportToSafUri`, `shareSpreadsheet`; require successful write/close and precise status |
| Medium | Export tests numeric eligibility after stripping `$` but writes the original trimmed text into numeric XML, allowing invalid numeric values such as `$12.50`. | `XlsxEngine.generateXlsxBytes`; serialize normalized numeric values and test interoperability |
| Medium | Camera permission is declared without runtime request in capture UI. | `AndroidManifest.xml`, `ReceiptCaptureSection`; implement/test permission flow or review whether permission is needed for chosen intent architecture |
| Medium | Missing wrapper files and signing files prevent a self-contained fresh-clone build; no committed CI/release workflow. | Repository tree and `app/build.gradle.kts`; restore wrapper and document secure signing provisioning |
| Medium | Parser/image memory limits and XML hardening absent; failures may be swallowed or interpreted as delimited text. | `XlsxEngine`, `loadAndScaleBitmap`; validate types, bound resource use, reject malformed input visibly |
| Low | Help claims automatic/100% matching, while guesses and unsupported spreadsheet cases exist. Review summary is stale after mapped edits. | `HelpDialog`, `MappedReviewDialog`, `updateMappedField`; align copy and summary with verified behavior |

## 12. Source map

All links below point to the reviewed source commit so future changes do not silently alter the evidence.

- [Build and signing configuration](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/build.gradle.kts)
- [Dependency pins](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/gradle/libs.versions.toml) and [wrapper properties](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/gradle/wrapper/gradle-wrapper.properties)
- [Environment template](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/.env.example) and [ignore rules](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/.gitignore)
- [Manifest and permissions](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/main/AndroidManifest.xml)
- [Gemini request and simulation fallback](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/main/java/com/example/ai/GeminiVisionService.kt)
- [Workbook lifecycle and export/share](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/main/java/com/example/ui/ReceiptScannerViewModel.kt)
- [Spreadsheet import/export engine](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/main/java/com/example/spreadsheet/XlsxEngine.kt) and [templates](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/main/java/com/example/spreadsheet/DefaultTemplates.kt)
- [Capture controls](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/main/java/com/example/ui/components/ReceiptCaptureSection.kt), [review dialog](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/main/java/com/example/ui/components/MappedReviewDialog.kt), and [spreadsheet table](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/main/java/com/example/ui/components/SpreadsheetTableView.kt)
- [Unit tests](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/test/java/com/example/ExampleUnitTest.kt), [Robolectric test](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/test/java/com/example/ExampleRobolectricTest.kt), and [device test](https://github.com/whitequill-software/receipt-scanner-mobile/blob/008bd3778d73e521ad0e7df20134492a5943dcd9/app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt)

Update the reviewed commit, review date, behavior, commands, acceptance record, and findings when the implementation changes.
