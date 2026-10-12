# Receipt Scanner — Smart Spreadsheet Extraction & Export

An intelligent Android application that bridges physical receipts and spreadsheet workflows. Users upload or pick an Excel spreadsheet (`.xlsx`, `.xls`, or choose from ready-to-use business expense templates), snap receipt photos using the camera or gallery, extract and map line items and totals to the spreadsheet's exact columns using Gemini vision intelligence, review mapped data interactively, and export/share the populated spreadsheet.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions were confirmed during the Phase 1 clarification interview:
> - **Spreadsheet Formats**: Support `.xlsx` and `.xls` uploads, plus bundled starter templates (Business Expenses, Travel & Per-Diem, Contractor Billing) so users can start immediately even without an existing spreadsheet.
> - **Row Handling**: Append each scanned receipt as a new row in sequence, preserving existing spreadsheet rows and formatting.
> - **Review Experience**: Provide an interactive visual review screen displaying the receipt photo alongside detected values and column mappings, allowing user adjustments before writing into the spreadsheet.

- **AI Model Selection**: `gemini-3.5-flash` with multimodal vision (direct REST API with `BuildConfig.GEMINI_API_KEY`) for high-accuracy text extraction, merchant identification, tax/subtotal/total parsing, and flexible schema-aligned field mapping.
- **Export & Storage**: Built-in OpenXML `.xlsx` generation and sharing via standard Android `Intent.ACTION_SEND` and Android Storage Access Framework (`ActivityResultContracts.CreateDocument`).
- **Help & Guidance**: Accessible Help modal and contextual step-by-step guidance covering upload, camera capture best practices, column matching tips, and export options.

---

### 1. Overview & Core Concept

- **What It Does**: Automates manual expense entry by converting photos of receipts into rows in user-defined or standard Excel spreadsheets.
- **Target Audience / Persona**: Business travelers, freelancers, small business owners, and accounting teams tracking daily receipts against expense templates.
- **Key Value**: Eliminates tedious manual data entry, prevents transposition errors, automatically aligns with any custom column headers (e.g., "Vendor", "Transaction Date", "Category", "Amount", "Tax", "Payment Method"), and delivers an export-ready Excel file.

---

### 2. User Experience & Visual Design

#### Key User Flows

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ 1. Start / Pick │  ───> │ 2. Capture /    │  ───> │ 3. Gemini Vision│
│   Spreadsheet   │       │    Upload Photo │       │    Extraction   │
└─────────────────┘       └─────────────────┘       └─────────────────┘
         │                                                   │
         v                                                   v
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ 6. Download /   │  <─── │ 5. Append Row to│  <─── │ 4. Side-by-Side │
│    Share XLSX   │       │    Spreadsheet  │       │    Field Review │
└─────────────────┘       └─────────────────┘       └─────────────────┘
```

1. **Step 1: Spreadsheet Setup**:
   - Option to upload a local `.xlsx` or `.xls` file.
   - Option to choose a pre-configured template (Expense Report, Tax Deductions, Travel & Mileage).
   - Display parsed headers (columns) and existing rows in a clean summary table.
2. **Step 2: Receipt Capture**:
   - Tap "Scan Receipt" to launch the camera or pick from gallery.
   - Live viewfinder with alignment guides and tips for optimal lighting and contrast.
3. **Step 3: AI Processing & Smart Mapping**:
   - Animated parsing indicator with status badges ("Analyzing receipt...", "Aligning columns...").
   - Gemini Vision inspects the image and matches receipt metadata to the active spreadsheet column headers.
4. **Step 4: Interactive Review & Correction**:
   - Preview of the receipt image alongside mapped fields.
   - Editable fields for Date, Merchant, Category, Total, Tax, Payment Method, and custom columns.
   - "Add to Spreadsheet" action.
5. **Step 5: Spreadsheet View & Export**:
   - Live preview of the spreadsheet data grid with the newly appended row highlighted.
   - Add more receipts sequentially or tap "Download / Share Spreadsheet" (`.xlsx`).
6. **Help & Tutorial Drawer**:
   - Dedicated "Help & Instructions" button present on the top app bar with detailed FAQs, sample workflows, and column naming tips.

#### Visual Identity & Theme
- **Aesthetic Direction**: Crisp, modern financial utility with emerald & slate palette (`#0F172A` Slate Dark, `#059669` Emerald Green accent, `#F8FAFC` Clean Light Surface).
- **Typography**: Clean typographic hierarchy with clear numeric tabular styling for currency and dates.
- **Micro-Interactions**: Spring-animated sheet transitions, tactile button states, badge indicators for matched confidence, and success confetti/feedback on export.

