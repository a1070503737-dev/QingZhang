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
        assertEquals("", ChineseVoiceParser.parse("早餐8块5", today).single().amount)
        assertEquals("", ChineseVoiceParser.parse("早餐8元25元", today).single().amount)
        assertEquals("", ChineseVoiceParser.parse("早餐8元午饭25元合计33元", today).single().amount)
        assertEquals("", ChineseVoiceParser.parse("2月30日早餐8元", today).single().date)
        assertEquals("", ChineseVoiceParser.parse("公交-8元", today).single().amount)
        assertEquals("", ChineseVoiceParser.parse("打车一百二元", today).single().amount)
    }

    @Test fun offlineTokensCanSeparateMealsWithoutPunctuation() {
        val drafts = ChineseVoiceParser.parse("早 餐 八 元 午 饭 二 十 五 元", today)
        assertEquals(listOf("8", "25"), drafts.map { it.amount })
        assertEquals(listOf("早餐", "午饭"), drafts.map { it.note })
    }
}
