package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.byok.model.ScanOutcome
import com.example.ui.components.HelpDialog
import com.example.ui.components.MappedReviewDialog
import com.example.ui.components.ReceiptCaptureSection
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SpreadsheetPickerCard
import com.example.ui.components.SpreadsheetTableView
import com.example.ui.components.TopAppBarSection

@Composable
fun ReceiptScannerScreen(
    viewModel: ReceiptScannerViewModel = viewModel()
) {
    val context = LocalContext.current
    val workbook by viewModel.workbook.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val validationResult by viewModel.validationResult.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val currentScanOutcome by viewModel.currentScanOutcome.collectAsStateWithLifecycle()
    val currentReceiptData by viewModel.currentReceiptData.collectAsStateWithLifecycle()
    val isHeaderDrift by viewModel.isHeaderDrift.collectAsStateWithLifecycle()

    val showSettingsDialog by viewModel.showSettingsDialog.collectAsStateWithLifecycle()
    val showReviewDialog by viewModel.showReviewDialog.collectAsStateWithLifecycle()
    val showHelpDialog by viewModel.showHelpDialog.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBarSection(
                workbook = workbook,
                activeProvider = activeProvider,
                activeModel = activeProfile.selectedModel,
                hasKey = activeProfile.hasKey,
                onOpenSettings = { viewModel.openSettings() },
                onOpenHelp = { viewModel.openHelp() },
                onShareSpreadsheet = { viewModel.shareSpreadsheet(context) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Status Feedback Banner
            AnimatedVisibility(
                visible = userMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                userMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Tab Navigation (Scanner / Spreadsheet Table)
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Scanner & Setup", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_scanner")
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = {
                        Text(
                            text = "Spreadsheet (${workbook.rows.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    modifier = Modifier.testTag("tab_spreadsheet")
                )
            }

            // Body Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (activeTab) {
                    0 -> {
                        // Scanner & Workflow Setup Tab
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            SpreadsheetPickerCard(
                                workbook = workbook,
                                onUploadFile = { uri -> viewModel.loadUploadedSpreadsheet(context, uri) },
                                onSelectTemplate = { tmplId -> viewModel.selectTemplate(tmplId) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            ReceiptCaptureSection(
                                isAnalyzing = isAnalyzing,
                                hasKey = activeProfile.hasKey,
                                providerName = activeProvider.displayName,
                                modelName = activeProfile.selectedModel,
                                onOpenSettings = { viewModel.openSettings() },
                                onReceiptCaptured = { uri -> viewModel.processReceiptImage(context, uri) },
                                onSelectSampleReceipt = { sample -> viewModel.loadSampleReceipt(context, sample) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    1 -> {
                        // Spreadsheet Data Table Tab
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            SpreadsheetTableView(
                                workbook = workbook,
                                onDeleteRow = { index -> viewModel.deleteRow(index) },
                                onExportToSaf = { uri -> viewModel.exportToSafUri(context, uri) },
                                onShareSpreadsheet = { viewModel.shareSpreadsheet(context) }
                            )
                        }
                    }
                }
            }
        }
    }

    // BYOK Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            currentProvider = activeProvider,
            currentProfile = activeProfile,
            validationResult = validationResult,
            onSelectProvider = { provider -> viewModel.selectProvider(provider) },
            onSaveKeyAndModel = { key, model, url, host ->
                viewModel.saveKeyAndModel(key, model, url, host)
            },
            onValidateKey = { key, model, url, host ->
                viewModel.validateKey(key, model, url, host)
            },
            onDeleteKey = { viewModel.deleteCurrentProviderKey() },
            onDismiss = { viewModel.closeSettings() }
        )
    }

    // Review & Edit Dialog
    if (showReviewDialog && currentReceiptData != null) {
        val providerLabel = when (val outcome = currentScanOutcome) {
            is ScanOutcome.Success -> outcome.providerId
            is ScanOutcome.Demo -> "Demo"
            else -> activeProvider.id
        }
        val modelLabel = when (val outcome = currentScanOutcome) {
            is ScanOutcome.Success -> outcome.modelUsed
            is ScanOutcome.Demo -> "Demo Sample"
            else -> activeProfile.selectedModel
        }

        MappedReviewDialog(
            receiptData = currentReceiptData!!,
            providerId = providerLabel,
            modelUsed = modelLabel,
            targetColumns = workbook.headers,
            isHeaderDrift = isHeaderDrift,
            onFieldChange = { col, value -> viewModel.updateMappedField(col, value) },
            onConfirmAddRow = { viewModel.commitCurrentExtractionToSpreadsheet() },
            onDismiss = { viewModel.closeReviewDialog() }
        )
    }

    // Help & Instructions Dialog
    if (showHelpDialog) {
        HelpDialog(
            onDismiss = { viewModel.closeHelp() }
        )
    }
}
