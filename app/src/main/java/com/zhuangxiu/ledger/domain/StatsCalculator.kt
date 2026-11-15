package com.zhuangxiu.ledger.domain

import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import java.time.LocalDate
import java.time.YearMonth

/** 饼图的一块：某个分类的花费 */
data class CategorySlice(val categoryId: Long, val name: String, val cents: Long)

/** 柱状图的一根柱子：某个月的花费 */
data class MonthAmount(val month: YearMonth, val cents: Long)

/** 统计图表的数据计算（纯函数） */
object StatsCalculator {

    /** 按日期范围筛选（闭区间，epochDay）；传 null 表示不限 */
    fun filterByRange(expenses: List<ExpenseWithDetails>, from: Long?, to: Long?): List<ExpenseWithDetails> =
        expenses.filter { item ->
            (from == null || item.expense.date >= from) && (to == null || item.expense.date <= to)
        }

    /** 按阶段或类型汇总花费，金额从大到小排列，去掉金额为 0 的分类 */
    fun byCategory(expenses: List<ExpenseWithDetails>, kind: CategoryKind): List<CategorySlice> =
        expenses
            .groupBy { if (kind == CategoryKind.STAGE) it.stage else it.type }
            .map { (category, list) -> CategorySlice(category.id, category.name, list.sumOf { it.expense.totalCents }) }
            .filter { it.cents > 0 }
            .sortedByDescending { it.cents }

    /** 按月汇总花费；从最早的月份到最晚的月份连续排列，中间没有花费的月份补 0 */
    fun byMonth(expenses: List<ExpenseWithDetails>): List<MonthAmount> {
        if (expenses.isEmpty()) return emptyList()
        val sums = expenses
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.expense.date)) }
            .mapValues { (_, list) -> list.sumOf { it.expense.totalCents } }
        val first = sums.keys.min()
        val last = sums.keys.max()
        val result = ArrayList<MonthAmount>()
        var month = first
        while (!month.isAfter(last)) {
            result += MonthAmount(month, sums[month] ?: 0)
            month = month.plusMonths(1)
        }
        return result
    }
}
