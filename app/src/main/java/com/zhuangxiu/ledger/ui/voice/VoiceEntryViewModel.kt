package com.zhuangxiu.ledger.ui.voice

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.domain.ChineseVoiceParser
import com.zhuangxiu.ledger.domain.EntryDraft
import com.zhuangxiu.ledger.domain.LedgerEntryService
import com.zhuangxiu.ledger.domain.parseEntryDate
import com.zhuangxiu.ledger.domain.validAmount
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class VoiceEntryState(
    val text: String = "",
    val drafts: List<EntryDraft> = emptyList(),
    val bookName: String? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val confirmSimilar: Boolean = false,
    val complete: Boolean = false,
    val error: String? = null,
) {
    val canSave get() = !busy && !complete && bookName != null && drafts.isNotEmpty() && drafts.all {
        it.kind != null && it.category?.kind == it.kind && validAmount(it.amount) &&
            parseEntryDate(it.date) != null && it.note.length <= 1000
    }
}

@HiltViewModel
internal class VoiceEntryViewModel @Inject constructor(
    private val ledger: LedgerEntryService,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val bookId = saved.get<Long>("bookId") ?: 1L
    private val requestId = saved.get<String>("voiceRequestId") ?: UUID.randomUUID().toString().also {
        saved["voiceRequestId"] = it
    }
    private val initialText = saved.get<String>("voiceText").orEmpty()
    private val _state = MutableStateFlow(
        VoiceEntryState(
            text = initialText,
            drafts = saved.get<ArrayList<Bundle>>("voiceDrafts")?.map(::decodeDraft).orEmpty(),
            complete = saved["voiceComplete"] ?: false,
        ),
    )
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val book = ledger.writableBook(bookId)
                _state.update { it.copy(bookName = book.name, loading = false) }
                if (initialText.isNotBlank() && !saved.contains("voiceDrafts")) parse()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, error = "这个日常账本当前无法记账") }
            }
        }
    }

    fun text(value: String) {
        if (_state.value.busy || value.length > LedgerEntryService.MAX_TRANSCRIPT) return
        saved["voiceText"] = value
        updateDrafts(emptyList(), value)
    }

    fun recognized(value: String) {
        text(value.take(LedgerEntryService.MAX_TRANSCRIPT))
        parse()
    }

    fun parse() {
        val state = _state.value
        if (state.busy || state.bookName == null) return
        val parsed = ChineseVoiceParser.parse(state.text, LocalDate.now())
        when {
            parsed.isEmpty() -> _state.update { it.copy(error = "请先说出或输入账目") }
            parsed.size > LedgerEntryService.MAX_DRAFTS -> _state.update { it.copy(error = "一次最多解析 20 笔，请分批输入") }
            else -> updateDrafts(parsed)
        }
    }

    fun edit(index: Int, transform: (EntryDraft) -> EntryDraft) {
        val state = _state.value
        if (!state.busy) updateDrafts(state.drafts.mapIndexed { i, draft -> if (i == index) transform(draft) else draft })
    }

    fun remove(index: Int) {
        val state = _state.value
        if (!state.busy) updateDrafts(state.drafts.filterIndexed { i, _ -> i != index })
    }

    fun dismissSimilar() = _state.update { it.copy(confirmSimilar = false) }

    fun save(allowSimilar: Boolean = false) {
        val state = _state.value
        if (!state.canSave) return
        _state.update { it.copy(busy = true, confirmSimilar = false, error = null) }
        viewModelScope.launch {
            try {
                val committed = ledger.save(bookId, requestId, state.drafts, allowSimilar)
                if (committed) saved["voiceComplete"] = true
                _state.update { it.copy(complete = committed, confirmSimilar = !committed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(error = e.message) }
            } catch (_: Exception) {
                _state.update { it.copy(error = "保存失败，请重试") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun updateDrafts(drafts: List<EntryDraft>, text: String = _state.value.text) {
        saved["voiceDrafts"] = ArrayList(drafts.map { draft ->
            Bundle().apply {
                putString("kind", draft.kind?.name)
                putString("amount", draft.amount)
                putString("date", draft.date)
                putString("category", draft.category?.name)
                putString("note", draft.note)
            }
        })
        _state.update { it.copy(text = text, drafts = drafts, error = null, confirmSimilar = false) }
    }

    private fun decodeDraft(bundle: Bundle) = EntryDraft(
        kind = bundle.getString("kind")?.let(EntryKind::valueOf),
        amount = bundle.getString("amount").orEmpty(),
        date = bundle.getString("date").orEmpty(),
        category = bundle.getString("category")?.let(DailyCategory::valueOf),
        note = bundle.getString("note").orEmpty(),
    )
}
