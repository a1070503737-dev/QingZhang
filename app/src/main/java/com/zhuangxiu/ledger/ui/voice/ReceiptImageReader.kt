package com.zhuangxiu.ledger.ui.voice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal class ReceiptImageReader @Inject constructor(@ApplicationContext context: Context) {
    private val decoder = ReceiptImageDecoder(context)

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()
        val (bitmap, rotation) = decoder.decode(uri, job::ensureActive)
        try {
            receiptLines(recognize(bitmap, rotation))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: MlKitException) {
            throw ReceiptImageException("本机文字识别失败（OCR ${error.errorCode}），请重新打开轻账后再试")
        } catch (_: Exception) {
            throw ReceiptImageException("本机文字识别无法启动，请重新打开轻账后再试")
        }
    }

    private suspend fun recognize(bitmap: Bitmap, rotation: Int): Text {
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
        return task.await()
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
}
