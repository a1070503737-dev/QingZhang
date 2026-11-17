package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.domain.DailyStatistics
import com.zhuangxiu.ledger.ui.daily.DailyEditState
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DailyStatisticsTest {
    private fun row(id: String, amount: Long, date: String, category: DailyCategory = DailyCategory.FOOD, book: Long = 1) =
        DailyEntryEntity(id, book, amount, category.kind, category, LocalDate.parse(date).toEpochDay(), 720, PayMethod.CASH)

    @Test fun centsRefundsAndBookIsolationUseOneRule() {
        val result = DailyStatistics.calculate(listOf(row("a", 101, "2026-11-01"), row("b", 202, "2026-11-02"),
            row("c", 10000, "2026-11-02", DailyCategory.SALARY), row("d", 101, "2026-11-03", DailyCategory.REFUND),
            row("other", 99999, "2026-11-01", book = 3)), 1, YearMonth.of(2026, 11))
        assertEquals(10101L, result.totals.income); assertEquals(303L, result.totals.expense)
        assertEquals(9798L, result.totals.balance)
        assertEquals(listOf(DailyCategory.FOOD to 303L), result.categories)
        assertEquals(4, result.entries.size)
        assertEquals(result.totals, result.months.last().totals)
    }
    @Test fun yearBoundaryLeapDayAndSixMonthsIncludeZeroMonths() {
        val result = DailyStatistics.calculate(listOf(row("a", 100, "2023-08-31"), row("b", 200, "2023-09-01"),
            row("c", 300, "2024-02-29"), row("d", 999, "2024-03-01")), 1, YearMonth.of(2024, 2))
        assertEquals(listOf("2023-09", "2023-10", "2023-11", "2023-12", "2024-01", "2024-02"), result.months.map { it.month.toString() })
        assertEquals(listOf(200L, 0L, 0L, 0L, 0L, 300L), result.months.map { it.totals.expense })
        assertEquals(300L, result.totals.expense)
    }
    @Test fun rankingAndEmptyMonthsHaveNoInventedAmounts() {
        val result = DailyStatistics.calculate(listOf(row("a", 200, "2026-11-01"), row("b", 300, "2026-11-01", DailyCategory.SHOPPING)), 1, YearMonth.of(2026, 11))
        assertEquals(listOf(DailyCategory.SHOPPING, DailyCategory.FOOD), result.categories.map { it.first })
        val empty = DailyStatistics.calculate(emptyList(), 1, YearMonth.of(2026, 11))
        assertEquals(0L, empty.totals.balance); assertTrue(empty.categories.isEmpty()); assertEquals(6, empty.months.size)
    }
    @Test fun illegalAmountsCannotSave() {
        listOf("", "0", "0.00", "-1", "1.234", "1e2", "NaN", "1000000000", "1,000", "abc").forEach { assertFalse(it, DailyEditState(amount = it).valid) }
        listOf("0.01", "25", "25.", "999999999.99").forEach { assertTrue(it, DailyEditState(amount = it).valid) }
    }
}
