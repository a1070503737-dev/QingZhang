package com.zhuangxiu.ledger.notification

import com.zhuangxiu.ledger.data.repository.BudgetRepository
import com.zhuangxiu.ledger.data.repository.CategoryRepository
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import com.zhuangxiu.ledger.data.repository.SettingsRepository
import com.zhuangxiu.ledger.domain.BudgetCalculator
import com.zhuangxiu.ledger.domain.BudgetLevel
import com.zhuangxiu.ledger.util.Dates
import com.zhuangxiu.ledger.util.Money
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提醒检查：判断哪些待付款快到期、哪些预算需要预警，并发出通知。
 * 由每日后台任务和记账后的即时检查共同调用。
 */
@Singleton
class ReminderChecker @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val notifier: Notifier,
) {
    /** 检查待付款到期：到期日在“提前天数”以内（含已逾期）且还有余款的账单 */
    suspend fun checkDuePayments() {
        val settings = settingsRepository.reminderSettings.first()
        if (!settings.paymentReminderEnabled) return
        val today = Dates.today()
        for (item in expenseRepository.listAll()) {
            val due = item.expense.dueDate ?: continue
            if (item.remainingCents <= 0) continue
            val daysLeft = due - today
            if (daysLeft > settings.remindDaysBefore) continue
            val who = item.expense.payee.ifBlank { "${item.stage.name} · ${item.type.name}" }
            val whenText = when {
                daysLeft < 0 -> "已逾期 ${-daysLeft} 天"
                daysLeft == 0L -> "今天到期"
                else -> "还有 $daysLeft 天到期"
            }
            notifier.notifyPaymentDue(
                expenseId = item.expense.id,
                title = "待付款提醒：$who",
                text = "待付 ${Money.format(item.remainingCents)}，$whenText（${Dates.format(due)}）",
            )
        }
    }

    /**
     * 检查预算：用到 80% 预警、超过 100% 提醒。
     * 同一项预算的同一档位只通知一次；花费回落后标记会清除，再次触发时会重新通知。
     */
    suspend fun checkBudgets() {
        val settings = settingsRepository.reminderSettings.first()
        if (!settings.budgetAlertEnabled) return
        val items = BudgetCalculator.budgetItems(
            budgets = budgetRepository.listAll(),
            categories = categoryRepository.observeAll().first(),
            expenses = expenseRepository.listAll(),
        )
        val notified = settingsRepository.notifiedBudgetKeys()
        val active = HashSet<String>()
        for (item in items) {
            val level = item.usage.level
            if (level != BudgetLevel.WARNING && level != BudgetLevel.OVER) continue
            val key = "${item.categoryId}:${level.name}"
            active += key
            // 超支时把预警档也视为已通知，避免金额回落到 80%~100% 时再弹一次
            if (level == BudgetLevel.OVER) active += "${item.categoryId}:${BudgetLevel.WARNING.name}"
            if (key in notified) continue
            if (!notifier.canNotify()) {
                // 没有通知权限时不记为已通知，等有权限后再提醒
                active -= key
                continue
            }
            if (level == BudgetLevel.OVER) {
                notifier.notifyBudget(
                    item.categoryId,
                    title = "${item.name}已超支",
                    text = "预算 ${Money.format(item.usage.budgetCents)}，已花 ${Money.format(item.usage.spentCents)}，" +
                        "超出 ${Money.format(-item.usage.remainingCents)}",
                )
            } else {
                notifier.notifyBudget(
                    item.categoryId,
                    title = "${item.name}已使用 ${item.usage.percent}%",
                    text = "预算 ${Money.format(item.usage.budgetCents)}，还剩 ${Money.format(item.usage.remainingCents)}",
                )
            }
        }
        settingsRepository.setNotifiedBudgetKeys(active)
    }
}
