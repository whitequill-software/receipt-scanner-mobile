package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.byok.crypto.KeystoreCredentialStore
import com.example.byok.model.AiProvider
import com.example.byok.model.ProviderConfig
import com.example.byok.model.ProviderProfile
import com.example.byok.model.ProviderValidationResult
import com.example.byok.model.ReceiptData
import com.example.byok.model.ScanOutcome
import com.example.byok.provider.ProviderRegistry
import com.example.byok.util.ReceiptJsonParser
import com.example.model.SampleReceipt
import com.example.model.SpreadsheetWorkbook
import com.example.spreadsheet.DefaultTemplates
import com.example.spreadsheet.XlsxEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class ReceiptScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialStore = KeystoreCredentialStore(application)

    // Workbook state (Preserved across all settings changes)
    private val _workbook = MutableStateFlow(
        SpreadsheetWorkbook(
            name = "Business_Expenses.xlsx",
            headers = DefaultTemplates.BusinessExpenses.columns,
            rows = DefaultTemplates.BusinessExpenses.initialRows,
            isCustomUploaded = false
        )
    )
    val workbook: StateFlow<SpreadsheetWorkbook> = _workbook.asStateFlow()

    // Active BYOK Provider and Profile
    private val _activeProvider = MutableStateFlow(AiProvider.GEMINI)
    val activeProvider: StateFlow<AiProvider> = _activeProvider.asStateFlow()

    private val _activeProfile = MutableStateFlow(credentialStore.getProfile(AiProvider.GEMINI))
    val activeProfile: StateFlow<ProviderProfile> = _activeProfile.asStateFlow()

    private val _validationResult = MutableStateFlow<ProviderValidationResult>(ProviderValidationResult.Unvalidated)
    val validationResult: StateFlow<ProviderValidationResult> = _validationResult.asStateFlow()

    // Scanning & Analysis State
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _currentScanOutcome = MutableStateFlow<ScanOutcome?>(null)
    val currentScanOutcome: StateFlow<ScanOutcome?> = _currentScanOutcome.asStateFlow()

    private val _currentReceiptData = MutableStateFlow<ReceiptData?>(null)
    val currentReceiptData: StateFlow<ReceiptData?> = _currentReceiptData.asStateFlow()

    // Header drift tracking to prevent incompatible appends
    private var scanSnapshotHeaders: List<String> = emptyList()

    private val _isHeaderDrift = MutableStateFlow(false)
    val isHeaderDrift: StateFlow<Boolean> = _isHeaderDrift.asStateFlow()

    // Dialog & Navigation states
    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _showReviewDialog = MutableStateFlow(false)
    val showReviewDialog: StateFlow<Boolean> = _showReviewDialog.asStateFlow()

    private val _showHelpDialog = MutableStateFlow(false)
    val showHelpDialog: StateFlow<Boolean> = _showHelpDialog.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>("Ready to scan receipts or upload spreadsheets.")
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    fun selectTab(index: Int) {
        _activeTab.value = index
    }

    fun openSettings() {
        _validationResult.value = ProviderValidationResult.Unvalidated
        _showSettingsDialog.value = true
    }

    fun closeSettings() {
        _showSettingsDialog.value = false
    }

    fun openHelp() {
        _showHelpDialog.value = true
    }

    fun closeHelp() {
        _showHelpDialog.value = false
    }

    fun closeReviewDialog() {
        _showReviewDialog.value = false
        _currentScanOutcome.value = null
        _currentReceiptData.value = null
    }

    // --- Provider and Credential Management ---

    fun selectProvider(provider: AiProvider) {
        _activeProvider.value = provider
        _activeProfile.value = credentialStore.getProfile(provider)
        _validationResult.value = ProviderValidationResult.Unvalidated
    }

    fun saveKeyAndModel(
        apiKeyInput: String,
        selectedModel: String,
        customEndpointUrl: String,
        confirmedHostname: String
    ) {
        val provider = _activeProvider.value
        if (apiKeyInput.isNotBlank()) {
            credentialStore.saveProviderKey(provider.id, apiKeyInput.trim())
        }
        val updatedProfile = ProviderProfile(
            provider = provider,
            selectedModel = selectedModel.ifBlank { provider.defaultModel },
            customEndpointUrl = customEndpointUrl.trim(),
            confirmedHostname = confirmedHostname.trim(),
            hasKey = credentialStore.getProviderKey(provider.id).isNotBlank()
        )
        credentialStore.saveProfileMetadata(updatedProfile)
        _activeProfile.value = updatedProfile
        _userMessage.value = "Saved settings for ${provider.displayName} ($selectedModel)."
        _showSettingsDialog.value = false
    }

    fun validateKey(
        apiKeyInput: String,
        selectedModel: String,
        customEndpointUrl: String,
        confirmedHostname: String
    ) {
        viewModelScope.launch {
            _validationResult.value = ProviderValidationResult.Validating
            val provider = _activeProvider.value
            val effectiveKey = apiKeyInput.ifBlank { credentialStore.getProviderKey(provider.id) }
            val config = ProviderConfig(
                provider = provider,
                apiKey = effectiveKey,
                selectedModel = selectedModel.ifBlank { provider.defaultModel },
                customEndpointUrl = customEndpointUrl.trim(),
                confirmedHostname = confirmedHostname.trim()
            )

            val providerImpl = ProviderRegistry.getProvider(provider)
            val result = providerImpl.validateCredentials(config)
            _validationResult.value = result
        }
    }

    fun deleteCurrentProviderKey() {
        val provider = _activeProvider.value
        credentialStore.deleteProviderKey(provider.id)
        _activeProfile.value = credentialStore.getProfile(provider)
        _validationResult.value = ProviderValidationResult.Unvalidated
        _userMessage.value = "Deleted local credential for ${provider.displayName}."
    }

    // --- Spreadsheet Management ---

    fun selectTemplate(templateId: String) {
        val tmpl = DefaultTemplates.allTemplates.find { it.id == templateId } ?: DefaultTemplates.BusinessExpenses
        _workbook.value = SpreadsheetWorkbook(
            name = "${tmpl.title.replace(" ", "_")}.xlsx",
            headers = tmpl.columns,
            rows = tmpl.initialRows,
            isCustomUploaded = false
        )
        checkHeaderDrift()
        _userMessage.value = "Loaded template: ${tmpl.title}"
    }

    fun loadUploadedSpreadsheet(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val fileName = getFileName(context, uri) ?: "Uploaded_Spreadsheet.xlsx"
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val parsed = XlsxEngine.parseSpreadsheet(inputStream, fileName)
                    _workbook.value = parsed
                    checkHeaderDrift()
                    _userMessage.value = "Loaded '$fileName' with ${parsed.headers.size} columns."
                } else {
                    _userMessage.value = "Could not open selected spreadsheet."
                }
            } catch (e: Exception) {
                _userMessage.value = "Error parsing spreadsheet: ${e.localizedMessage}"
            }
        }
    }

    private fun checkHeaderDrift() {
        if (_showReviewDialog.value && scanSnapshotHeaders.isNotEmpty()) {
            _isHeaderDrift.value = _workbook.value.headers != scanSnapshotHeaders
        }
    }

    // --- Receipt Scanning (Zero Fabrication) ---

    fun processReceiptImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            val provider = _activeProvider.value
            val config = credentialStore.getFullConfig(provider)

            if (config.apiKey.isBlank()) {
                _userMessage.value = "No API key configured for ${provider.displayName}. Please configure your key in Settings."
                _showSettingsDialog.value = true
                return@launch
            }

            _isAnalyzing.value = true
            _userMessage.value = "Analyzing receipt with ${provider.displayName} (${config.selectedModel})..."
            scanSnapshotHeaders = _workbook.value.headers
            _isHeaderDrift.value = false

            try {
                val providerImpl = ProviderRegistry.getProvider(provider)
                val outcome = providerImpl.extractReceipt(context, imageUri, scanSnapshotHeaders, config)
                _currentScanOutcome.value = outcome

                when (outcome) {
                    is ScanOutcome.Success -> {
                        _currentReceiptData.value = outcome.data
                        _showReviewDialog.value = true
                        _userMessage.value = "Receipt scanned via ${provider.displayName}! Review fields before appending."
                    }
                    is ScanOutcome.Error -> {
                        _currentReceiptData.value = null
                        _showReviewDialog.value = false
                        _userMessage.value = outcome.sanitizedMessage
                    }
                    is ScanOutcome.Demo -> {
                        _currentReceiptData.value = outcome.data
                        _showReviewDialog.value = true
                    }
                }
            } catch (e: Exception) {
                _userMessage.value = "Scan failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun loadSampleReceipt(context: Context, sample: SampleReceipt) {
        scanSnapshotHeaders = _workbook.value.headers
        _isHeaderDrift.value = false

        val mappings = mutableMapOf<String, String>()
        scanSnapshotHeaders.forEach { col ->
            val lower = col.lowercase()
            mappings[col] = when {
                lower.contains("date") -> sample.date
                lower.contains("vendor") || lower.contains("merchant") -> sample.merchant
                lower.contains("subtotal") -> sample.subtotal
                lower.contains("tax") -> sample.tax
                lower.contains("total") || lower.contains("amount") -> sample.total
                lower.contains("category") -> sample.category
                lower.contains("payment") -> sample.payment
                lower.contains("desc") || lower.contains("item") -> sample.items
                else -> ""
            }
        }

        val demoData = ReceiptData(
            imageUri = null,
            merchant = sample.merchant,
            date = sample.date,
            subtotal = sample.subtotal,
            tax = sample.tax,
            tip = "0.00",
            total = sample.total,
            currency = "$",
            category = sample.category,
            paymentMethod = sample.payment,
            itemSummary = sample.items,
            mappedColumns = mappings,
            isDemo = true
        )

        _currentScanOutcome.value = ScanOutcome.Demo(demoData, sample.title)
        _currentReceiptData.value = demoData
        _showReviewDialog.value = true
        _isAnalyzing.value = false
        _userMessage.value = "Loaded demo receipt for ${sample.merchant}."
    }

    fun updateMappedField(columnName: String, newValue: String) {
        _currentReceiptData.update { current ->
            if (current == null) return@update null
            val updatedMap = current.mappedColumns.toMutableMap()
            updatedMap[columnName] = newValue

            val lower = columnName.lowercase()
            var newMerchant = current.merchant
            var newDate = current.date
            var newTotal = current.total

            if (lower.contains("vendor") || lower.contains("merchant") || lower.contains("payee")) {
                newMerchant = newValue
            } else if (lower.contains("date")) {
                newDate = newValue
            } else if (lower.contains("total") || lower.contains("amount") || lower.contains("cost")) {
                newTotal = newValue
            }

            current.copy(
                merchant = newMerchant,
                date = newDate,
                total = newTotal,
                mappedColumns = updatedMap
            )
        }
    }

    fun commitCurrentExtractionToSpreadsheet() {
        if (_workbook.value.headers != scanSnapshotHeaders) {
            _isHeaderDrift.value = true
            _userMessage.value = "Cannot append: Spreadsheet headers changed since scan started."
            return
        }

        val data = _currentReceiptData.value ?: return
        val currentHeaders = _workbook.value.headers
        val newRow = mutableMapOf<String, String>()
        currentHeaders.forEach { header ->
            newRow[header] = data.mappedColumns[header] ?: ""
        }

        _workbook.update { it.addRow(newRow) }
        _showReviewDialog.value = false
        _currentScanOutcome.value = null
        _currentReceiptData.value = null
        _activeTab.value = 1
        _userMessage.value = "Appended receipt row for ${data.merchant.ifBlank { "Receipt" }} (${data.total})."
    }

    fun deleteRow(rowIndex: Int) {
        _workbook.update { it.removeRow(rowIndex) }
        _userMessage.value = "Row ${rowIndex + 1} deleted."
    }

    fun exportToSafUri(context: Context, destinationUri: Uri) {
        viewModelScope.launch {
            try {
                val xlsxBytes = XlsxEngine.generateXlsxBytes(_workbook.value)
                context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    out.write(xlsxBytes)
                }
                _userMessage.value = "Successfully exported ${_workbook.value.name} (${_workbook.value.rows.size} rows) to device!"
            } catch (e: Exception) {
                _userMessage.value = "Failed to export: ${e.localizedMessage}"
            }
        }
    }

    fun shareSpreadsheet(context: Context) {
        viewModelScope.launch {
            try {
                val xlsxBytes = XlsxEngine.generateXlsxBytes(_workbook.value)
                val exportDir = File(context.cacheDir, "exports")
                if (!exportDir.exists()) exportDir.mkdirs()

                val fileName = if (_workbook.value.name.endsWith(".xlsx")) {
                    _workbook.value.name
                } else {
                    "${_workbook.value.name.substringBeforeLast(".")}.xlsx"
                }

                val exportFile = File(exportDir, fileName)
                FileOutputStream(exportFile).use { it.write(xlsxBytes) }

                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Receipt Expense Spreadsheet - ${exportFile.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Share Spreadsheet (${exportFile.name})")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                _userMessage.value = "Spreadsheet shared."
            } catch (e: Exception) {
                _userMessage.value = "Could not share file: ${e.localizedMessage}"
            }
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        name = cursor.getString(nameIdx)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }
}
