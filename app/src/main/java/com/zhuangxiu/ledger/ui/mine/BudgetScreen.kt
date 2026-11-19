package com.zhuangxiu.ledger.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.BudgetEntity
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.data.repository.BudgetRepository
import com.zhuangxiu.ledger.data.repository.CategoryRepository
import com.zhuangxiu.ledger.data.repository.ExpenseRepository
import com.zhuangxiu.ledger.domain.BudgetCalculator
import com.zhuangxiu.ledger.domain.BudgetItem
import com.zhuangxiu.ledger.domain.BudgetLevel
import com.zhuangxiu.ledger.notification.ReminderChecker
import com.zhuangxiu.ledger.ui.components.AmountInputDialog
import com.zhuangxiu.ledger.ui.components.BudgetProgressBar
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.budgetLevelColor
import com.zhuangxiu.ledger.ui.theme.LedgerTheme
import com.zhuangxiu.ledger.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetUiState(
    val total: BudgetItem? = null,
    val stages: List<BudgetItem> = emptyList(),
    val types: List<BudgetItem> = emptyList(),
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    private val budgetRepository: BudgetRepository,
    categoryRepository: CategoryRepository,
    private val reminderChecker: ReminderChecker,
) : ViewModel() {

    val uiState: StateFlow<BudgetUiState> = combine(
        expenseRepository.observeAll(),
        budgetRepository.observeAll(),
        categoryRepository.observeAll(),
    ) { allExpenses, budgets, categories ->
        val expenses = allExpenses.filter { it.expense.bookId == 2L }
        val budgetMap = budgets.associate { it.categoryId to it.amountCents }
        val spent = BudgetCalculator.spentByCategory(expenses)
        fun item(id: Long, name: String, spentCents: Long) =
            BudgetItem(id, name, BudgetCalculator.usage(budgetMap[id] ?: 0, spentCents))
        BudgetUiState(
            total = item(BudgetEntity.TOTAL_ID, "总预算", BudgetCalculator.summary(expenses).spentCents),
            stages = categories.filter { it.kind == CategoryKind.STAGE }.map { item(it.id, it.name, spent[it.id] ?: 0) },
            types = categories.filter { it.kind == CategoryKind.TYPE }.map { item(it.id, it.name, spent[it.id] ?: 0) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    fun setBudget(categoryId: Long, cents: Long) {
        viewModelScope.launch {
            budgetRepository.setBudget(categoryId, cents)
            runCatching { reminderChecker.checkBudgets() }
        }
    }
}

/** 预算设置页 */
@Composable
fun BudgetScreen(onBack: () -> Unit, viewModel: BudgetViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BudgetItem?>(null) }

    SubPageScaffold(title = "预算设置", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.total?.let { total ->
                item { BudgetRow(total, highlighted = true, onClick = { editing = total }) }
            }
            item { GroupTitle("按装修阶段") }
            items(state.stages, key = { it.categoryId }) { item -> BudgetRow(item, onClick = { editing = item }) }
            item { GroupTitle("按费用类型") }
            items(state.types, key = { it.categoryId }) { item -> BudgetRow(item, onClick = { editing = item }) }
            item {
                Text(
                    "“已花”按账单总价统计（包含还没付清的尾款）。用到 80% 时预警，超出预算时标红并发送通知。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    editing?.let { item ->
        AmountInputDialog(
            title = "设置${item.name}" + if (item.categoryId == BudgetEntity.TOTAL_ID) "" else "预算",
            initialCents = item.usage.budgetCents,
            label = "预算金额（元）",
            hint = "留空并确定可以取消这项预算",
            allowEmpty = true,
            onConfirm = {
                viewModel.setBudget(item.categoryId, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

@Composable
private fun BudgetRow(item: BudgetItem, highlighted: Boolean = false, onClick: () -> Unit) {
    val usage = item.usage
    val expenseColor = LedgerTheme.extra.expense
    LedgerCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = if (highlighted) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = if (highlighted) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (usage.level == BudgetLevel.NONE) {
                    Text("未设置 ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "${usage.percent}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = budgetLevelColor(usage.level),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (usage.level == BudgetLevel.NONE) {
                if (usage.spentCents > 0) {
                    Text(
                        "已花 ${Money.format(usage.spentCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = expenseColor,
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                BudgetProgressBar(usage)
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        buildAnnotatedString {
                            append("已花 ")
                            withStyle(SpanStyle(color = expenseColor)) { append(Money.format(usage.spentCents)) }
                            append(" / ${Money.format(usage.budgetCents)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (usage.level == BudgetLevel.OVER) {
                            "超支 ${Money.format(-usage.remainingCents)}"
                        } else {
                            "剩余 ${Money.format(usage.remainingCents)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (usage.level == BudgetLevel.NORMAL) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            budgetLevelColor(usage.level)
                        },
                        fontWeight = if (usage.level == BudgetLevel.OVER) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
