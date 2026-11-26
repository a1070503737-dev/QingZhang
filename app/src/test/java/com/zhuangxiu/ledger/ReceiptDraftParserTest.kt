package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.domain.ReceiptDraftParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptDraftParserTest {
    private val today = LocalDate.of(2026, 11, 25)

    @Test fun recognizesAChinesePaymentScreenshotWithSplitLabels() {
        val draft = requireNotNull(ReceiptDraftParser.parse("""
            微信支付
            付款成功
            -￥25.68
            商品说明
            午餐
            商户名称
            星河餐饮
            付款时间
            2026-11-24 18:35:07
            交易单号
            420000254122342128
        """.trimIndent(), today))
        assertEquals("25.68", draft.amount)
        assertEquals(EntryKind.EXPENSE, draft.kind)
        assertEquals(DailyCategory.FOOD, draft.category)
        assertEquals("2026-11-24", draft.date)
        assertEquals("18:35", draft.time)
        assertEquals(PayMethod.WECHAT, draft.payMethod)
        assertEquals("午餐 · 星河餐饮", draft.note)
    }

    @Test fun prioritizesTheActualDebitAndExplicitPaymentMethod() {
        val draft = requireNotNull(ReceiptDraftParser.parse("""
            09:41
            微信支付
            商家名称：烘焙店
            商品：面包
            合计：￥31.00
            优惠：￥6.00
            实付：￥25.00
            支付方式：招商银行信用卡(1234)
            找零：0.00
        """.trimIndent(), today))
        assertEquals("25", draft.amount)
        assertEquals(PayMethod.BANK, draft.payMethod)
        assertEquals("2026-11-25", draft.date)
        assertEquals("", draft.time)
    }

    @Test fun keepsFailedAndAmbiguousPaymentsUnconfirmed() {
        val failed = requireNotNull(ReceiptDraftParser.parse("交易状态：支付失败\n支付金额：100.00", today))
        assertEquals("", failed.amount)
        assertNull(failed.kind)
        val ambiguous = requireNotNull(ReceiptDraftParser.parse("支付成功\n¥10.00\n¥20.00\n订单号：1234567", today))
        assertEquals("", ambiguous.amount)
        val unknown = requireNotNull(ReceiptDraftParser.parse("订单号：1234567\n运单号\n98765432", today))
        assertEquals("", unknown.amount)
        assertNull(unknown.kind)
    }

    @Test fun recognizesTheRefundAmountInsteadOfTheOriginalCharge() {
        val draft = requireNotNull(ReceiptDraftParser.parse("""
            退款成功
            退款金额：+￥18.50
            交易金额：￥38.50
            退款时间：2026年11月3日 9:07
            收款方式：余额宝
        """.trimIndent(), today))
        assertEquals(EntryKind.INCOME, draft.kind)
        assertEquals(DailyCategory.REFUND, draft.category)
        assertEquals("18.5", draft.amount)
        assertEquals("2026-11-03", draft.date)
        assertEquals("09:07", draft.time)
        assertEquals(PayMethod.ALIPAY, draft.payMethod)
    }

    @Test fun findsFullTimestampWhenOcrPlacesItBeforeItsLabel() {
        val draft = requireNotNull(ReceiptDraftParser.parse("""
            09:41
            微 信 支 付
            -¥25.68
            支付 成 功
            商品 说 明 午餐
            商户 名 称 星河 餐饮
            2026-11-24 18:35:07
            付款 时 间
            付款 方式
        """.trimIndent(), today))
        assertEquals("25.68", draft.amount)
        assertEquals("2026-11-24", draft.date)
        assertEquals("18:35", draft.time)
        assertEquals(PayMethod.WECHAT, draft.payMethod)
    }
}
