package com.zhuangxiu.ledger.ui.daily

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.data.repository.DailyRepository
import com.zhuangxiu.ledger.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

data class DailyEditState(
    val amount: String = "", val kind: EntryKind = EntryKind.EXPENSE,
    val category: DailyCategory = DailyCategory.FOOD, val day: Long = LocalDate.now().toEpochDay(),
    val minute: Int = LocalTime.now().let { it.hour * 60 + it.minute },
    val method: PayMethod = PayMethod.WECHAT, val note: String = "",
    val categoryName: String? = null, val customCategories: List<DailyCategoryEntity> = emptyList(),
    val originalCategory: String? = null, val account: String? = null, val sourceMethod: String? = null,
    val loading: Boolean = true, val busy: Boolean = false, val complete: Boolean = false,
    val error: String? = null, val missing: Boolean = false,
    val bookName: String? = null, val resultMessage: String = "已保存账目",
) {
    val valid: Boolean get() = amount.matches(Regex("[0-9]+(\\.[0-9]{0,2})?")) && (Money.parseToCents(amount) ?: 0) > 0 && note.length <= 1000
}

@HiltViewModel
class DailyEditViewModel @Inject constructor(private val repository: DailyRepository, private val saved: SavedStateHandle) : ViewModel() {
    val editing = !saved.get<String>("entryId").isNullOrEmpty()
    private val id: String = saved.get<String>("entryId")?.takeIf { it.isNotEmpty() }
        ?: saved.get<String>("draftId") ?: UUID.randomUUID().toString().also { saved["draftId"] = it }
    private var bookId = saved.get<Long>("bookId") ?: 1L
    private var original: DailyEntryEntity? = null
    private val _state = MutableStateFlow(DailyEditState())
    val state = _state.asStateFlow()

    init { viewModelScope.launch {
        try {
            val entry = if (editing) repository.get(id) else null
            original = entry
            if (editing && entry == null) { _state.value = DailyEditState(loading = false, missing = true, error = "这笔记录不存在或已被删除"); return@launch }
            if (entry != null) bookId = entry.bookId
            val base = entry?.let { DailyEditState(amount = Money.toInput(it.amountCents), kind = it.kind, category = it.category,
                day = it.day, minute = it.minute, method = it.payMethod, note = it.note, categoryName = it.categoryName,
                originalCategory = it.sourceCategory, account = it.account, sourceMethod = it.sourceMethod) } ?: DailyEditState()
            _state.value = base.copy(amount = saved["amount"] ?: base.amount,
                kind = saved.get<String>("kind")?.let(EntryKind::valueOf) ?: base.kind,
                category = saved.get<String>("category")?.let(DailyCategory::valueOf) ?: base.category,
                day = saved["day"] ?: base.day, minute = saved["minute"] ?: base.minute,
                method = saved.get<String>("method")?.let(PayMethod::valueOf) ?: base.method,
                note = saved["note"] ?: base.note, loading = false, complete = saved["complete"] ?: false,
                categoryName = if (saved.contains("categoryName")) saved["categoryName"] else base.categoryName,
                sourceMethod = if (saved.contains("sourceMethod")) saved["sourceMethod"] else base.sourceMethod,
                customCategories = repository.categoriesForBook(bookId),
                bookName = repository.book(bookId)?.name,
                resultMessage = saved["resultMessage"] ?: base.resultMessage)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.value = DailyEditState(loading = false, missing = true, error = "无法读取记录，请返回重试") }
    } }
    fun change(transform: (DailyEditState) -> DailyEditState) {
        if (_state.value.busy || _state.value.loading || _state.value.complete) return
        val s = transform(_state.value).copy(error = null)
        _state.value = s
        saved["amount"] = s.amount; saved["kind"] = s.kind.name; saved["category"] = s.category.name
        saved["day"] = s.day; saved["minute"] = s.minute; saved["method"] = s.method.name; saved["note"] = s.note
        saved["categoryName"] = s.categoryName; saved["sourceMethod"] = s.sourceMethod
    }
    fun kind(kind: EntryKind) = change {
        if (it.kind == kind) it else it.copy(kind = kind, category = DailyCategory.entries.first { category -> category.kind == kind }, categoryName = null)
    }
    fun save() {
        val s = _state.value
        if (s.busy || s.loading || s.complete || s.missing) return
        if (!s.valid) { _state.value = s.copy(error = "请输入有效的正数金额，最多两位小数"); return }
        operate {
            repository.save(DailyEntryEntity(id, bookId, requireNotNull(Money.parseToCents(s.amount)), s.kind, s.category, s.day, s.minute, s.method, s.note.trim(),
                categoryName = s.categoryName, sourceCategory = original?.sourceCategory, account = s.account, sourceMethod = s.sourceMethod,
                importFingerprint = original?.importFingerprint, importBatchId = original?.importBatchId), editing)
        }
    }
    fun delete() { if (editing && !_state.value.busy && !_state.value.complete) operate("已删除账目") { repository.delete(id) } }
    private fun operate(message: String = "已保存账目", block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                saved["resultMessage"] = message
                saved["complete"] = true
                _state.update { it.copy(complete = true, resultMessage = message) }
            }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(error = e.message ?: "保存失败，请重试") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}
