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

data class BookState(
    val books: List<BookEntity> = emptyList(),
    val selected: BookEntity? = null,
    val error: String? = null,
    val all: Boolean = false,
    val loaded: Boolean = false,
)
data class DailyState(val month: YearMonth = YearMonth.now(), val summary: DailySummary? = null, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyViewModel @Inject constructor(
    private val repository: DailyRepository,
    private val settings: SettingsRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val historyBook = saved.getStateFlow<Long?>("historyBook", null)
    val books = combine(repository.books, settings.selectedBook, error, historyBook) { books, selected, error, historyId ->
        val active = books.filterNot { it.archived }
        val history = books.firstOrNull { it.id == historyId && it.archived }
        BookState(books, history ?: active.firstOrNull { it.id == selected } ?: active.firstOrNull(), error, selected == 0L && history == null, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookState())
    private val month = saved.getStateFlow("month", YearMonth.now().toString())
    val daily = combine(books, month) { state, month -> (if (state.all) 0L else state.selected?.id) to YearMonth.parse(month) }
        .distinctUntilChanged().flatMapLatest { (book, month) ->
            if (book == null) flowOf(DailyState(month))
            else repository.observeActual(book, month.minusMonths(5).atDay(1).toEpochDay(), month.plusMonths(1).atDay(1).toEpochDay())
                .map { DailyState(month, DailyStatistics.calculate(it, book, month)) }
                .onStart { emit(DailyState(month)) }
                .catch { emit(DailyState(month, error = "读取账目失败，请重新打开应用")) }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyState())

    fun select(id: Long) = action {
        if (books.value.books.any { it.id == id && it.archived }) {
            saved["historyBook"] = id
        } else {
            settings.selectBook(id)
            saved["historyBook"] = null
        }
    }
    fun month(offset: Long) {
        val next = YearMonth.parse(month.value).plusMonths(offset)
        if (next.year in 1..9999) saved["month"] = next.toString()
    }
    fun currentMonth() { saved["month"] = YearMonth.now().toString() }
    fun saveBook(book: BookEntity, done: () -> Unit) = action {
        if (book.id == 0L) {
            settings.selectBook(repository.addBook(book.name, book.kind, book.trackPayments, book.trackPhotos))
            saved["historyBook"] = null
        }
        else repository.editBook(book)
        done()
    }
    fun clearError() { error.value = null }
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "操作失败，请重试" }
    } }
}
