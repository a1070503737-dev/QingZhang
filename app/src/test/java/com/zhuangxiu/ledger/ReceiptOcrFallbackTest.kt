package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.domain.ReceiptDraftParser
import com.zhuangxiu.ledger.ui.voice.ReceiptImageException
import com.zhuangxiu.ledger.ui.voice.recognizeReceiptText
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptOcrFallbackTest {
    @Test fun primaryStartupFailureCanStillProduceAWeChatDraft() = runTest {
        val text = recognizeReceiptText(
            primary = { throw IllegalStateException("recognizer unavailable") },
            fallback = {
                "微信支付\n-¥25.68\n支付成功\n商品说明 午餐\n付款时间 2026-11-24 18:35:07\n付款方式 零钱"
            },
        )
        val draft = requireNotNull(ReceiptDraftParser.parse(text, LocalDate.of(2026, 11, 26)))
        assertEquals("25.68", draft.amount)
        assertEquals("2026-11-24", draft.date)
        assertEquals("18:35", draft.time)
        assertEquals(PayMethod.WECHAT, draft.payMethod)
    }

    @Test fun emptyAndUnlinkedPrimaryRecognizersUseTheOfflineFallback() = runTest {
        assertEquals("备用文字", recognizeReceiptText({ "  " }, { "备用文字" }))
        assertEquals("备用文字", recognizeReceiptText({ throw UnsatisfiedLinkError() }, { "备用文字" }))
    }

    @Test fun successfulPrimaryRecognitionSkipsFallback() = runTest {
        val text = recognizeReceiptText({ "识别结果" }) { error("fallback should not run") }
        assertEquals("识别结果", text)
    }

    @Test fun cancelledRecognitionDoesNotStartFallback() = runTest {
        var called = false
        val result = runCatching {
            recognizeReceiptText({ throw CancellationException() }, { called = true; "" })
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertFalse(called)
    }

    @Test fun failureShowsBothStagesWithoutExposingInputPaths() = runTest {
        val result = runCatching {
            recognizeReceiptText(
                { throw IllegalStateException("image unavailable") },
                { throw IOException("/private/receipt.png") },
            )
        }
        val failure = result.exceptionOrNull() as ReceiptImageException
        assertTrue(failure.message.orEmpty().contains("主识别 IllegalStateException"))
        assertTrue(failure.message.orEmpty().contains("备用识别 IOException"))
        assertFalse(failure.message.orEmpty().contains("receipt.png"))
    }
}
