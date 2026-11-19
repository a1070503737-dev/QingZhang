package com.zhuangxiu.ledger.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.CategoryEntity
import com.zhuangxiu.ledger.data.db.CategoryKind
import com.zhuangxiu.ledger.data.repository.CategoryRepository
import com.zhuangxiu.ledger.ui.components.ConfirmDialog
import com.zhuangxiu.ledger.ui.components.LedgerCard
import com.zhuangxiu.ledger.ui.components.LedgerButton
import com.zhuangxiu.ledger.ui.components.LedgerIconButton
import com.zhuangxiu.ledger.ui.components.LedgerSegments
import com.zhuangxiu.ledger.ui.components.SubPageScaffold
import com.zhuangxiu.ledger.ui.components.TextInputDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun add(kind: CategoryKind, name: String) = viewModelScope.launch {
        if (categoryRepository.add(kind, name) == null) _message.value = "已经有叫“$name”的${kind.label}了"
    }

    fun rename(category: CategoryEntity, name: String) = viewModelScope.launch {
        if (!categoryRepository.rename(category, name)) _message.value = "已经有叫“$name”的${category.kind.label}了"
    }

    fun delete(category: CategoryEntity) = viewModelScope.launch {
        if (!categoryRepository.delete(category)) {
            _message.value = "“${category.name}”已有账单在使用，或是最后一个分类，不能删除。可以改名，或先把相关账单改到其他分类。"
        }
    }

    fun move(category: CategoryEntity, offset: Int) = viewModelScope.launch {
        categoryRepository.move(category, offset)
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val all by viewModel.categories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(CategoryKind.STAGE) }
    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<CategoryEntity?>(null) }
    val list = all.filter { it.kind == kind }

    SubPageScaffold(
        title = "分类管理",
        onBack = onBack,
        floatingActionButton = {
            LedgerButton(
                onClick = { showAdd = true },
                minHeight = 52.dp,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新增${kind.label.takeLast(2)}")
            }
        },
    ) { modifier ->
        Column(modifier.fillMaxSize()) {
            val entries = CategoryKind.entries
            LedgerSegments(
                itemCount = entries.size,
                selectedIndex = kind.ordinal,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                height = 48.dp,
                role = Role.Tab,
                onSelect = { idx -> kind = entries[idx] },
            ) { idx, selected ->
                Text(
                    entries[idx].label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(list, key = { _, item -> item.id }) { index, category ->
                    LedgerCard(
                        Modifier
                            .fillMaxWidth()
                            .animateItem(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            LedgerIconButton(onClick = { viewModel.move(category, -1) }, enabled = index > 0, size = 36.dp) {
                                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(20.dp))
                            }
                            LedgerIconButton(onClick = { viewModel.move(category, 1) }, enabled = index < list.lastIndex, size = 36.dp) {
                                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(20.dp))
                            }
                            LedgerIconButton(onClick = { renaming = category }, size = 36.dp) {
                                Icon(Icons.Outlined.Edit, contentDescription = "重命名", modifier = Modifier.size(19.dp))
                            }
                            LedgerIconButton(onClick = { deleting = category }, size = 36.dp) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", modifier = Modifier.size(19.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog(
            title = "新增${kind.label}",
            label = "名称",
            onConfirm = {
                viewModel.add(kind, it)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    renaming?.let { category ->
        TextInputDialog(
            title = "重命名",
            initial = category.name,
            label = "名称",
            onConfirm = {
                viewModel.rename(category, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = "删除“${category.name}”？",
            text = "该分类的预算设置会一并删除。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                viewModel.delete(category)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
    message?.let {
        ConfirmDialog(title = "无法完成", text = it, confirmLabel = "知道了", onConfirm = viewModel::clearMessage, onDismiss = viewModel::clearMessage)
    }
}
