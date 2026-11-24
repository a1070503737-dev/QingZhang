package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.domain.ChineseVoiceParser
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
}
