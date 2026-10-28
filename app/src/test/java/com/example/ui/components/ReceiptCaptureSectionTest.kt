package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiptCaptureSectionTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val registry = CaptureRegistry()
    private val captured = mutableListOf<Uri>()

    @Before fun resetProviderRootForRobolectricSandbox() {
        // Robolectric creates a different cacheDir per test, while AndroidX's
        // process-wide root cache survives. Do not reuse a previous test's root.
        ReflectionHelpers.getStaticField<MutableMap<String, Any>>(FileProvider::class.java, "sCache")
            .remove("${context.packageName}.fileprovider")
    }

    private inner class CaptureRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry get() = this
        var cameras = 0
        var galleries = 0
        var requestCode = 0
        var uri: Uri? = null
        var intent: Intent? = null
        var failure: RuntimeException? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            if (contract is ActivityResultContracts.TakePicture) {
                cameras++
                this.requestCode = requestCode
                uri = input as Uri
                intent = contract.createIntent(context, input)
                failure?.let { throw it }
            } else if (contract is ActivityResultContracts.PickVisualMedia) {
                galleries++
            } else {
                fail("Unexpected activity contract: $contract")
            }
        }
    }

    private fun showCapture(hasKey: Boolean = true, onSettings: () -> Unit = {}): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                MaterialTheme {
                    ReceiptCaptureSection(
                        isAnalyzing = false, hasKey = hasKey,
                        providerName = "Test provider", modelName = "Test model",
                        onOpenSettings = onSettings,
                        onReceiptCaptured = { captured.add(it) },
                        onSelectSampleReceipt = {}
                    )
                }
            }
        }
        return restoration
    }

    @Test fun delegatedCaptureDoesNotDeclareCameraOrStoragePermissions() {
        val permissions = context.packageManager.getPackageInfo(
            context.packageName, PackageManager.GET_PERMISSIONS
        ).requestedPermissions.orEmpty()
        assertFalse(permissions.contains(Manifest.permission.CAMERA))
        assertFalse(permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    @Test fun fileProviderCreatesUniqueReadableWritableJpegUrisAndStaysPrivate() {
        val first = createReceiptCameraUri(context)
        val second = createReceiptCameraUri(context)
        try {
            assertNotEquals(first, second)
            assertEquals("content", first.scheme)
            assertEquals("${context.packageName}.fileprovider", first.authority)
            assertTrue(first.path!!.startsWith("/camera_images/"))
            assertEquals("image/jpeg", context.contentResolver.getType(first))
            context.contentResolver.openOutputStream(first)!!.use { it.write(byteArrayOf(1, 2, 3)) }
            assertArrayEquals(byteArrayOf(1, 2, 3), context.contentResolver.openInputStream(first)!!.use { it.readBytes() })
            val provider = context.packageManager.resolveContentProvider(first.authority!!, 0)!!
            assertFalse(provider.exported)
            assertTrue(provider.grantUriPermissions)
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, first.authority!!, File(context.filesDir, "private.txt"))
            }
        } finally {
            context.contentResolver.delete(first, null, null)
            context.contentResolver.delete(second, null, null)
        }
    }

    @Test fun cameraLaunchesImageCaptureWithOutputAndTemporaryUriGrants() {
        showCapture()
        compose.onNodeWithTag("take_photo_button").performClick()
        compose.runOnIdle {
            val intent = registry.intent!!
            assertEquals(1, registry.cameras)
            assertEquals(0, registry.galleries)
            assertEquals(MediaStore.ACTION_IMAGE_CAPTURE, intent.action)
            assertEquals(registry.uri, intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java))
            assertEquals(registry.uri, intent.clipData!!.getItemAt(0).uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        }
        compose.onNodeWithTag("take_photo_button").assertIsNotEnabled()
        compose.onNodeWithTag("gallery_button").assertIsNotEnabled()
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, Activity.RESULT_CANCELED, null) }
        compose.onNodeWithTag("take_photo_button").assertIsEnabled()
        assertTrue(captured.isEmpty())
        assertThrows(java.io.FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(registry.uri!!)
        }
    }

    @Test fun galleryUsesOnlyPhotoPicker() {
        showCapture()
        compose.onNodeWithTag("gallery_button").performClick()
        compose.runOnIdle {
            assertEquals(1, registry.galleries)
            assertEquals(0, registry.cameras)
        }
    }

    @Test fun securityFailureShowsErrorAndLogsExceptionWithoutOpeningGallery() {
        val failure = SecurityException("Synthetic camera launch denial")
        registry.failure = failure
        showCapture()
        compose.onNodeWithTag("take_photo_button").performClick()
        compose.onNodeWithTag("capture_error")
            .assertTextEquals(context.getString(R.string.camera_access_blocked)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, registry.galleries)
            assertTrue(captured.isEmpty())
            assertTrue(ShadowLog.getLogsForTag("ReceiptCapture").any { it.throwable === failure })
        }
        compose.onNodeWithTag("take_photo_button").assertIsEnabled()
        assertThrows(java.io.FileNotFoundException::class.java) {
            context.contentResolver.openInputStream(registry.uri!!)
        }
    }

    @Test fun missingCameraShowsActionableErrorWithoutOpeningGallery() {
        registry.failure = ActivityNotFoundException("Synthetic missing camera app")
        showCapture()
        compose.onNodeWithTag("take_photo_button").performClick()
        compose.onNodeWithTag("capture_error").assertTextEquals(context.getString(R.string.camera_unavailable))
        assertEquals(0, registry.galleries)
    }

    @Test fun emptySuccessfulOutputIsReportedInsteadOfScanned() {
        showCapture()
        compose.onNodeWithTag("take_photo_button").performClick()
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, Activity.RESULT_OK, null) }
        compose.onNodeWithTag("capture_error").assertTextEquals(context.getString(R.string.camera_no_photo))
        assertTrue(captured.isEmpty())
    }

    @Test fun pendingOutputSurvivesSavedStateRestorationAndDeliversOriginalUri() {
        val restoration = showCapture()
        compose.onNodeWithTag("take_photo_button").performClick()
        val uri = registry.uri!!
        context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("take_photo_button").assertIsNotEnabled()
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, Activity.RESULT_OK, null) }
        compose.runOnIdle { assertEquals(listOf(uri), captured) }
        context.contentResolver.delete(uri, null, null)
    }

    @Test fun cameraAndGalleryPreserveApiKeySetupGate() {
        var settingsOpened = 0
        showCapture(hasKey = false, onSettings = { settingsOpened++ })
        compose.onNodeWithTag("take_photo_button").performClick()
        compose.onNodeWithTag("gallery_button").performClick()
        compose.runOnIdle {
            assertEquals(2, settingsOpened)
            assertEquals(0, registry.cameras)
            assertEquals(0, registry.galleries)
        }
    }
}
