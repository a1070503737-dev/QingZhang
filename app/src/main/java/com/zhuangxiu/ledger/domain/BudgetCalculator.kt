package com.zhuangxiu.ledger.domain

import com.zhuangxiu.ledger.data.db.BudgetEntity
import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails

/** 预算使用程度 */
enum class BudgetLevel {
    /** 没有设置预算 */
    NONE,

    /** 正常 */
    NORMAL,

    /** 已用到 80% 及以上，预警 */
    WARNING,

    /** 超出预算 */
    OVER,
}

/** 某一项预算的使用情况 */
data class BudgetUsage(
    val budgetCents: Long,
    val spentCents: Long,
) {
    /** 剩余金额，超支时为负数 */
    val remainingCents: Long get() = budgetCents - spentCents

    /** 使用百分比（向下取整），没有预算时为 0 */
    val percent: Int get() = if (budgetCents <= 0) 0 else (spentCents * 100 / budgetCents).toInt()

    /** 进度条用的比例，限制在 0..1 */
    val fraction: Float
        get() = if (budgetCents <= 0) 0f else (spentCents.toDouble() / budgetCents).toFloat().coerceIn(0f, 1f)

    val level: BudgetLevel
        get() = when {
            budgetCents <= 0 -> BudgetLevel.NONE
            spentCents > budgetCents -> BudgetLevel.OVER
            spentCents * 100 >= budgetCents * BudgetCalculator.WARNING_PERCENT -> BudgetLevel.WARNING
            else -> BudgetLevel.NORMAL
        }
}

/** 带名字的预算条目（总预算或某个分类的预算） */
data class BudgetItem(
    /** 分类 id；总预算为 [BudgetEntity.TOTAL_ID] */
    val categoryId: Long,
    val name: String,
    val usage: BudgetUsage,
)

/** 整个账本的汇总数字，首页使用 */
data class LedgerSummary(
    /** 已花：所有账单的订单总价之和（含尚未付清的部分） */
    val spentCents: Long,
    /** 实际已经付出去的钱 */
    val paidCents: Long,
    /** 待付总额：所有账单剩余未付金额之和 */
    val pendingCents: Long,
)

/**
 * 预算计算逻辑（纯函数，便于单元测试）。
 * “已花”按订单总价统计：签了单即使尾款没付，这笔钱也已经占用了预算。
 */
object BudgetCalculator {
    /** 预警阈值：用到 80% */
    const val WARNING_PERCENT = 80

    fun usage(budgetCents: Long, spentCents: Long) = BudgetUsage(budgetCents, spentCents)

    fun summary(expenses: List<ExpenseWithDetails>) = LedgerSummary(
        spentCents = expenses.sumOf { it.expense.totalCents },
        paidCents = expenses.sumOf { it.paidCents },
        pendingCents = expenses.sumOf { it.remainingCents },
    )

    /**
     * 每个分类的花费。阶段和类型共用一张表、id 不重复，
     * 所以可以放在同一个 Map 里：key 是分类 id。
     */
    fun spentByCategory(expenses: List<ExpenseWithDetails>): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        for (item in expenses) {
            val e = item.expense
            result[e.stageId] = (result[e.stageId] ?: 0) + e.totalCents
            result[e.typeId] = (result[e.typeId] ?: 0) + e.totalCents
        }
        return result
    }

    /**
     * 列出所有已设置的预算及其使用情况：总预算排第一，其余按分类顺序。
     */
    fun budgetItems(
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        expenses: List<ExpenseWithDetails>,
    ): List<BudgetItem> {
        val budgetMap = budgets.filter { it.amountCents > 0 }.associate { it.categoryId to it.amountCents }
        val spent = spentByCategory(expenses)
        val items = ArrayList<BudgetItem>()
        budgetMap[BudgetEntity.TOTAL_ID]?.let { total ->
            items += BudgetItem(BudgetEntity.TOTAL_ID, "总预算", usage(total, summary(expenses).spentCents))
        }
        for (category in categories) {
            val budget = budgetMap[category.id] ?: continue
            items += BudgetItem(category.id, category.name, usage(budget, spent[category.id] ?: 0))
        }
        return items
    }
}
