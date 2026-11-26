package com.zhuangxiu.ledger.ui.voice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class TesseractReceiptReader(private val context: Context) {
    fun read(bitmap: Bitmap, rotation: Int): String {
        val data = prepareLanguageData()
        val image = if (rotation == 0) bitmap else Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation.toFloat()) }, true,
        )
        try {
            val api = TessBaseAPI()
            try {
                if (!api.init(data.absolutePath, "chi_sim+eng", TessBaseAPI.OEM_LSTM_ONLY)) {
                    throw ReceiptImageException("备用文字识别语言文件无法加载")
                }
                api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
                api.setVariable("user_defined_dpi", "200")
                api.setImage(image)
                val text = api.getUTF8Text().orEmpty()
                if (text.isBlank()) return text
                val iterator = api.getResultIterator() ?: return text
                val lines = mutableListOf<Pair<Rect, String>>()
                try {
                    val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
                    iterator.begin()
                    do {
                        val line = iterator.getUTF8Text(level).orEmpty().trim()
                        val bounds = iterator.getBoundingBox(level)
                        if (line.isNotEmpty() && bounds != null && bounds.size == 4) {
                            lines += Rect(bounds[0], bounds[1], bounds[2], bounds[3]) to line
                        }
                    } while (iterator.next(level))
                } finally {
                    iterator.delete()
                }
                return if (lines.isEmpty()) text else formatReceiptLines(lines)
            } finally {
                api.recycle()
            }
        } finally {
            if (image !== bitmap) image.recycle()
        }
    }

    private fun prepareLanguageData(): File = synchronized(assetLock) {
        val parent = File(context.noBackupFilesDir, "receipt-ocr-4.1.0")
        val directory = File(parent, "tessdata")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw ReceiptImageException("无法准备备用识别文件，请检查剩余空间")
        }
        for ((name, bytes) in languageFiles) {
            val destination = File(directory, name)
            if (destination.length() == bytes) continue
            val temporary = File.createTempFile(name, ".part", directory)
            try {
                context.assets.open("ocr/tessdata/$name").use { input ->
                    temporary.outputStream().use { input.copyTo(it) }
                }
                if (temporary.length() != bytes) throw ReceiptImageException("备用识别语言文件不完整")
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
        parent
    }

    private companion object {
        val assetLock = Any()
        val languageFiles = mapOf("chi_sim.traineddata" to 2_469_156L, "eng.traineddata" to 4_113_088L)
    }
}
