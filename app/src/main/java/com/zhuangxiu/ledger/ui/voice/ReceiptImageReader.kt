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
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal class ReceiptImageReader @Inject constructor(@ApplicationContext context: Context) {
    private val decoder = ReceiptImageDecoder(context)
    private val tesseract = TesseractReceiptReader(context)

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()
        val (bitmap, rotation) = decoder.decode(uri, job::ensureActive)
        try {
            recognizeReceiptText(
                primary = { receiptLines(recognize(bitmap, rotation)) },
                fallback = {
                    job.ensureActive()
                    tesseract.read(bitmap, rotation)
                },
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognize(bitmap: Bitmap, rotation: Int): Text {
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val task = recognizer.process(InputImage.fromBitmap(bitmap, rotation))
            try {
                return task.await()
            } catch (cancelled: CancellationException) {
                if (currentCoroutineContext().isActive) throw IOException("主识别任务提前结束")
                // The task can outlive a dismissed review and still use its bitmap.
                withContext(NonCancellable) { runCatching { task.await() } }
                throw cancelled
            }
        } finally {
            recognizer.close()
        }
    }

    private fun receiptLines(result: Text): String {
        val lines = result.textBlocks.flatMap { it.lines }.map { line ->
            val box = line.boundingBox ?: return result.text
            box to line.text
        }
        return if (lines.isEmpty()) result.text else formatReceiptLines(lines)
    }
}

internal suspend fun recognizeReceiptText(primary: suspend () -> String, fallback: suspend () -> String): String {
    val first = attemptReceiptOcr(primary)
    first.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    return attemptReceiptOcr(fallback).getOrElse { backupError ->
        val primaryError = first.exceptionOrNull()
        val primaryDetail = when (primaryError) {
            null -> "无文字"
            is MlKitException -> "OCR ${primaryError.errorCode}"
            else -> primaryError.javaClass.simpleName
        }
        val backupDetail = (backupError as? ReceiptImageException)?.message ?: backupError.javaClass.simpleName
        throw ReceiptImageException("本机文字识别失败（主识别 $primaryDetail，备用识别 $backupDetail）")
    }
}

private suspend fun attemptReceiptOcr(read: suspend () -> String): Result<String> = try {
    Result.success(read())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
} catch (error: LinkageError) {
    Result.failure(error)
}

internal fun formatReceiptLines(input: List<Pair<Rect, String>>): String {
    if (input.isEmpty()) return ""
    val lines = input.sortedBy { (box, _) -> box.centerY() }
    val rows = mutableListOf<List<Pair<Rect, String>>>()
    var start = 0
    for (index in 1 until lines.size) {
        val anchor = lines[start].first
        val box = lines[index].first
        val overlap = minOf(anchor.bottom, box.bottom) - maxOf(anchor.top, box.top)
        if (overlap * 2 < minOf(anchor.height(), box.height())) {
            rows += lines.subList(start, index)
            start = index
        }
    }
    rows += lines.subList(start, lines.size)
    return rows.joinToString("\n") { row ->
        row.sortedBy { it.first.left }.joinToString(" ") { it.second }
    }
}
