package com.zhuangxiu.ledger.domain

import com.zhuangxiu.ledger.data.db.*
import java.time.YearMonth

data class DailyTotals(val income: Long = 0, val expense: Long = 0) {
    val balance: Long get() = Math.subtractExact(income, expense)
}
data class DailyMonth(val month: YearMonth, val totals: DailyTotals)
data class DailySummary(
    val totals: DailyTotals,
    val categories: List<Pair<DailyCategory, Long>>,
    val months: List<DailyMonth>,
    val entries: List<DailyEntryEntity>,
)

/** 唯一统计口径：正数分 + 收支类型。退款作为收入一次计入，不抵减原支出，也不涉及装修付款。 */
object DailyStatistics {
    fun calculate(records: List<DailyEntryEntity>, bookId: Long, month: YearMonth): DailySummary {
        val all = records.filter { it.bookId == bookId }
        fun inMonth(m: YearMonth) = all.filter { it.day >= m.atDay(1).toEpochDay() && it.day < m.plusMonths(1).atDay(1).toEpochDay() }
        fun totals(rows: List<DailyEntryEntity>): DailyTotals = rows.fold(DailyTotals()) { acc, row ->
            require(row.amountCents > 0)
            if (row.kind == EntryKind.INCOME) acc.copy(income = Math.addExact(acc.income, row.amountCents))
            else acc.copy(expense = Math.addExact(acc.expense, row.amountCents))
        }
        val selected = inMonth(month).sortedWith(compareByDescending<DailyEntryEntity> { it.day }.thenByDescending { it.minute }.thenByDescending { it.id })
        val categories = selected.filter { it.kind == EntryKind.EXPENSE }.groupBy { it.category }
            .map { (category, rows) -> category to rows.fold(0L) { sum, row -> Math.addExact(sum, row.amountCents) } }
            .sortedByDescending { it.second }
        return DailySummary(totals(selected), categories, (5 downTo 0).map { offset ->
            val m = month.minusMonths(offset.toLong()); DailyMonth(m, totals(inMonth(m)))
        }, selected)
    }
}
