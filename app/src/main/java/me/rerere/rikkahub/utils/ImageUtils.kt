@file:Suppress("unused")

package me.rerere.rikkahub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import com.drew.imaging.ImageMetadataReader
import com.drew.imaging.png.PngChunkType
import com.drew.metadata.png.PngDirectory
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Image processing utilities
 * Provides image compression, rotation correction, QR code parsing, etc.
 */
object ImageUtils {

    /**
     * Optimized image loading that avoids OOM
     * 1. Get the image dimensions first
     * 2. Compute a suitable sample size
     * 3. Load the downsampled image
     * 4. Handle image rotation
     *
     * @param context Android context
     * @param uri image URI
     * @param maxSize maximum size limit, 1024px by default
     * @return the compressed Bitmap, or null on failure
     */
    fun loadOptimizedBitmap(
        context: Context,
        uri: Uri,
        maxSize: Int = 1024
    ): Bitmap? {
        return runCatching {
            // Step 1: get the image's original dimensions without loading it into memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            // Compute a suitable sample size
            val sampleSize = calculateInSampleSize(options, maxSize, maxSize)

            // Step 2: load the downsampled image using the sample size
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Use RGB_565 to reduce memory usage
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, loadOptions)
            }

            // Step 3: handle image rotation (if needed)
            bitmap?.let { correctImageOrientation(context, uri, it) }
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    /**
     * Compute a suitable sample size
     *
     * @param options BitmapFactory.Options containing the original image dimensions
     * @param reqWidth target width
     * @param reqHeight target height
     * @return the sample size (a power of 2)
     */
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Compute the largest inSampleSize that is a power of 2 and keeps both height and width larger than the requested height and width
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Correct image rotation
     * Automatically rotates the image to the correct orientation based on its EXIF info
     *
     * @param context Android context
     * @param uri image URI
     * @param bitmap the original bitmap
     * @return the rotated bitmap
     */
    fun correctImageOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            inputStream?.close()

            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap // No rotation needed
            }

            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle() // Recycle the original bitmap
            }
            rotatedBitmap
        }.onFailure {
            it.printStackTrace()
        }.getOrDefault(bitmap)
    }

    /**
     * Whether the image the uri points to is HEIF/HEIC
     * Prefers the ContentResolver's MIME type, falling back to sniffing the file header's magic number
     */
    fun isHeifImage(context: Context, uri: Uri): Boolean {
        context.contentResolver.getType(uri)?.lowercase()?.let { mime ->
            if (mime.contains("heic") || mime.contains("heif")) return true
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 12) return@use false
                if (header.copyOfRange(4, 8).toString(Charsets.US_ASCII) != "ftyp") return@use false
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) in HEIF_BRANDS
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * Decode a HEIF/HEIC image and re-encode it as JPEG into [target]
     * Used to work around poor HEIF (especially HDR HEIF) decoding compatibility in some components (such as UCrop)
     *
     * @return true on success, false on failure (e.g. the system can't decode HEIF)
     */
    fun convertHeifToJpeg(
        context: Context,
        uri: Uri,
        target: File,
        maxSize: Int = 4096,
        quality: Int = 95,
    ): Boolean = runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOptions)
        }
        val loadOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions, maxSize, maxSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, loadOptions)
        } ?: return@runCatching false
        // Bake the EXIF rotation into the pixels, so the output JPEG has no orientation info and isn't rotated again downstream
        val oriented = correctImageOrientation(context, uri, decoded)
        try {
            target.outputStream().use { output ->
                oriented.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            true
        } finally {
            recycleBitmapSafely(oriented)
        }
    }.onFailure {
        it.printStackTrace()
    }.getOrDefault(false)

    private val HEIF_BRANDS = setOf(
        "heic", "heix", "heim", "heis",
        "hevc", "hevx", "hevm", "hevs",
        "mif1", "msf1", "heif",
    )

    /**
     * Parse a QR code from an image
     *
     * @param bitmap the image to parse
     * @return the QR code content, or null if parsing fails
     */
    fun decodeQRCodeFromBitmap(bitmap: Bitmap): String? {
        return runCatching {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)

            result.text
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    /**
     * Load an image from a URI and parse its QR code (combined method)
     *
     * @param context Android context
     * @param uri image URI
     * @param maxSize maximum size limit, 1024px by default
     * @return the QR code content, or null if parsing fails
     */
    fun decodeQRCodeFromUri(
        context: Context,
        uri: Uri,
        maxSize: Int = 1024
    ): String? {
        val bitmap = loadOptimizedBitmap(context, uri, maxSize) ?: return null
        return try {
            decodeQRCodeFromBitmap(bitmap)
        } finally {
            bitmap.recycle() // Make sure the memory is released
        }
    }

    /**
     * Safely recycle a Bitmap's memory
     *
     * @param bitmap the bitmap to recycle
     */
    fun recycleBitmapSafely(bitmap: Bitmap?) {
        bitmap?.let {
            if (!it.isRecycled) {
                it.recycle()
            }
        }
    }

    /**
     * Get basic image info (without loading it into memory)
     *
     * @param context Android context
     * @param uri image URI
     * @return ImageInfo containing the width, height, MIME type, etc.
     */
    fun getImageInfo(context: Context, uri: Uri): ImageInfo? {
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            if (options.outWidth > 0 && options.outHeight > 0) {
                ImageInfo(
                    width = options.outWidth,
                    height = options.outHeight,
                    mimeType = options.outMimeType
                )
            } else null
        }.onFailure {
            it.printStackTrace()
        }.getOrNull()
    }

    /**
     * Get the character metadata from a SillyTavern character card (if present)
     *
     * @param context Android context
     * @param uri image URI
     * @return a Result<String> containing the character metadata
     */
    fun getTavernCharacterMeta(context: Context, uri: Uri): Result<String> = runCatching {
        val metadata = context.contentResolver.openInputStream(uri)?.use { ImageMetadataReader.readMetadata(it) }
        if (metadata == null) error("Metadata is null, please check if the image is a character card")
        if (!metadata.containsDirectoryOfType(PngDirectory::class.java)) error("No PNG directory found, please check if the image is a character card")

        val pngDirectory = metadata.getDirectoriesOfType(PngDirectory::class.java)
            .firstOrNull { directory ->
                directory.pngChunkType == PngChunkType.tEXt
                    && directory.getString(PngDirectory.TAG_TEXTUAL_DATA).startsWith("[chara:")
            } ?: error("No tEXt chunk found, please check if the image is a character card")

        val value = pngDirectory.getString(PngDirectory.TAG_TEXTUAL_DATA)

        val regex = Regex("""\[chara:\s*(.+?)]""")
        return Result.success(regex.find(value)?.groupValues?.get(1) ?: error("No character data found"))
    }

    data class ImageInfo(
        val width: Int,
        val height: Int,
        val mimeType: String?
    )
}
