package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiVisionService
import com.example.model.ReceiptExtractionResult
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

class ReceiptScannerViewModel : ViewModel() {

    private val _workbook = MutableStateFlow(
        SpreadsheetWorkbook(
            name = "Business_Expenses.xlsx",
            headers = DefaultTemplates.BusinessExpenses.columns,
            rows = DefaultTemplates.BusinessExpenses.initialRows,
            isCustomUploaded = false
        )
    )
    val workbook: StateFlow<SpreadsheetWorkbook> = _workbook.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _currentExtraction = MutableStateFlow<ReceiptExtractionResult?>(null)
    val currentExtraction: StateFlow<ReceiptExtractionResult?> = _currentExtraction.asStateFlow()

    private val _showReviewDialog = MutableStateFlow(false)
    val showReviewDialog: StateFlow<Boolean> = _showReviewDialog.asStateFlow()

    private val _showHelpDialog = MutableStateFlow(false)
    val showHelpDialog: StateFlow<Boolean> = _showHelpDialog.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>("Ready to scan receipts or upload spreadsheets.")
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _activeTab = MutableStateFlow(0) // 0: Scanner & Workflows, 1: Spreadsheet Table
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    fun selectTab(index: Int) {
        _activeTab.value = index
    }

    fun openHelp() {
        _showHelpDialog.value = true
    }

    fun closeHelp() {
        _showHelpDialog.value = false
    }

    fun closeReviewDialog() {
        _showReviewDialog.value = false
    }

    fun selectTemplate(templateId: String) {
        val tmpl = DefaultTemplates.allTemplates.find { it.id == templateId } ?: DefaultTemplates.BusinessExpenses
        _workbook.value = SpreadsheetWorkbook(
            name = "${tmpl.title.replace(" ", "_")}.xlsx",
            headers = tmpl.columns,
            rows = tmpl.initialRows,
            isCustomUploaded = false
        )
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
                    _userMessage.value = "Loaded '$fileName' with ${parsed.headers.size} columns and ${parsed.rows.size} existing rows."
                } else {
                    _userMessage.value = "Could not open selected spreadsheet."
                }
            } catch (e: Exception) {
                _userMessage.value = "Error parsing spreadsheet: ${e.localizedMessage}"
            }
        }
    }

    fun processReceiptImage(context: Context, imageUri: Uri) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _userMessage.value = "Gemini AI is analyzing receipt and matching spreadsheet columns..."
            try {
                val targetColumns = _workbook.value.headers
                val result = GeminiVisionService.analyzeReceipt(context, imageUri, targetColumns)
                _currentExtraction.value = result
                _showReviewDialog.value = true
                _userMessage.value = "Receipt scanned! Review extracted fields before appending."
            } catch (e: Exception) {
                _userMessage.value = "Scanning failed: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun loadSampleReceipt(context: Context, sample: SampleReceipt) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _userMessage.value = "Loading sample receipt: ${sample.title}..."
            kotlinx.coroutines.delay(600) // Small tactile delay for visual scanning feedback
            val targetColumns = _workbook.value.headers
            val mappings = mutableMapOf<String, String>()
            targetColumns.forEach { col ->
                mappings[col] = GeminiVisionService.smartColumnMatch(
                    colName = col,
                    merchant = sample.merchant,
                    date = sample.date,
                    total = sample.total,
                    subtotal = sample.subtotal,
                    tax = sample.tax,
                    tip = "0.00",
                    category = sample.category,
                    payment = sample.payment,
                    items = sample.items
                )
            }
            val result = ReceiptExtractionResult(
                imageUri = null,
                merchant = sample.merchant,
                date = sample.date,
                totalAmount = sample.total,
                subtotal = sample.subtotal,
                taxAmount = sample.tax,
                tipAmount = "0.00",
                category = sample.category,
                paymentMethod = sample.payment,
                itemsSummary = sample.items,
                mappedColumns = mappings,
                rawAiResponse = "Sample receipt demonstration",
                isAiPowered = true
            )
            _currentExtraction.value = result
            _showReviewDialog.value = true
            _isAnalyzing.value = false
            _userMessage.value = "Review fields for ${sample.merchant}."
        }
    }

    fun updateMappedField(columnName: String, newValue: String) {
        _currentExtraction.update { current ->
            if (current == null) return@update null
            val updatedMap = current.mappedColumns.toMutableMap()
            updatedMap[columnName] = newValue
            current.copy(mappedColumns = updatedMap)
        }
    }

    fun commitCurrentExtractionToSpreadsheet() {
        val extraction = _currentExtraction.value ?: return
        val currentHeaders = _workbook.value.headers
        val newRow = mutableMapOf<String, String>()
        currentHeaders.forEach { header ->
            newRow[header] = extraction.mappedColumns[header] ?: ""
        }

        _workbook.update { it.addRow(newRow) }
        _showReviewDialog.value = false
        _currentExtraction.value = null
        _activeTab.value = 1 // Switch to spreadsheet view so user immediately sees their row!
        _userMessage.value = "Added receipt row for ${extraction.merchant.ifBlank { "Receipt" }} ($${extraction.totalAmount})."
    }

    fun deleteRow(rowIndex: Int) {
        _workbook.update { it.removeRow(rowIndex) }
        _userMessage.value = "Row ${rowIndex + 1} deleted."
    }

    fun clearAllRows() {
        _workbook.update { it.copy(rows = emptyList()) }
        _userMessage.value = "Cleared all rows."
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
