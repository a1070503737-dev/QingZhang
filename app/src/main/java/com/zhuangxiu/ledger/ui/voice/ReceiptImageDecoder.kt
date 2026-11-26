package com.zhuangxiu.ledger.ui.voice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class ReceiptImageException(message: String) : IOException(message)

internal class ReceiptImageDecoder(private val context: Context) {
    fun decode(uri: Uri, ensureActive: () -> Unit): Pair<Bitmap, Int> {
        val local = try {
            File.createTempFile("receipt-ocr-", ".image", context.cacheDir)
        } catch (_: IOException) {
            throw ReceiptImageException("无法暂存图片，请检查手机剩余空间后再试")
        }
        try {
            copyFromPicker(uri, local, ensureActive)
            ensureActive()
            return decodeLocal(local)
        } finally {
            local.delete()
        }
    }

    private fun copyFromPicker(uri: Uri, target: File, ensureActive: () -> Unit) {
        val resolver = context.contentResolver
        try {
            val source = try {
                resolver.openInputStream(uri)
            } catch (_: FileNotFoundException) {
                null
            } ?: resolver.openTypedAssetFileDescriptor(uri, "image/*", null)?.createInputStream()
                ?: throw FileNotFoundException()
            source.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > 64L * 1024 * 1024) throw ReceiptImageException("图片文件过大，请裁剪账单区域后重试")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (error: ReceiptImageException) {
            throw error
        } catch (_: SecurityException) {
            throw ReceiptImageException("相册没有提供图片访问权限，请重新选择图片")
        } catch (_: IOException) {
            throw ReceiptImageException("读取图片失败，请先把原图保存到手机相册再试")
        }
    }

    private fun decodeLocal(local: File): Pair<Bitmap, Int> {
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(local)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height))
                }
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        } else null
        val (bitmap, rotation) = if (decoded != null) decoded to 0 else decodeLegacy(local)
        if (minOf(bitmap.width, bitmap.height) < 32) {
            bitmap.recycle()
            throw ReceiptImageException("图片尺寸太小，请重新选择原始截图")
        }
        if (bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.colorSpace?.isSrgb == true && !bitmap.hasAlpha()) {
            return bitmap to rotation
        }
        val rgb = try {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { result ->
                Canvas(result).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
                result.setHasAlpha(false)
            }
        } finally {
            bitmap.recycle()
        }
        return rgb to rotation
    }

    private fun decodeLegacy(local: File): Pair<Bitmap, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(local.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ReceiptImageException("图片格式无法解码，请试试原始 PNG 或 JPG 截图")
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val rotation = try {
            when (ExifInterface(local.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: IOException) {
            0
        }
        val bitmap = BitmapFactory.decodeFile(local.path, options)
            ?: throw ReceiptImageException("图片内容不完整，请重新截一张账单详情图")
        return bitmap to rotation
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > 4096 || width.toLong() * height / (sample.toLong() * sample) > 8_000_000) {
            sample *= 2
        }
        return sample
    }
}
