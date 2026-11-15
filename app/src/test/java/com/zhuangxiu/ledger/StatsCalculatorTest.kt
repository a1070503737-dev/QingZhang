package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.domain.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** 统计图表的数据计算 */
class StatsCalculatorTest {

    private val expenses = listOf(
        TestData.expense(1000_00, stage = TestData.stageWater, type = TestData.typeMaterial, date = LocalDate.of(2025, 1, 15)),
        TestData.expense(400_00, stage = TestData.stageWater, type = TestData.typeLabor, date = LocalDate.of(2025, 1, 20)),
        TestData.expense(2500_00, stage = TestData.stageWood, type = TestData.typeLabor, date = LocalDate.of(2025, 4, 2)),
    )

    /** 按阶段汇总并从大到小排序 */
    @Test
    fun byStage_sortedDescending() {
        val slices = StatsCalculator.byCategory(expenses, CategoryKind.STAGE)
        assertEquals(listOf("木工" to 2500_00L, "水电" to 1400_00L), slices.map { it.name to it.cents })
    }

    /** 按类型汇总 */
    @Test
    fun byType() {
        val slices = StatsCalculator.byCategory(expenses, CategoryKind.TYPE)
        assertEquals(listOf("人工" to 2900_00L, "主材" to 1000_00L), slices.map { it.name to it.cents })
    }

    /** 按月汇总 中间没有花费的月份补0 */
    @Test
    fun byMonth_fillsEmptyMonthsWithZero() {
        val months = StatsCalculator.byMonth(expenses)
        assertEquals(
            listOf(
                YearMonth.of(2025, 1) to 1400_00L,
                YearMonth.of(2025, 2) to 0L,
                YearMonth.of(2025, 3) to 0L,
                YearMonth.of(2025, 4) to 2500_00L,
            ),
            months.map { it.month to it.cents },
        )
    }

    /** 按时间范围筛选是闭区间 */
    @Test
    fun filterByRangeIsInclusive() {
        val from = LocalDate.of(2025, 1, 20).toEpochDay()
        val to = LocalDate.of(2025, 4, 2).toEpochDay()
        assertEquals(2, StatsCalculator.filterByRange(expenses, from, to).size)
        assertEquals(1, StatsCalculator.filterByRange(expenses, from, to - 1).size)
        assertEquals(3, StatsCalculator.filterByRange(expenses, null, null).size)
        assertEquals(1, StatsCalculator.filterByRange(expenses, to, null).size)
    }

    /** 没有账单时结果为空 */
    @Test
    fun emptyInputGivesEmptyResults() {
        assertTrue(StatsCalculator.byMonth(emptyList()).isEmpty())
        assertTrue(StatsCalculator.byCategory(emptyList(), CategoryKind.STAGE).isEmpty())
    }
}
