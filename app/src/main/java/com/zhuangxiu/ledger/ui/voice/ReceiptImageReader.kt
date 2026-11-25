package com.zhuangxiu.ledger.ui.voice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal class ReceiptImageReader @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val (bitmap, rotation) = decode(uri)
        val recognizer = try {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
        val task = try {
            recognizer.process(InputImage.fromBitmap(bitmap, rotation))
        } catch (error: Throwable) {
            recognizer.close()
            bitmap.recycle()
            throw error
        }
        // The recognition task can outlive a cancelled review. Keep its input alive until it finishes.
        task.addOnCompleteListener {
            recognizer.close()
            bitmap.recycle()
        }
        receiptLines(task.await())
    }

    private fun receiptLines(result: Text): String {
        val lines = result.textBlocks.flatMap { it.lines }.map { line ->
            val box = line.boundingBox ?: return result.text
            box to line.text
        }.sortedBy { (box, _) -> box.centerY() }
        if (lines.isEmpty()) return result.text
        val rows = mutableListOf<List<Pair<Rect, String>>>()
        var start = 0
        for (index in 1 until lines.size) {
            val anchor = lines[start].first
            val box = lines[index].first
            if (abs(anchor.centerY() - box.centerY()) * 2 > minOf(anchor.height(), box.height())) {
                rows += lines.subList(start, index)
                start = index
            }
        }
        rows += lines.subList(start, lines.size)
        return rows.joinToString("\n") { row ->
            row.sortedBy { it.first.left }.joinToString(" ") { it.second }
        }
    }

    private fun decode(uri: Uri): Pair<Bitmap, Int> {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height))
            }
            return bitmap to 0
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val rotation = try {
            resolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: IOException) {
            0
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("图片无法打开")
        return bitmap to rotation
    }

    private fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) throw IOException("图片尺寸无效")
        var sample = 1
        while (maxOf(width, height) / sample > 3600) sample *= 2
        return sample
    }
}
