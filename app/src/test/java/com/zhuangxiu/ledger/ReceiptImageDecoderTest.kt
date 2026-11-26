package com.zhuangxiu.ledger

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorSpace
import android.net.Uri
import com.zhuangxiu.ledger.ui.voice.ReceiptImageDecoder
import com.zhuangxiu.ledger.ui.voice.ReceiptImageException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptImageDecoderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val screenshot = Uri.parse("content://test.photo.picker/item/42")

    @Test fun snapshotsStreamAndNormalizesWideColorScreenshotForOcr() {
        val p3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)
        val source = Bitmap.createBitmap(144, 320, Bitmap.Config.RGBA_F16, true, p3)
        source.setPixel(72, 160, Color.BLACK)
        val png = ByteArrayOutputStream().also { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        source.recycle()
        shadowOf(context.contentResolver).registerInputStreamSupplier(screenshot) {
            ByteArrayInputStream(png.toByteArray())
        }
        val (image, rotation) = ReceiptImageDecoder(context).decode(screenshot) {}
        try {
            assertEquals(144, image.width)
            assertEquals(320, image.height)
            assertEquals(0, rotation)
            assertEquals(Bitmap.Config.ARGB_8888, image.config)
            assertTrue(image.colorSpace?.isSrgb == true)
            assertFalse(image.hasAlpha())
            assertEquals(Color.WHITE, image.getPixel(0, 0))
            assertEquals(Color.BLACK, image.getPixel(72, 160))
        } finally {
            image.recycle()
        }
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("receipt-ocr-") })
    }

    @Test fun reportsInvalidImageAsDecoderFailureAndRemovesTemporaryCopy() {
        shadowOf(context.contentResolver).registerInputStream(screenshot, ByteArrayInputStream(byteArrayOf(5, 9, 1)))
        val error = assertThrows(ReceiptImageException::class.java) {
            ReceiptImageDecoder(context).decode(screenshot) {}
        }
        assertEquals("图片格式无法解码，请试试原始 PNG 或 JPG 截图", error.message)
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("receipt-ocr-") })
    }
}
