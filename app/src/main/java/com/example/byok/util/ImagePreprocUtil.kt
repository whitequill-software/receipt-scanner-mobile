package com.example.byok.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

object ImagePreprocUtil {

    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 85

    fun loadAndScaleBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            // Step 1: Decode image dimensions without loading full pixels into memory
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            if (originalWidth <= 0 || originalHeight <= 0) return null

            // Step 2: Compute sample size
            var sampleSize = 1
            val maxOriginal = max(originalWidth, originalHeight)
            while (maxOriginal / sampleSize > MAX_DIMENSION * 1.5) {
                sampleSize *= 2
            }

            // Step 3: Decode bitmap with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Step 4: Correct EXIF orientation if needed
            val rotationDegrees = getExifRotationDegrees(context, uri)
            val orientedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    sampledBitmap,
                    0,
                    0,
                    sampledBitmap.width,
                    sampledBitmap.height,
                    matrix,
                    true
                )
                if (rotated != sampledBitmap) sampledBitmap.recycle()
                rotated
            } else {
                sampledBitmap
            }

            // Step 5: Final bound scale if still above MAX_DIMENSION
            val width = orientedBitmap.width
            val height = orientedBitmap.height
            if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
                orientedBitmap
            } else {
                val scale = MAX_DIMENSION.toFloat() / max(width, height)
                val finalWidth = (width * scale).toInt()
                val finalHeight = (height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(orientedBitmap, finalWidth, finalHeight, true)
                if (scaled != orientedBitmap) orientedBitmap.recycle()
                scaled
            }
        } catch (_: Exception) {
            null
        }
    }

    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            stream?.use {
                val exif = ExifInterface(it)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