---

### 3. Key Product Decisions & Trade-Offs

- **Spreadsheet Engine**:
  - *Chosen Approach*: Lightweight, robust native OpenXML generator and reader in Kotlin.
  - *Why*: Eliminates hefty 30MB+ Java dependencies like full Apache POI which have known Android runtime incompatibilities with missing `java.awt` packages. Ensures 100% reliable `.xlsx` reading and writing without bloating APK size or hitting multidex limits.
  - *Alternatives Considered*: Standard Apache POI (heavy, risk of dex/runtime crashes on Android API levels).

- **Camera & Image Selection**:
  - *Chosen Approach*: Jetpack ActivityResult contracts (`TakePicture` / `PickVisualMedia`) with FileProvider, plus CameraX fallback.
  - *Why*: Gives users immediate access to native high-resolution camera hardware with auto-focus and flash, without complex device-specific camera bugs.

- **Offline / Local Persistence**:
  - *Chosen Approach*: Session state + in-memory spreadsheet workbook with Room-backed receipt history for safety against app restarts.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                              UI LAYER                                  │
│  MainActivity  ──>  ReceiptScannerScreen                               │
│                      ├── TopBar (App Title + Help Button)              │
│                      ├── SpreadsheetPickerSection (Upload/Template)    │
│                      ├── CameraCaptureSheet / GalleryPicker            │
│                      ├── MappedDataReviewDialog / Screen               │
│                      ├── SpreadsheetGridView                           │
│                      └── HelpInstructionsDialog                       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                           VIEWMODEL LAYER                              │
│  ReceiptScannerViewModel                                               │
│   ├── UiState (SpreadsheetLoaded, Scanning, Reviewing, ReadyToExport)   │
│   ├── parseSpreadsheet(uri / template)                                 │
│   ├── analyzeReceiptImage(bitmap, columns)                             │
│   ├── updateMappedValues(map)                                          │
│   ├── appendRowAndRegenerate()                                         │
│   └── exportSpreadsheet(uri)                                           │
└───────────────────┬───────────────────────────────┬────────────────────┘
                    │                               │
┌───────────────────▼──────────────┐   ┌────────────▼───────────────────┐
│         GEMINI AI SERVICE        │   │       SPREADSHEET ENGINE       │
│  GeminiVisionService             │   │  XlsxManager                   │
│   - Direct REST API              │   │   - Unzip / parse sheet1.xml   │
│   - Prompt with target columns   │   │   - Extract header strings     │
│   - Structured JSON response     │   │   - Build valid OpenXML .xlsx  │
│   - Base64 compressed image      │   │   - Template generator         │
└──────────────────────────────────┘   └────────────────────────────────┘
```

#### State & Data Models

- **`SpreadsheetData`**:
  - `fileName: String`
  - `columns: List<String>`
  - `rows: List<Map<String, String>>`
- **`ReceiptScanResult`**:
  - `imageUri: Uri?`
  - `merchant: String`
  - `date: String`
  - `total: String`
  - `tax: String`
  - `category: String`
  - `columnMappings: Map<String, String>` (mapped to active spreadsheet columns)
- **`HelpTopic`**:
  - Steps, FAQs, supported column names, and tips for high-clarity photos.
