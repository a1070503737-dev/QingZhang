package com.zhuangxiu.ledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.data.repository.BudgetRepository
import com.zhuangxiu.ledger.data.repository.CategoryRepository
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import com.zhuangxiu.ledger.domain.BudgetCalculator
import com.zhuangxiu.ledger.domain.BudgetItem
import com.zhuangxiu.ledger.domain.CategorySlice
import com.zhuangxiu.ledger.domain.MonthAmount
import com.zhuangxiu.ledger.domain.StatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/** 时间范围预设 */
enum class RangePreset(val label: String) {
    ALL("全部"),
    THIS_MONTH("本月"),
    LAST_3_MONTHS("近3个月"),
    THIS_YEAR("今年"),
    CUSTOM("自定义"),
}

/** 统计页的筛选条件 */
data class StatsOptions(
    val preset: RangePreset = RangePreset.ALL,
    /** 自定义范围的起止日期（epochDay，闭区间） */
    val customFrom: Long? = null,
    val customTo: Long? = null,
    val kind: CategoryKind = CategoryKind.STAGE,
) {
    /** 把预设换算成具体的起止日期；null 表示不限 */
    fun range(today: LocalDate = LocalDate.now()): Pair<Long?, Long?> = when (preset) {
        RangePreset.ALL -> null to null
        RangePreset.THIS_MONTH -> today.withDayOfMonth(1).toEpochDay() to today.withDayOfMonth(today.lengthOfMonth()).toEpochDay()
        RangePreset.LAST_3_MONTHS -> today.minusMonths(2).withDayOfMonth(1).toEpochDay() to today.toEpochDay()
        RangePreset.THIS_YEAR -> today.withDayOfYear(1).toEpochDay() to today.withDayOfYear(today.lengthOfYear()).toEpochDay()
        RangePreset.CUSTOM -> customFrom to customTo
    }
}

data class StatsUiState(
    val loading: Boolean = true,
    val options: StatsOptions = StatsOptions(),
    val totalCents: Long = 0,
    val count: Int = 0,
    val slices: List<CategorySlice> = emptyList(),
    val months: List<MonthAmount> = emptyList(),
    /** 预算与实际对比（与饼图同一维度；总预算始终显示） */
    val budgetItems: List<BudgetItem> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val options = MutableStateFlow(StatsOptions())

    val uiState: StateFlow<StatsUiState> = combine(
        expenseRepository.observeAll(),
        budgetRepository.observeAll(),
        categoryRepository.observeAll(),
        options,
    ) { expenses, budgets, categories, options ->
        val (from, to) = options.range()
        val inRange = StatsCalculator.filterByRange(expenses, from, to)
        StatsUiState(
            loading = false,
            options = options,
            totalCents = inRange.sumOf { it.expense.totalCents },
            count = inRange.size,
            slices = StatsCalculator.byCategory(inRange, options.kind),
            months = StatsCalculator.byMonth(inRange),
            // 预算是针对整个装修的，所以对比图始终按全部账单计算，不受时间范围影响
            budgetItems = BudgetCalculator.budgetItems(
                budgets,
                categories.filter { it.kind == options.kind },
                expenses,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPreset(preset: RangePreset) = options.update { it.copy(preset = preset) }

    fun setCustomRange(from: Long, to: Long) =
        options.update { it.copy(preset = RangePreset.CUSTOM, customFrom = from, customTo = to) }

    fun setKind(kind: CategoryKind) = options.update { it.copy(kind = kind) }
}
