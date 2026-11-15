package com.zhuangxiu.ledger

import com.zhuangxiu.ledger.data.db.BudgetEntity
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.domain.BudgetCalculator
import com.zhuangxiu.ledger.domain.BudgetLevel
import com.zhuangxiu.ledger.domain.PaymentLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 预算计算逻辑 */
class BudgetCalculatorTest {

    /** 没有预算时等级为NONE且百分比为0 */
    @Test
    fun noBudget_levelNoneAndZeroPercent() {
        val usage = BudgetCalculator.usage(budgetCents = 0, spentCents = 5000)
        assertEquals(BudgetLevel.NONE, usage.level)
        assertEquals(0, usage.percent)
        assertEquals(0f, usage.fraction)
    }

    /** 已花剩余和百分比 */
    @Test
    fun spentRemainingAndPercent() {
        val usage = BudgetCalculator.usage(budgetCents = 10_000_00, spentCents = 2_500_00)
        assertEquals(7_500_00L, usage.remainingCents)
        assertEquals(25, usage.percent)
        assertEquals(0.25f, usage.fraction)
        assertEquals(BudgetLevel.NORMAL, usage.level)
    }

    /** 不到80时正常 刚好80时预警 */
    @Test
    fun below80Normal_at80Warning() {
        assertEquals(BudgetLevel.NORMAL, BudgetCalculator.usage(100_00, 79_99).level)
        assertEquals(BudgetLevel.WARNING, BudgetCalculator.usage(100_00, 80_00).level)
        assertEquals(BudgetLevel.WARNING, BudgetCalculator.usage(100_00, 99_99).level)
    }

    /** 刚好用完算预警 超出一分钱算超支 */
    @Test
    fun exactly100IsWarning_oneCentOverIsOver() {
        val exact = BudgetCalculator.usage(100_00, 100_00)
        assertEquals(BudgetLevel.WARNING, exact.level)
        assertEquals(100, exact.percent)
        assertEquals(0L, exact.remainingCents)

        val over = BudgetCalculator.usage(100_00, 100_01)
        assertEquals(BudgetLevel.OVER, over.level)
        assertEquals(-1L, over.remainingCents)
    }

    /** 超支时百分比超过100但进度条封顶 */
    @Test
    fun overBudget_percentAbove100ButFractionCapped() {
        val usage = BudgetCalculator.usage(100_00, 250_00)
        assertEquals(250, usage.percent)
        assertEquals(1f, usage.fraction)
    }

    /** 大金额不会溢出 */
    @Test
    fun largeAmountsDoNotOverflow() {
        // 预算 5000 万元，已花 4500 万元
        val usage = BudgetCalculator.usage(50_000_000_00, 45_000_000_00)
        assertEquals(90, usage.percent)
        assertEquals(BudgetLevel.WARNING, usage.level)
    }

    /** 汇总 已花按订单总价 待付按剩余金额 */
    @Test
    fun summary_spentByOrderTotal_pendingByRemaining() {
        val expenses = listOf(
            TestData.expense(total = 1000_00),                 // 已付清
            TestData.expense(total = 5000_00, paid = 2000_00), // 还欠 3000
            TestData.expense(total = 800_00, paid = 0),        // 全部未付
        )
        val summary = BudgetCalculator.summary(expenses)
        assertEquals(6800_00L, summary.spentCents)
        assertEquals(3000_00L, summary.paidCents)
        assertEquals(3800_00L, summary.pendingCents)
    }

    /** 按分类统计花费 阶段和类型各算一次 */
    @Test
    fun spentByCategory_countsStageAndType() {
        val expenses = listOf(
            TestData.expense(1000_00, stage = TestData.stageWater, type = TestData.typeMaterial),
            TestData.expense(500_00, stage = TestData.stageWater, type = TestData.typeLabor),
            TestData.expense(300_00, stage = TestData.stageWood, type = TestData.typeLabor),
        )
        val spent = BudgetCalculator.spentByCategory(expenses)
        assertEquals(1500_00L, spent[TestData.stageWater.id])
        assertEquals(300_00L, spent[TestData.stageWood.id])
        assertEquals(1000_00L, spent[TestData.typeMaterial.id])
        assertEquals(800_00L, spent[TestData.typeLabor.id])
    }

    /** 预算条目 总预算排第一 没设预算的分类不出现 */
    @Test
    fun budgetItems_totalFirst_skipsCategoriesWithoutBudget() {
        val expenses = listOf(
            TestData.expense(900_00, stage = TestData.stageWater, type = TestData.typeMaterial),
            TestData.expense(300_00, stage = TestData.stageWood, type = TestData.typeLabor),
        )
        val budgets = listOf(
            BudgetEntity(TestData.stageWater.id, 1000_00),
            BudgetEntity(BudgetEntity.TOTAL_ID, 1000_00),
            BudgetEntity(TestData.typeLabor.id, 0), // 金额为 0 视为没设置
        )
        val items = BudgetCalculator.budgetItems(budgets, TestData.categories, expenses)

        assertEquals(listOf("总预算", "水电"), items.map { it.name })
        // 总预算：花了 1200，预算 1000 -> 超支
        assertEquals(1200_00L, items[0].usage.spentCents)
        assertEquals(BudgetLevel.OVER, items[0].usage.level)
        // 水电：花了 900，预算 1000 -> 90% 预警
        assertEquals(90, items[1].usage.percent)
        assertEquals(BudgetLevel.WARNING, items[1].usage.level)
    }

    /** 没有任何预算时条目为空 */
    @Test
    fun budgetItems_emptyWhenNoBudgets() {
        assertTrue(BudgetCalculator.budgetItems(emptyList(), TestData.categories, emptyList()).isEmpty())
    }

    // ---------- 付款状态推导 ----------

    /** 付款状态由总价和已付金额推导 */
    @Test
    fun statusDerivedFromTotalAndPaid() {
        assertEquals(PaymentStatus.PAID, PaymentLogic.resolveStatus(PaymentStatus.UNPAID, 100, 100))
        assertEquals(PaymentStatus.PAID, PaymentLogic.resolveStatus(PaymentStatus.DEPOSIT, 100, 150))
        assertEquals(PaymentStatus.UNPAID, PaymentLogic.resolveStatus(PaymentStatus.PAID, 100, 0))
        assertEquals(PaymentStatus.DEPOSIT, PaymentLogic.resolveStatus(PaymentStatus.DEPOSIT, 100, 30))
        assertEquals(PaymentStatus.BALANCE_DUE, PaymentLogic.resolveStatus(PaymentStatus.BALANCE_DUE, 100, 30))
        assertEquals(PaymentStatus.BALANCE_DUE, PaymentLogic.resolveStatus(PaymentStatus.UNPAID, 100, 30))
    }

    /** 新建账单时的首笔付款金额 */
    @Test
    fun initialPaidForNewExpense() {
        assertEquals(100L, PaymentLogic.initialPaid(PaymentStatus.PAID, 100, 0))
        assertEquals(0L, PaymentLogic.initialPaid(PaymentStatus.UNPAID, 100, 50))
        assertEquals(30L, PaymentLogic.initialPaid(PaymentStatus.DEPOSIT, 100, 30))
        assertEquals(100L, PaymentLogic.initialPaid(PaymentStatus.BALANCE_DUE, 100, 999))
    }
}
