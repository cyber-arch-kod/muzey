package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressedImageResult(
    val filePath: String,
    val width: Int,
    val height: Int
)

class ImageCompressor(private val context: Context) {

    private val cacheDir: File
        get() = File(context.filesDir, "museums_cache").apply {
            if (!exists()) mkdirs()
        }

    suspend fun compressToWebP(uri: Uri, maxSidePx: Int = 1600, quality: Int = 80): Result<CompressedImageResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver

                // 1. Read EXIF orientation
                val orientation = resolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL

                // 2. Decode bounds first to compute safe inSampleSize
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }

                val rawW = bounds.outWidth
                val rawH = bounds.outHeight
                if (rawW <= 0 || rawH <= 0) {
                    throw IllegalArgumentException("Rasm o'lchamini aniqlab bo'lmadi")
                }

                var sampleSize = 1
                while (max(rawW / sampleSize, rawH / sampleSize) > maxSidePx * 2) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val sampledBitmap = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: throw IllegalStateException("Rasmni ochib bo'lmadi")

                // 3. Apply EXIF orientation + scale to maxSidePx
                val rotatedBitmap = applyExifMatrix(sampledBitmap, orientation)
                if (rotatedBitmap !== sampledBitmap) {
                    sampledBitmap.recycle()
                }

                val finalBitmap = scaleDownIfNeeded(rotatedBitmap, maxSidePx)
                if (finalBitmap !== rotatedBitmap) {
                    rotatedBitmap.recycle()
                }

                val outFile = File(cacheDir, "${UUID.randomUUID()}.webp")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }

                FileOutputStream(outFile).use { fos ->
                    finalBitmap.compress(format, quality, fos)
                }

                val finalW = finalBitmap.width
                val finalH = finalBitmap.height
                finalBitmap.recycle()

                CompressedImageResult(
                    filePath = outFile.absolutePath,
                    width = finalW,
                    height = finalH
                )
            }
        }

    private fun applyExifMatrix(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxSidePx: Int): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim <= maxSidePx) return bitmap
        val ratio = maxSidePx.toFloat() / maxDim.toFloat()
        val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
