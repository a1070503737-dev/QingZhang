package com.zhuangxiu.ledger.domain

import com.zhuangxiu.ledger.data.db.DailyEntryEntity
import com.zhuangxiu.ledger.data.db.PayMethod
import com.zhuangxiu.ledger.data.repository.DailyRepository
import com.zhuangxiu.ledger.util.Money
import java.time.LocalDate
import javax.inject.Inject

internal class LedgerEntryService @Inject constructor(private val repository: DailyRepository) {
    internal companion object {
        const val MAX_DRAFTS = 20
        const val MAX_TRANSCRIPT = 2_000
    }

    suspend fun save(bookId: Long, requestId: String, drafts: List<EntryDraft>, allowSimilar: Boolean): Boolean {
        require(requestId.matches(Regex("[A-Za-z0-9-]{1,100}"))) { "记账请求编号无效" }
        require(drafts.size in 1..MAX_DRAFTS) { "每次可保存 1–20 笔账目" }
        val entries = drafts.mapIndexed { index, draft ->
            val kind = requireNotNull(draft.kind) { "请选择每笔账目的收支类型" }
            val amount = requireNotNull(draft.amount.takeIf(::validAmount)?.let(Money::parseToCents)) { "请填写有效金额" }
            val date = requireNotNull(parseEntryDate(draft.date)) { "请填写有效日期（年-月-日）" }
            val category = requireNotNull(draft.category?.takeIf { it.kind == kind }) { "请选择每笔账目的分类" }
            DailyEntryEntity("review:$requestId:$index", bookId, amount, kind, category, date.toEpochDay(),
                -1, PayMethod.UNSPECIFIED, draft.note.trim())
        }
        return repository.saveReviewed(entries, allowSimilar)
    }
}

internal fun parseEntryDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim()) }.getOrNull()?.takeIf { it.year in 1..9999 }

internal fun validAmount(text: String): Boolean =
    text.trim().matches(Regex("[0-9]+(?:\\.[0-9]{1,2})?")) && (Money.parseToCents(text) ?: 0L) > 0
