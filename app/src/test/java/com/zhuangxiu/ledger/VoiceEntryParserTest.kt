package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.domain.ChineseVoiceParser
import com.zhuangxiu.ledger.domain.parseEntryTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceEntryParserTest {
    private val today = LocalDate.of(2026, 11, 23)

    @Test fun multipleMealsShareSpokenDateAndUseCentsWithoutRounding() {
        val drafts = ChineseVoiceParser.parse("昨天早餐 8 元，午饭二十五元，晚饭 １２.５元", today)
        assertEquals(listOf("8", "25", "12.5"), drafts.map { it.amount })
        assertEquals(listOf("早餐", "午饭", "晚饭"), drafts.map { it.note })
        assertEquals(List(3) { "2026-11-22" }, drafts.map { it.date })
        assertEquals(List(3) { EntryKind.EXPENSE }, drafts.map { it.kind })
        assertEquals(List(3) { DailyCategory.FOOD }, drafts.map { it.category })
    }

    @Test fun incomeAndAbsoluteDate() {
        val draft = ChineseVoiceParser.parse("2026年11月3日收到工资 8,000.25 元", today).single()
        assertEquals("8000.25", draft.amount)
        assertEquals("2026-11-03", draft.date)
        assertEquals(DailyCategory.SALARY, draft.category)
        assertEquals(EntryKind.INCOME, draft.kind)
    }

    @Test fun ambiguousDetailsStayIncomplete() {
        assertNull(ChineseVoiceParser.parse("十八元", today).single().kind)
        assertEquals("", ChineseVoiceParser.parse("早餐8元25元", today).single().amount)
        assertEquals("", ChineseVoiceParser.parse("早餐8元午饭25元合计33元", today).single().amount)
        assertEquals("", ChineseVoiceParser.parse("2月30日早餐8元", today).single().date)
        assertEquals("", ChineseVoiceParser.parse("公交-8元", today).single().amount)
        assertEquals("", ChineseVoiceParser.parse("打车一百二元", today).single().amount)
    }

    @Test fun colloquialAmountsUseExactCents() {
        val drafts = ChineseVoiceParser.parse("早餐八块五午饭12块5毛3分，然后公交五毛", today)
        assertEquals(listOf("8.5", "12.53", "0.5"), drafts.map { it.amount })
        assertEquals(listOf(DailyCategory.FOOD, DailyCategory.FOOD, DailyCategory.TRANSPORT), drafts.map { it.category })
        val implicit = ChineseVoiceParser.parse("早饭花了二十中饭三十", today)
        assertEquals(listOf("20", "30"), implicit.map { it.amount })
        assertEquals(listOf(DailyCategory.FOOD, DailyCategory.FOOD), implicit.map { it.category })
        assertEquals("3.5", ChineseVoiceParser.parse("地铁三块半", today).single().amount)
    }

    @Test fun approximateAmountsRemainEditableAndDoNotHideTheNextItem() {
        for (text in listOf("早餐大概二三十元", "打车四十多块", "外卖二三元", "公交5块到8", "公交负八块五", "早餐十块多")) {
            assertEquals(text, "", ChineseVoiceParser.parse(text, today).single().amount)
        }
        val drafts = ChineseVoiceParser.parse("午饭25元左右公交3元", today)
        assertEquals(listOf("", "3"), drafts.map { it.amount })
        assertEquals(listOf("午饭25元左右", "公交"), drafts.map { it.note })
    }

    @Test fun offlineTokensCanSeparateMealsWithoutPunctuation() {
        val drafts = ChineseVoiceParser.parse("早 餐 八 元 午 饭 二 十 五 元", today)
        assertEquals(listOf("8", "25"), drafts.map { it.amount })
        assertEquals(listOf("早餐", "午饭"), drafts.map { it.note })
    }

    @Test fun infersCategoriesFromOrdinarySpeechWithoutCategoryNames() {
        val drafts = ChineseVoiceParser.parse("我昨晚吃麻辣拌六十八元买露营灯199元", today)
        assertEquals(listOf(DailyCategory.FOOD, DailyCategory.SHOPPING), drafts.map { it.category })
        assertEquals(listOf("68", "199"), drafts.map { it.amount })
        assertEquals(List(2) { EntryKind.EXPENSE }, drafts.map { it.kind })
        assertEquals("2026-11-22", drafts.first().date)
    }

    @Test fun specificPurchasesKeepTheirOwnCategory() {
        val drafts = ChineseVoiceParser.parse("买了杯咖啡18元，买了张地铁票三块五，买了药30元，买了电影票39元", today)
        assertEquals(listOf(DailyCategory.FOOD, DailyCategory.TRANSPORT, DailyCategory.HEALTH, DailyCategory.FUN),
            drafts.map { it.category })
        assertEquals(listOf("18", "3.5", "30", "39"), drafts.map { it.amount })
        assertEquals(DailyCategory.SHOPPING, ChineseVoiceParser.parse("买了台咖啡机699元", today).single().category)
        assertEquals(DailyCategory.REFUND, ChineseVoiceParser.parse("收到超市退款30元", today).single().category)
    }

    @Test fun missingAmountsDoNotUseQuantitiesOrProductModelNumbers() {
        val meals = ChineseVoiceParser.parse("今晚吃了2份炸鸡", today).single()
        assertEquals(DailyCategory.FOOD, meals.category)
        assertEquals("", meals.amount)
        val shopping = ChineseVoiceParser.parse("买了小米15", today).single()
        assertEquals(DailyCategory.SHOPPING, shopping.category)
        assertEquals("", shopping.amount)
        assertEquals("299", ChineseVoiceParser.parse("买了2个耳机299元", today).single().amount)
        assertEquals("40", ChineseVoiceParser.parse("花了40买了个杯子", today).single().amount)
    }

    @Test fun plansAndNegatedSpendingNeedManualConfirmation() {
        for (phrase in listOf("我想买个耳机200元", "今晚没有吃烧烤68元")) {
            val draft = ChineseVoiceParser.parse(phrase, today).single()
            assertNull(draft.kind)
            assertNull(draft.category)
            assertEquals("", draft.amount)
        }
    }

    @Test fun distinguishesPiecesFromYuanInLocalMode() {
        for (phrase in listOf("今晚吃了三块蛋糕", "买了3块瓷砖")) {
            assertEquals(phrase, "", ChineseVoiceParser.parse(phrase, today).single().amount)
        }
        val payment = ChineseVoiceParser.parse("今晚吃了三块蛋糕花了25元", today).single()
        assertEquals("25", payment.amount)
        assertEquals(DailyCategory.FOOD, payment.category)
        assertEquals("3", ChineseVoiceParser.parse("地铁三块钱", today).single().amount)
    }

    @Test fun assignsPaymentToEachTransactionEvenWithoutPunctuation() {
        val drafts = ChineseVoiceParser.parse("早餐8块微信支付午饭25元支付宝付的，刷信用卡买了电影票39元，打车15元付现金", today)
        assertEquals(listOf("8", "25", "39", "15"), drafts.map { it.amount })
        assertEquals(listOf(PayMethod.WECHAT, PayMethod.ALIPAY, PayMethod.BANK, PayMethod.CASH), drafts.map { it.payMethod })
        val income = ChineseVoiceParser.parse("支付宝收到退款18元", today).single()
        assertEquals(EntryKind.INCOME, income.kind)
        assertEquals(PayMethod.ALIPAY, income.payMethod)
    }

    @Test fun missingAmbiguousAndUnrelatedPaymentMethodsStayUnspecified() {
        for (phrase in listOf("早餐8元", "买了微信会员20元", "吃饭30元用微信还是支付宝", "微信还是支付宝付了30元")) {
            assertEquals(phrase, PayMethod.UNSPECIFIED, ChineseVoiceParser.parse(phrase, today).single().payMethod)
        }
        assertEquals("", ChineseVoiceParser.parse("用微信买了小米15", today).single().amount)
    }

    @Test fun retainsDatesAndTimeForEachEntryAndIgnoresUnstatedTimes() {
        val drafts = ChineseVoiceParser.parse("昨晚八点半，吃饭25元，用微信付的，今天18：05打车30元，买了奶茶8元", today)
        assertEquals(listOf("2026-11-22", "2026-11-23", "2026-11-23"), drafts.map { it.date })
        assertEquals(listOf("20:30", "18:05", ""), drafts.map { it.time })
        assertEquals(listOf("25", "30", "8"), drafts.map { it.amount })
        assertEquals(PayMethod.WECHAT, drafts.first().payMethod)
        val trailing = ChineseVoiceParser.parse("打车30元，下午两点，现金", today).single()
        assertEquals("14:00", trailing.time)
        assertEquals(PayMethod.CASH, trailing.payMethod)
    }

    @Test fun separatesSpokenMoneyFromClockTimeAndLeavesInvalidClockEditable() {
        val fare = ChineseVoiceParser.parse("下午三点一刻地铁六点五元", today).single()
        assertEquals("15:15", fare.time)
        assertEquals("6.5", fare.amount)
        val invalid = ChineseVoiceParser.parse("25:67吃饭25元", today).single()
        assertEquals("25:67", invalid.time)
        assertNull(parseEntryTime(invalid.time))
        assertEquals("25", invalid.amount)
    }
}
