package com.zhuangxiu.ledger.ui.voice

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhuangxiu.ledger.data.db.BookEntity
import com.zhuangxiu.ledger.data.db.DailyCategory
import com.zhuangxiu.ledger.data.db.EntryKind
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.domain.ChineseVoiceParser
import com.zhuangxiu.ledger.domain.EntryDraft
import com.zhuangxiu.ledger.domain.LedgerEntryService
import com.zhuangxiu.ledger.domain.parseEntryDate
import com.zhuangxiu.ledger.domain.parseEntryTime
import com.zhuangxiu.ledger.domain.validAmount
import com.zhuangxiu.ledger.voice.CloudVoiceException
import com.zhuangxiu.ledger.voice.CloudVoiceSettings
import com.zhuangxiu.ledger.voice.CloudVoiceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal data class VoiceEntryState(
    val text: String = "",
    val drafts: List<EntryDraft> = emptyList(),
    val bookName: String? = null,
    val saving: Boolean = false,
    val parsing: Boolean = false,
    val notice: String? = null,
    val confirmSimilar: Boolean = false,
    val complete: Boolean = false,
    val error: String? = null,
) {
    val busy get() = saving || parsing
    val canSave get() = !busy && !complete && bookName != null && drafts.isNotEmpty() && drafts.all {
        it.kind != null && it.category?.kind == it.kind && validAmount(it.amount) &&
            parseEntryDate(it.date) != null && (it.time.isBlank() || parseEntryTime(it.time) != null) &&
            it.note.length <= 1000
    }
}

@HiltViewModel
internal class VoiceEntryViewModel @Inject constructor(
    private val ledger: LedgerEntryService,
    private val saved: SavedStateHandle,
    private val cloudSettings: CloudVoiceSettings,
    private val cloud: CloudVoiceClient,
) : ViewModel() {
    private var parseJob: Job? = null
    private val _state = MutableStateFlow(
        VoiceEntryState(
            text = saved.get<String>("voiceText").orEmpty(),
            drafts = saved.get<ArrayList<Bundle>>("voiceDrafts")?.map(::decodeDraft).orEmpty(),
            bookName = saved["voiceBookName"],
            complete = saved["voiceComplete"] ?: false,
        ),
    )
    val state = _state.asStateFlow()

    fun open(book: BookEntity, transcription: String = "") {
        if (_state.value.busy) return
        dismiss()
        saved["voiceBookId"] = book.id
        saved["voiceBookName"] = book.name
        saved["voiceRequestId"] = UUID.randomUUID().toString()
        _state.value = VoiceEntryState(bookName = book.name)
        if (transcription.isNotBlank()) {
            text(transcription.take(LedgerEntryService.MAX_TRANSCRIPT))
            parse()
        }
    }

    fun dismiss() {
        if (_state.value.saving) return
        parseJob?.cancel()
        listOf("voiceBookId", "voiceBookName", "voiceRequestId", "voiceText", "voiceDrafts", "voiceComplete",
            "voiceRecordedMinute").forEach {
            saved.remove<Any>(it)
        }
        _state.value = VoiceEntryState()
    }

    fun text(value: String) {
        if (_state.value.busy || value.length > LedgerEntryService.MAX_TRANSCRIPT) return
        saved["voiceText"] = value
        updateDrafts(emptyList(), value)
    }

    fun parse() {
        val state = _state.value
        if (state.busy || state.bookName == null || state.text.isBlank()) return
        val today = LocalDate.now()
        val local = ChineseVoiceParser.parse(state.text, today)
        if (!cloudSettings.status.value.enabled) {
            showParsed(local)
            return
        }
        _state.update { it.copy(parsing = true, error = null, notice = null) }
        parseJob = viewModelScope.launch {
            try {
                val access = withContext(Dispatchers.IO) { cloudSettings.active() }
                if (access == null) showParsed(local)
                else {
                    val result = cloud.parse(access, state.text, today, local)
                    showParsed(result.drafts)
                    _state.update { it.copy(notice = "已由 ${result.modelLabel} 整理，请核对后保存") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showParsed(local)
                val reason = (error as? CloudVoiceException)?.message ?: "云端整理暂时不可用"
                _state.update { it.copy(notice = "$reason。已切换到本地整理。") }
            } finally {
                if (isActive) _state.update { it.copy(parsing = false) }
            }
        }
    }

    private fun showParsed(parsed: List<EntryDraft>) {
        when {
            parsed.isEmpty() -> {
                updateDrafts(emptyList())
                _state.update { it.copy(error = "未找到可记的账目，请检查转写文字") }
            }
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
        val bookId = saved.get<Long>("voiceBookId") ?: return
        val requestId = saved.get<String>("voiceRequestId") ?: return
        val recordedMinute = saved.get<Int>("voiceRecordedMinute") ?: (LocalTime.now().toSecondOfDay() / 60).also {
            saved["voiceRecordedMinute"] = it
        }
        _state.update { it.copy(saving = true, confirmSimilar = false, error = null) }
        viewModelScope.launch {
            try {
                val committed = ledger.save(bookId, requestId, state.drafts, allowSimilar, recordedMinute)
                if (committed) saved["voiceComplete"] = true
                _state.update { it.copy(complete = committed, confirmSimilar = !committed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(error = e.message) }
            } catch (_: Exception) {
                _state.update { it.copy(error = "保存失败，请重试") }
            } finally {
                _state.update { it.copy(saving = false) }
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
                putString("payMethod", draft.payMethod.name)
                putString("time", draft.time)
            }
        })
        _state.update { it.copy(text = text, drafts = drafts, error = null, notice = null, confirmSimilar = false) }
    }

    private fun decodeDraft(bundle: Bundle) = EntryDraft(
        kind = bundle.getString("kind")?.let(EntryKind::valueOf),
        amount = bundle.getString("amount").orEmpty(),
        date = bundle.getString("date").orEmpty(),
        category = bundle.getString("category")?.let(DailyCategory::valueOf),
        note = bundle.getString("note").orEmpty(),
        payMethod = PayMethod.entries.firstOrNull { it.name == bundle.getString("payMethod") } ?: PayMethod.UNSPECIFIED,
        time = bundle.getString("time").orEmpty(),
    )
}
