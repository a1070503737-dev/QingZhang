package com.zhuangxiu.ledger.ui.daily

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.*
import com.zhuangxiu.ledger.data.repository.DailyRepository
import com.zhuangxiu.ledger.data.repository.SettingsRepository
import com.zhuangxiu.ledger.domain.DailyStatistics
import com.zhuangxiu.ledger.domain.DailySummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.YearMonth
import javax.inject.Inject

data class BookState(val books: List<BookEntity> = emptyList(), val selected: BookEntity? = null, val error: String? = null)
data class DailyState(val month: YearMonth = YearMonth.now(), val summary: DailySummary? = null, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyViewModel @Inject constructor(
    private val repository: DailyRepository,
    private val settings: SettingsRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val books = combine(repository.books, settings.selectedBook, error) { books, selected, error ->
        BookState(books, books.firstOrNull { it.id == selected } ?: books.firstOrNull(), error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookState())
    private val month = saved.getStateFlow("month", YearMonth.now().toString())
    val daily = combine(books, month) { state, month -> state.selected to YearMonth.parse(month) }
        .distinctUntilChanged().flatMapLatest { (book, month) ->
            if (book?.kind != BookKind.DAILY) flowOf(DailyState(month))
            else repository.observeRange(book.id, month.minusMonths(5).atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay())
                .map { DailyState(month, DailyStatistics.calculate(it, book.id, month)) }
                .onStart { emit(DailyState(month)) }
                .catch { emit(DailyState(month, error = "读取账目失败，请重新打开应用")) }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyState())

    fun select(id: Long) = action { settings.selectBook(id) }
    fun month(offset: Long) {
        val next = YearMonth.parse(month.value).plusMonths(offset)
        if (next.year in 2..9998) saved["month"] = next.toString()
    }
    fun currentMonth() { saved["month"] = YearMonth.now().toString() }
    fun addBook(name: String, done: () -> Unit) = action { settings.selectBook(repository.addBook(name)); done() }
    fun renameBook(id: Long, name: String, done: () -> Unit) = action { repository.renameBook(id, name); done() }
    fun clearError() { error.value = null }
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "操作失败，请重试" }
    } }
}
