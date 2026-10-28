package com.example.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.SampleReceipt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptCaptureSection(
    isAnalyzing: Boolean,
    hasKey: Boolean,
    providerName: String,
    modelName: String,
    onOpenSettings: () -> Unit,
    onReceiptCaptured: (Uri) -> Unit,
    onSelectSampleReceipt: (SampleReceipt) -> Unit
) {
    val context = LocalContext.current
    // The camera can recreate our activity while it is in the foreground.
    var tempCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var captureError by rememberSaveable { mutableStateOf<String?>(null) }

    fun discardPhoto(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (exception: Exception) {
            Log.w("ReceiptCapture", "Could not remove unused camera output", exception)
        }
    }

    // Camera Launcher with FileProvider
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = remember { ReceiptCameraCapture() }
    ) { success ->
        val uri = tempCameraUri
        tempCameraUri = null
        if (!success) {
            Log.d("ReceiptCapture", "Camera capture canceled or returned RESULT_CANCELED")
            uri?.let(::discardPhoto)
        } else {
            val hasPhoto = try {
                uri != null && context.contentResolver.openInputStream(uri)?.use { it.read() != -1 } == true
            } catch (exception: Exception) {
                Log.e("ReceiptCapture", "Could not read completed camera output", exception)
                false
            }
            if (hasPhoto && uri != null) {
                onReceiptCaptured(uri)
            } else {
                Log.e("ReceiptCapture", "Camera returned success without readable photo output")
                captureError = context.getString(R.string.camera_no_photo)
                uri?.let(::discardPhoto)
            }
        }
    }

    // Photo Picker Launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onReceiptCaptured(uri)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("receipt_capture_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Scan Receipt",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (hasKey) "Ready with $providerName ($modelName)" else "Requires API key or explore Demo mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Missing Key Notice with Setup Action
            if (!hasKey) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BYOK Setup Required for Real Scans",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Enter your $providerName key to scan receipts, or test with Demo samples below.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("setup_key_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Setup", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Camera Button
                Button(
                    onClick = {
                        if (!hasKey) {
                            onOpenSettings()
                        } else {
                            captureError = null
                            try {
                                val uri = createReceiptCameraUri(context)
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } catch (e: Exception) {
                                Log.e(
                                    "ReceiptCapture",
                                    "Camera launch failed; provider=${context.packageName}.fileprovider",
                                    e
                                )
                                tempCameraUri?.let(::discardPhoto)
                                tempCameraUri = null
                                captureError = context.getString(
                                    when (e) {
                                        is ActivityNotFoundException -> R.string.camera_unavailable
                                        is SecurityException -> R.string.camera_access_blocked
                                        is java.io.IOException -> R.string.camera_storage_error
                                        else -> R.string.camera_launch_error
                                    }
                                )
                            }
                        }
                    },
                    enabled = tempCameraUri == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("take_photo_button")
                ) {
                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Camera", fontWeight = FontWeight.Bold)
                }

                // Gallery Button
                OutlinedButton(
                    onClick = {
                        if (!hasKey) {
                            onOpenSettings()
                        } else {
                            captureError = null
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    enabled = tempCameraUri == null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("gallery_button")
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Gallery")
                }
            }

            captureError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp).testTag("capture_error")
                )
            }

            // Scanning / Analyzing Banner
            AnimatedVisibility(
                visible = isAnalyzing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Analyzing with $providerName...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Model: $modelName • Extracting values & mapping spreadsheet columns",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Test Samples Section (DEMO MODE - Clearly Labeled)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DEMO SAMPLES (NO KEY REQUIRED)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val sampleReceipts = listOf(
                SampleReceipt(
                    title = "Bistro Dinner",
                    merchant = "Bistro Lumiere",
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    total = "58.40",
                    subtotal = "50.00",
                    tax = "4.40",
                    category = "Meals & Entertainment",
                    payment = "Visa *8832",
                    items = "Steak Frites, Sparkling Water, Espresso"
                ),
                SampleReceipt(
                    title = "Office Depot",
                    merchant = "Office Supply Depot",
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    total = "124.99",
                    subtotal = "114.67",
                    tax = "10.32",
                    category = "Office Supplies",
                    payment = "Corporate Amex",
                    items = "Laser Toner, Copy Paper 5-Pack, USB Drive"
                ),
                SampleReceipt(
                    title = "Airlines Baggage",
                    merchant = "SkyHigh Airlines",
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    total = "75.00",
                    subtotal = "75.00",
                    tax = "0.00",
                    category = "Travel & Lodging",
                    payment = "Mastercard *1920",
                    items = "Checked Baggage Fee, Seat Selection"
                ),
                SampleReceipt(
                    title = "Client Coffee",
                    merchant = "Artisan Roasters",
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    total = "18.75",
                    subtotal = "16.89",
                    tax = "1.86",
                    category = "Meals & Entertainment",
                    payment = "Apple Pay",
                    items = "2x Cold Brew, Croissant, Muffin"
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sampleReceipts.forEach { sample ->
                    Surface(
                        onClick = { onSelectSampleReceipt(sample) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 1.dp,
                        modifier = Modifier.testTag("sample_receipt_${sample.title.replace(" ", "_")}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = sample.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$${sample.total} • [Demo Mode]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
