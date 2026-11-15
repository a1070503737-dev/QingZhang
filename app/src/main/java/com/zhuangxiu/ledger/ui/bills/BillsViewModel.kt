package com.zhuangxiu.ledger.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.data.db.ExpenseWithDetails
import com.zhuangxiu.ledger.data.db.PaymentStatus
import com.zhuangxiu.ledger.data.repository.CategoryRepository
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 账单筛选条件；为 null 表示不限 */
data class BillFilter(
    val query: String = "",
    val stageId: Long? = null,
    val typeId: Long? = null,
    val status: PaymentStatus? = null,
) {
    val isActive: Boolean get() = query.isNotBlank() || stageId != null || typeId != null || status != null
}

/** 同一天的账单分组 */
data class DayGroup(val date: Long, val totalCents: Long, val items: List<ExpenseWithDetails>)

data class BillsUiState(
    val loading: Boolean = true,
    val filter: BillFilter = BillFilter(),
    val stages: List<CategoryEntity> = emptyList(),
    val types: List<CategoryEntity> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val count: Int = 0,
    val totalCents: Long = 0,
)

@HiltViewModel
class BillsViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(BillFilter())

    val uiState: StateFlow<BillsUiState> = combine(
        expenseRepository.observeAll(),
        categoryRepository.observeAll(),
        filter,
    ) { expenses, categories, filter ->
        val filtered = expenses.filter { matches(it, filter) }
        BillsUiState(
            loading = false,
            filter = filter,
            stages = categories.filter { it.kind == CategoryKind.STAGE },
            types = categories.filter { it.kind == CategoryKind.TYPE },
            // 查询结果已按日期倒序，groupBy 会保持这个顺序
            groups = filtered.groupBy { it.expense.date }.map { (date, items) ->
                DayGroup(date, items.sumOf { it.expense.totalCents }, items)
            },
            count = filtered.size,
            totalCents = filtered.sumOf { it.expense.totalCents },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsUiState())

    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setStage(id: Long?) = filter.update { it.copy(stageId = id) }
    fun setType(id: Long?) = filter.update { it.copy(typeId = id) }
    fun setStatus(status: PaymentStatus?) = filter.update { it.copy(status = status) }
    fun clearFilter() = filter.update { BillFilter() }

    /** 删除账单（连同付款记录和凭证图片），列表会随数据库变化自动刷新 */
    fun delete(expenseId: Long) {
        viewModelScope.launch { expenseRepository.delete(expenseId) }
    }

    /** 搜索范围：商家/工人、备注、阶段名、类型名 */
    private fun matches(item: ExpenseWithDetails, filter: BillFilter): Boolean {
        if (filter.stageId != null && item.expense.stageId != filter.stageId) return false
        if (filter.typeId != null && item.expense.typeId != filter.typeId) return false
        if (filter.status != null && item.expense.status != filter.status) return false
        val query = filter.query.trim()
        if (query.isEmpty()) return true
        return item.expense.payee.contains(query, ignoreCase = true) ||
            item.expense.note.contains(query, ignoreCase = true) ||
            item.stage.name.contains(query, ignoreCase = true) ||
            item.type.name.contains(query, ignoreCase = true)
    }
}
