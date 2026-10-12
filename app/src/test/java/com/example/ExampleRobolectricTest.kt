package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.byok.crypto.KeystoreCredentialStore
import com.example.byok.model.AiProvider
import com.example.byok.model.ProviderProfile
import com.example.byok.model.ScanOutcome
import com.example.spreadsheet.DefaultTemplates
import com.example.ui.ReceiptScannerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var credentialStore: KeystoreCredentialStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        credentialStore = KeystoreCredentialStore(context)
        credentialStore.clearAllCredentials()
    }

    @Test
    fun testAppNameString() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Receipt Scanner", appName)
    }

    @Test
    fun testKeystoreCredentialStorageLifecycle() {
        // Initially empty
        assertEquals("", credentialStore.getProviderKey(AiProvider.GEMINI.id))
        assertEquals("", credentialStore.getProviderKey(AiProvider.OPENAI.id))

        // Save synthetic keys for separate providers
        credentialStore.saveProviderKey(AiProvider.GEMINI.id, "synthetic-gemini-key-12345")
        credentialStore.saveProviderKey(AiProvider.OPENAI.id, "synthetic-openai-key-67890")

        // Retrieve and verify provider isolation
        assertEquals("synthetic-gemini-key-12345", credentialStore.getProviderKey(AiProvider.GEMINI.id))
        assertEquals("synthetic-openai-key-67890", credentialStore.getProviderKey(AiProvider.OPENAI.id))
        assertEquals("", credentialStore.getProviderKey(AiProvider.ANTHROPIC.id))

        // Replace an existing key
        credentialStore.saveProviderKey(AiProvider.GEMINI.id, "synthetic-gemini-key-REPLACED")
        assertEquals("synthetic-gemini-key-REPLACED", credentialStore.getProviderKey(AiProvider.GEMINI.id))

        // Delete a key
        credentialStore.deleteProviderKey(AiProvider.GEMINI.id)
        assertEquals("", credentialStore.getProviderKey(AiProvider.GEMINI.id))
        // Other provider key remains intact
        assertEquals("synthetic-openai-key-67890", credentialStore.getProviderKey(AiProvider.OPENAI.id))
    }

    @Test
    fun testCorruptedCredentialFileRecovery() {
        // Write corrupted bytes directly into the credentials file
        val file = File(context.filesDir, "byok_credentials.enc")
        FileOutputStream(file).use { it.write(byteArrayOf(1, 2, 3, 4, 5)) }

        // Must gracefully return empty string rather than crashing the app
        val key = credentialStore.getProviderKey(AiProvider.GEMINI.id)
        assertEquals("", key)
    }

    @Test
    fun testProviderProfileMetadataPersistence() {
        val profile = ProviderProfile(
            provider = AiProvider.OPENAI,
            selectedModel = "gpt-4o",
            customEndpointUrl = "",
            confirmedHostname = "",
            hasKey = false
        )
        credentialStore.saveProfileMetadata(profile)

        val retrieved = credentialStore.getProfile(AiProvider.OPENAI)
        assertEquals("gpt-4o", retrieved.selectedModel)
        assertEquals(AiProvider.OPENAI, retrieved.provider)
    }

    @Test
    fun testViewModelSettingsChangePreservesWorkbook() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ReceiptScannerViewModel(app)

        val initialName = viewModel.workbook.value.name
        val initialRowsCount = viewModel.workbook.value.rows.size

        // Select different providers and save credentials
        viewModel.selectProvider(AiProvider.ANTHROPIC)
        viewModel.saveKeyAndModel("synthetic-key", "claude-3-5-haiku-20241022", "", "")

        // Workbook must NOT be reset!
        assertEquals(initialName, viewModel.workbook.value.name)
        assertEquals(initialRowsCount, viewModel.workbook.value.rows.size)
    }

    @Test
    fun testViewModelHeaderDriftPrevention() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ReceiptScannerViewModel(app)

        // Load a demo sample
        val sample = DefaultTemplates.BusinessExpenses.let {
            com.example.model.SampleReceipt(
                title = "Test Receipt",
                merchant = "Test Vendor",
                date = "2026-10-12",
                total = "25.00",
                subtotal = "23.00",
                tax = "2.00",
                category = "Office Supplies",
                payment = "Cash",
                items = "Pens"
            )
        }
        viewModel.loadSampleReceipt(context, sample)
        assertTrue(viewModel.showReviewDialog.value)

        // User changes the spreadsheet template while scan is in review!
        viewModel.selectTemplate(DefaultTemplates.TravelMeals.id)

        // Attempting to commit must be blocked by header drift detection
        viewModel.commitCurrentExtractionToSpreadsheet()
        assertTrue(viewModel.isHeaderDrift.value)
    }

    @Test
    fun testMappedFieldEditsUpdateSummary() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ReceiptScannerViewModel(app)

        val sample = com.example.model.SampleReceipt(
            title = "Coffee",
            merchant = "Initial Cafe",
            date = "2026-10-12",
            total = "5.00",
            subtotal = "4.50",
            tax = "0.50",
            category = "Meals",
            payment = "Cash",
            items = "Latte"
        )
        viewModel.loadSampleReceipt(context, sample)

        // Update the merchant column mapping
        viewModel.updateMappedField("Merchant", "Updated Cafe Name")
        assertEquals("Updated Cafe Name", viewModel.currentReceiptData.value?.merchant)

        // Update the total column mapping
        viewModel.updateMappedField("Total Amount", "7.50")
        assertEquals("7.50", viewModel.currentReceiptData.value?.total)
    }

    @Test
    fun testDemoModeIsolation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ReceiptScannerViewModel(app)

        val sample = com.example.model.SampleReceipt(
            title = "Demo Sample",
            merchant = "Demo Store",
            date = "2026-10-12",
            total = "10.00",
            subtotal = "9.00",
            tax = "1.00",
            category = "Supplies",
            payment = "Card",
            items = "Notebook"
        )
        viewModel.loadSampleReceipt(context, sample)

        val outcome = viewModel.currentScanOutcome.value
        assertTrue(outcome is ScanOutcome.Demo)
        assertEquals(true, viewModel.currentReceiptData.value?.isDemo)
    }
}
