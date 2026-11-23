package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.assistant.assistantPeriod
import com.zhuangxiu.ledger.assistant.assistantRequestKey
import com.zhuangxiu.ledger.assistant.resolveAssistantBook
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AssistantInputTest {
    @Test fun retriesUseTheSameIdButRejectMalformedKeys() {
        assertEquals(assistantRequestKey("rule-test-123"), assistantRequestKey("rule-test-123"))
        assertNotEquals(assistantRequestKey("rule-test-123"), assistantRequestKey("rule-test-124"))
        assertThrows(IllegalArgumentException::class.java) { assistantRequestKey("short") }
        assertThrows(IllegalArgumentException::class.java) { assistantRequestKey("no/such/key") }
        assertEquals(1L, resolveAssistantBook(0))
        assertThrows(IllegalArgumentException::class.java) { resolveAssistantBook(-1) }
    }

    @Test fun exactDayAndMonthAreValidatedBeforeOpeningTheLedger() {
        val current = YearMonth.of(2026, 11)
        assertEquals(current to null, assistantPeriod(null, null, current))
        assertEquals(YearMonth.of(2026, 10) to LocalDate.of(2026, 10, 13), assistantPeriod(null, "2026-10-13", current))
        assertThrows(IllegalArgumentException::class.java) { assistantPeriod("2026-10", "2026-11-03", current) }
        assertThrows(IllegalArgumentException::class.java) { assistantPeriod(null, "2026-02-30", current) }
        assertThrows(IllegalArgumentException::class.java) { assistantPeriod("2026-00", null, current) }
    }
}
