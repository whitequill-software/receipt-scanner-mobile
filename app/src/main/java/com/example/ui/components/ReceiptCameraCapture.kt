package com.example.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** Keep TakePicture's full-size output contract and explicitly share it with OEM camera apps. */
internal class ReceiptCameraCapture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            clipData = ClipData.newRawUri("Receipt photo", input)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}

internal fun createReceiptCameraUri(context: Context): Uri {
    val directory = File(context.cacheDir, "receipt_images")
    if (!directory.isDirectory && !directory.mkdirs()) {
        throw IOException("Could not create receipt image directory")
    }
    // Create a real, unique file before handing its content URI to another app.
    val image = File.createTempFile("RECEIPT_", ".jpg", directory)
    return try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", image)
    } catch (exception: Exception) {
        image.delete()
        throw exception
    }
}
